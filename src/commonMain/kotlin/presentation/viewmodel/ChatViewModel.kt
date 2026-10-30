package presentation.viewmodel

import core.AppLogger
import core.LocalChatHistoryStore
import core.Throttle
import core.restoreRecentHistory
import core.state.ChatState
import core.state.ConversationPreviewState
import core.state.GlobalAppState
import data.repository.ChatRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import model.Group
import model.GroupMessage
import model.Message
import model.MessageType
import model.User

internal val AGENT_ASSISTANT_ID = core.ServerConfig.AGENT_ASSISTANT_ID

/** 聊天ViewModel 处理聊天相关的UI逻辑和状态 */
internal val logger = KotlinLogging.logger {}

// ── 本地历史写盘 debounce ────────────────────────────────
// 合并高频消息变更；写入队列仅持有尚未提交的最新修订，成功后释放正文。
internal const val SAVE_HISTORY_DEBOUNCE_MS = 1000L

open class ChatViewModel(
    internal val chatRepository: ChatRepository,
    internal val chatState: ChatState,
) {
    // 唯一的作用域嵌套：应用级（永不取消）→ 会话级（clear 时重建）
    internal val appJob = SupervisorJob()
    internal var sessionJob: Job = SupervisorJob(appJob)
    internal val sessionScope
        get() = CoroutineScope(sessionJob + Dispatchers.Main.immediate)

    /** Android 子类需要访问 coroutineScope */
    protected val coroutineScope: CoroutineScope
        get() = sessionScope

    // 用户列表状态Flow
    val usersFlow: StateFlow<List<User>> = chatState.users

    // 私聊消息状态Flow
    val messagesFlow: StateFlow<List<Message>> = chatState.messages

    // 群聊消息状态Flow
    val groupMessagesFlow: StateFlow<List<GroupMessage>> = chatState.groupMessages

    // 当前选中的用户/群组Flow
    val selectedChatTargetFlow: StateFlow<User?> = chatState.selectedChatTarget

    // 会话预览状态Flow（最近消息时间、未读数）
    val conversationStatesFlow: StateFlow<Map<model.ConversationKey, ConversationPreviewState>> =
        chatState.conversationStates

    // 加载更多历史消息的状态Flow
    val isLoadingMoreFlow: StateFlow<Boolean> = chatState.isLoadingMore

    var selectedUser: User?
        get() = chatState.selectedChatTarget.value
        set(value) {
            AppLogger.d(" Setting selectedUser: ${value?.id} - ${value?.username}")
            sessionScope.launch {
                chatState.selectChatTarget(value)
                val owner = GlobalAppState.currentUserId?.toString() ?: return@launch
                if (value != null) {
                    if (value.isGroup && chatState.groupMessagesFor(value.id).value.isEmpty()) {
                        val rows =
                            withContext(Dispatchers.IO) {
                                LocalChatHistoryStore.getGroupMessagesBefore(owner, value.id)
                                    .messages
                            }
                        chatState.prependGroupMessages(
                            rows,
                            isCurrentAccount = {
                                GlobalAppState.currentUserId?.toString() == owner
                            },
                        )
                    } else if (!value.isGroup && chatState.messagesFor(value.id).value.isEmpty()) {
                        val rows =
                            withContext(Dispatchers.IO) {
                                LocalChatHistoryStore.getPrivateMessagesBefore(owner, value.id)
                                    .messages
                            }
                        chatState.prependMessages(
                            rows,
                            isCurrentAccount = {
                                GlobalAppState.currentUserId?.toString() == owner
                            },
                        )
                    }
                }
            }
        }

    var isLoadingMore: Boolean
        get() = chatState.isLoadingMore.value
        set(value) {
            sessionScope.launch {
                chatState.setLoadingMore(value)
            }
        }

    // 离线消息拉取状态
    internal var isFetchingOfflineMessages = false
    // 离线消息拉取期间收到的临时消息缓存
    internal val pendingMessages = mutableListOf<Message>()
    internal val pendingGroupMessages = mutableListOf<GroupMessage>()

    /**
     * 聊天助手流式消息专用入口：
     * 1) 首块创建消息
     * 2) 后续块原位更新，不走删除+新增
     */
    internal var streamUpdates: core.ConflatedStreamUpdates? = null
    internal var streamSession: Job? = null

    // 当前选中的用户（用于好友管理等）
    internal val selectedUserState = MutableStateFlow<User?>(null)
    // 好友/群请求状态Flow
    internal val friendRequestState = MutableStateFlow<List<User>>(emptyList())
    val friendRequests: StateFlow<List<User>> = friendRequestState.asStateFlow()

    internal val groupRequestState = MutableStateFlow<List<User>>(emptyList())
    val groupRequests: StateFlow<List<User>> = groupRequestState.asStateFlow()

    internal val historySyncMutex = Mutex()

    open suspend fun syncAllConversations() = historySyncMutex.withLock {
        val owner = GlobalAppState.currentUserId ?: return@withLock
        val token = GlobalAppState.currentToken ?: return@withLock
        val ownerSession = sessionJob
        fun isCurrent() =
            GlobalAppState.currentUserId == owner &&
                sessionJob === ownerSession &&
                ownerSession.isActive
        core.recoverOperation(
            onFailure = { e ->
                AppLogger.w("最近历史恢复失败: ${e.message}")
            }
        ) {
            restoreRecentHistory(
                ::isCurrent,
                { cursor -> core.getRecentHistoryPage(token, cursor) },
            ) { rows ->
                applyRecentPage(owner, token, rows, ::isCurrent)
            }
            AppLogger.d("最近七天历史恢复结束")
        }
    }

    /** Commit complete pages before confirming delivery; no offset over a shrinking unread set. */
    open suspend fun fetchOfflineMessages(): Boolean {
        val credentials =
            GlobalAppState.currentUserId?.let { owner ->
                GlobalAppState.currentToken?.let { token -> owner to token }
            }
        return if (isFetchingOfflineMessages || credentials == null) false
        else restoreOfflinePage(credentials.first, credentials.second)
    }

    /** 发送私聊消息 */
    internal val throttle = Throttle()

    open fun sendPrivateMessage(
        user: User,
        messageText: String,
        messageType: MessageType = MessageType.TEXT,
        fileUrl: String? = null,
        fileName: String? = null,
        fileSize: Long? = null,
        replyToMessageId: String? = null,
        replyToContent: String? = null,
        replyToSender: String? = null,
        onDone: () -> Unit = {},
    ) =
        enqueueSendPrivateMessage(
            user,
            messageText,
            messageType,
            fileUrl,
            fileName,
            fileSize,
            replyToMessageId,
            replyToContent,
            replyToSender,
            onDone,
        )

    /** 发送群聊消息 */
    open fun sendGroupMessage(
        group: Group,
        messageText: String,
        messageType: MessageType = MessageType.TEXT,
        fileUrl: String? = null,
        fileName: String? = null,
        fileSize: Long? = null,
        replyToMessageId: String? = null,
        replyToContent: String? = null,
        replyToSender: String? = null,
        onDone: () -> Unit = {},
    ) =
        enqueueSendGroupMessage(
            group,
            messageText,
            messageType,
            fileUrl,
            fileName,
            fileSize,
            replyToMessageId,
            replyToContent,
            replyToSender,
            onDone,
        )

    /** 清空所有聊天数据（退出登录时调用）。 取消旧协程作用域，重建新作用域，避免内存泄漏与过期任务。 */
    open fun clear() {
        saveConversationSeqIds()
        streamUpdates?.clear()
        streamUpdates = null
        streamSession = null
        historyWriter?.close()
        historyWriter = null
        historyOwner = null
        val clearingOwner = GlobalAppState.currentUserId?.toString()
        // 重置内部状态
        isFetchingOfflineMessages = false
        pendingMessages.clear()
        pendingGroupMessages.clear()
        friendRequestState.value = emptyList()
        groupRequestState.value = emptyList()

        // 重建会话作用域（取消旧会话 → 挂回 appJob）
        sessionJob.cancel()
        sessionJob = SupervisorJob(appJob)

        // 清除聊天数据
        sessionScope.launch(Dispatchers.IO) {
            chatState.clear()
            if (clearingOwner != null) LocalChatHistoryStore.clear(clearingOwner)
        }
    }

    /** 仅清空内存中的消息，不删除本地存储（清空聊天记录时调用） */
    open fun clearMessages() {
        sessionScope.launch {
            chatState.clear()
        }
    }

    /** 保存聊天历史到本地（debounce 合并：高频调用只触发一次落盘）。 所有状态访问收敛到 sessionScope(Main) 上，避免 IO 线程并发访问。 */
    internal data class HistoryBatch(
        val owner: String,
        val privateRows: MutableMap<String, Message>,
        val groupRows: MutableMap<String, GroupMessage>,
    )

    internal var historyWriter: core.HistoryWriteQueue<HistoryBatch>? = null
    internal var historyOwner: String? = null

    /** 从本地加载聊天历史 */
    open fun loadLocalChatHistory() {
        sessionScope.launch(Dispatchers.IO) {
            val userId = GlobalAppState.currentUserId ?: return@launch
            val accountId = userId.toString()
            core.recoverOperation(
                onFailure = { e ->
                    AppLogger.d(" 从本地加载聊天历史失败: ${e.message}")
                    logger.error(e) { "ChatViewModel error" }
                }
            ) {
                val history = LocalChatHistoryStore.restore(accountId)
                if (GlobalAppState.currentUserId != userId) return@launch
                if (history.privateMessages.isNotEmpty() || history.groupMessages.isNotEmpty()) {
                    // 加载到聊天状态
                    chatState.prependMessages(
                        history.privateMessages.map {
                            it.copy(deliveryState = it.deliveryState.afterRestart())
                        },
                        markAsUnread = false,
                    )
                    chatState.prependGroupMessages(
                        history.groupMessages.map {
                            it.copy(deliveryState = it.deliveryState.afterRestart())
                        },
                        markAsUnread = false,
                    )
                    AppLogger.d(
                        " 从本地加载聊天历史，私聊消息: ${history.privateMessages.size}条, 群聊消息: ${history.groupMessages.size}条"
                    )
                }
            }
        }
    }
}
