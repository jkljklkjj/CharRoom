package presentation.viewmodel

import core.Chat
import core.GlobalApiService
import core.LocalChatHistoryStore
import io.github.oshai.kotlinlogging.KotlinLogging
import core.MsgType
import core.ServerConfig.AGENT_ASSISTANT_ID
import core.batchOnlineStatus
import core.buildChatPayload
import core.buildGroupChatPayload
import core.json
import core.state.ChatState
import core.state.GlobalAppState
import core.state.GlobalChatState
import data.repository.ChatRepository
import data.repository.GlobalChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import model.Group
import model.GroupMessage
import model.Message
import model.MessageIdGenerator
import model.MessageType
import model.User
import core.state.ConversationPreviewState
import core.Throttle
import core.ThrottleOp

private const val AGENT_ASSISTANT_ID = 900000001

/**
 * 聊天 ViewModel
 * 处理聊天相关的 UI 逻辑和状态。
 *
 * 职责拆分：
 * - 消息 CRUD：addMessage, prependMessages, deleteMessage...
 * - 社交操作：addFriend, deleteFriend, addGroup, fetchRequests
 * - 个人资料：updateUserProfile, getCurrentUserProfile
 * - 委托：ConversationSyncService（同步）、MessageSender（发送）
 */
private val logger = KotlinLogging.logger {}

open class ChatViewModel(
    protected val chatRepository: ChatRepository = GlobalChatRepository,
    protected val chatState: ChatState = GlobalChatState
) {
    // 唯一的作用域嵌套：应用级（永不取消）→ 会话级（clear 时重建）
    private val appJob = SupervisorJob()
    private var sessionJob: Job = SupervisorJob(appJob)
    private val sessionScope get() = CoroutineScope(sessionJob + Dispatchers.Main.immediate)
    /** Android 子类需要访问 coroutineScope */
    protected val coroutineScope: CoroutineScope get() = sessionScope

    // 委托服务
    private val syncService = ConversationSyncService(chatRepository, chatState, sessionScope)
    val messageSender = MessageSender(chatState, sessionScope)

    // 用户列表状态Flow
    val usersFlow: StateFlow<List<User>> = chatState.users

    // 私聊消息状态Flow
    val messagesFlow: StateFlow<List<Message>> = chatState.messages

    // 群聊消息状态Flow
    val groupMessagesFlow: StateFlow<List<GroupMessage>> = chatState.groupMessages

    // 当前选中的用户/群组Flow
    val selectedChatTargetFlow: StateFlow<User?> = chatState.selectedChatTarget

    // 会话预览状态Flow（最近消息时间、未读数）
    val conversationStatesFlow: StateFlow<Map<Int, ConversationPreviewState>> = chatState.conversationStates

    // 加载更多历史消息的状态Flow
    val isLoadingMoreFlow: StateFlow<Boolean> = chatState.isLoadingMore

    var selectedUser: User?
        get() = chatState.selectedChatTarget.value
        set(value) {
            chatState.selectChatTarget(value)
        }

    var isLoadingMore: Boolean
        get() = chatState.isLoadingMore.value
        set(value) {
            chatState.setLoadingMore(value)
        }

    // ═══ 消息 CRUD ═══════════════════════════════════════

    fun updateUserOnlineStatus(userId: Int, online: Boolean) {
        sessionScope.launch {
            chatState.updateUserOnlineStatus(userId, online)
        }
    }

    fun prependMessages(newMessages: List<Message>) {
        sessionScope.launch {
            chatState.prependMessages(newMessages)
        }
    }

    fun prependGroupMessages(newMessages: List<GroupMessage>) {
        sessionScope.launch {
            chatState.prependGroupMessages(newMessages)
        }
    }

    fun addMessage(message: Message) {
        sessionScope.launch {
            // 如果是自己发的消息，检查是否有对应的乐观消息需要替换
            if (message.sender) {
                val currentUsers = chatState.users.value
                val senderUser = currentUsers.find { it.id == message.senderId }
                if (senderUser == null) {
                    // 如果发送者不在联系人列表中，添加到列表
                    chatState.upsertUser(User(
                        id = message.senderId,
                        username = "用户${message.senderId}"
                    ))
                }
            }
            chatState.addMessage(message)
        }
    }

    fun addGroupMessage(message: GroupMessage) {
        sessionScope.launch {
            chatState.addGroupMessage(message)
        }
    }

    fun updateMessageSentStatus(messageId: String, isSent: Boolean) {
        sessionScope.launch {
            chatState.updateMessageSentStatus(messageId, isSent)
        }
    }

    fun updateMessage(updatedMessage: Message) {
        sessionScope.launch {
            chatState.updateMessage(updatedMessage)
        }
    }

    /**
     * 流式 Agent 消息：首块创建，后续块原位更新。
     */
    fun upsertAgentStreamMessage(messageId: String, fullContent: String) {
        sessionScope.launch {
            val existingMessage = chatState.messages.value.find {
                it.messageId == messageId
            }
            if (existingMessage != null) {
                chatState.updateMessage(existingMessage.copy(message = fullContent))
            } else {
                val agentMessage = Message(
                    senderId = AGENT_ASSISTANT_ID,
                    message = fullContent,
                    sender = false,
                    receiverId = GlobalAppState.currentUserId ?: 0,
                    timestamp = System.currentTimeMillis(),
                    isSent = true,
                    messageId = messageId
                )
                chatState.addMessage(agentMessage)
            }
        }
    }

    fun updateGroupMessageSentStatus(messageId: String, isSent: Boolean) {
        sessionScope.launch {
            chatState.updateGroupMessageSentStatus(messageId, isSent)
        }
    }

    fun deleteMessage(messageId: String) {
        sessionScope.launch {
            chatState.deleteMessage(messageId)
        }
    }

    fun deleteGroupMessage(messageId: String) {
        sessionScope.launch {
            chatState.deleteGroupMessage(messageId)
        }
    }

    // ═══ 联系人 & 同步 ═══════════════════════════════════

    private val _friendRequests = MutableStateFlow<List<User>>(emptyList())
    val friendRequests: StateFlow<List<User>> = _friendRequests.asStateFlow()

    private val _groupRequests = MutableStateFlow<List<User>>(emptyList())
    val groupRequests: StateFlow<List<User>> = _groupRequests.asStateFlow()

    /**
     * 加载好友和群组列表 + 触发 SeqId 增量同步。
     */
    fun loadContacts() {
        syncService.loadContacts()
    }

    /**
     * 增量同步单个会话（sync_hint 触发）。
     */
    open suspend fun syncConversation(conversationId: String, seqId: Long) {
        syncService.syncConversation(conversationId, seqId)
    }

    /**
     * 增量同步所有会话（基于 seqId 游标）。
     */
    open suspend fun syncAllConversations() {
        syncService.syncAllConversations()
    }

    /**
     * 拉取好友和群聊请求
     */
    fun fetchRequests() {
        sessionScope.launch(Dispatchers.IO) {
            try {
                val friendRequests = chatRepository.fetchFriendRequests()
                val groupRequests = chatRepository.fetchGroupRequests()

                _friendRequests.value = friendRequests
                _groupRequests.value = groupRequests
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                println("[ChatViewModel] 拉取请求失败: ${e.message}")
                logger.error(e) { "ChatViewModel error" }
            }
        }
    }

    // ═══ 社交操作 ═════════════════════════════════════════

    /**
     * 获取当前用户信息
     */
    fun getCurrentUserProfile(onResult: (User?) -> Unit) {
        sessionScope.launch {
            val user = chatRepository.getCurrentUserProfile()
            onResult(user)
        }
    }

    /**
     * 添加好友
     */
    fun addFriend(account: String, onResult: (Boolean) -> Unit) {
        sessionScope.launch {
            val success = chatRepository.addFriend(account)
            if (success) {
                loadContacts()
            }
            onResult(success)
        }
    }

    /**
     * 删除好友
     */
    fun deleteFriend(friendId: Int, onResult: (Boolean) -> Unit) {
        sessionScope.launch {
            val success = chatRepository.deleteFriend(friendId)
            if (success) {
                loadContacts()
                if (chatState.selectedChatTarget.value?.id == friendId) {
                    chatState.selectChatTarget(null)
                }
            }
            onResult(success)
        }
    }

    /**
     * 加入群组
     */
    fun addGroup(groupId: String, onResult: (Boolean) -> Unit) {
        sessionScope.launch {
            val success = chatRepository.addGroup(groupId)
            if (success) {
                loadContacts()
            }
            onResult(success)
        }
    }

    // ═══ 消息发送（委托给 MessageSender）═══════════════════

    /**
     * 发送私聊消息
     */
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
        onDone: () -> Unit = {}
    ) {
        messageSender.sendPrivateMessage(
            user, messageText, messageType, fileUrl, fileName, fileSize,
            replyToMessageId, replyToContent, replyToSender, onDone
        )
    }

    /**
     * 发送群聊消息
     */
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
        onDone: () -> Unit = {}
    ) {
        messageSender.sendGroupMessage(
            group, messageText, messageType, fileUrl, fileName, fileSize,
            replyToMessageId, replyToContent, replyToSender, onDone
        )
    }

    // ═══ 消息重试 ═════════════════════════════════════════

    /**
     * 重试发送失败的消息。
     */
    fun retryMessage(message: Message) {
        val user = chatState.users.value.find { it.id == message.receiverId } ?: return
        // 更新状态为发送中
        chatState.updateMessageSentStatus(message.messageId, true)
        // 重新发送
        messageSender.sendPrivateMessage(
            user = user,
            messageText = message.message,
            messageType = message.messageType,
            fileUrl = message.fileUrl,
            fileName = message.fileName,
            fileSize = message.fileSize,
            replyToMessageId = message.replyToMessageId,
            replyToContent = message.replyToContent,
            replyToSender = message.replyToSender
        )
    }

    // ═══ 个人资料 ═════════════════════════════════════════

    /**
     * 更新用户个人资料
     */
    fun updateUserProfile(
        username: String,
        phone: String,
        signature: String,
        password: String? = null,
        onResult: (Boolean) -> Unit
    ) {
        sessionScope.launch {
            val success = chatRepository.updateUserProfile(username, phone, signature, password)
            onResult(success)
        }
    }

    /**
     * 更新用户列表
     */
    fun updateUsers(users: List<User>) {
        sessionScope.launch {
            chatState.updateUsers(users)
        }
    }

    // ═══ 离线消息 ═════════════════════════════════════════

    private var isFetchingOfflineMessages = false
    private val pendingMessages = mutableListOf<Message>()
    private val pendingGroupMessages = mutableListOf<GroupMessage>()

    /**
     * 从本地存储恢复离线待发送消息队列。
     */
    fun restorePendingMessages() {
        try {
            val (private, group) = LocalChatHistoryStore.restorePendingMessages()
            if (private.isNotEmpty() || group.isNotEmpty()) {
                pendingMessages.addAll(private)
                pendingGroupMessages.addAll(group)
                println("[ChatViewModel] 恢复离线队列: ${private.size} 条私聊, ${group.size} 条群聊")
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
    }

    /**
     * 保存离线待发送消息队列到本地存储。
     */
    private fun savePendingMessages() {
        try {
            LocalChatHistoryStore.savePendingMessages(pendingMessages, pendingGroupMessages)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
    }

    /**
     * 分页拉取离线消息
     */
    open suspend fun fetchOfflineMessages(page: Int = 0, pageSize: Int = 50): Boolean {
        if (isFetchingOfflineMessages) return false

        isFetchingOfflineMessages = true
        isLoadingMore = page > 0

        return try {
            val maxPages = 50 // 防止无限循环
            var currentPage = page
            var hasMore = true

            while (hasMore && currentPage - page < maxPages) {
                val messages = chatRepository.getOfflineMessagesPage(currentPage, pageSize)
                if (messages.isEmpty()) break

                chatState.prependMessages(messages.sortedBy { it.timestamp }, markAsUnread = true)
                currentPage++
                hasMore = messages.size >= pageSize
            }

            if (!hasMore) {
                pendingMessages.sortedBy { it.timestamp }.forEach { chatState.addMessage(it) }
                pendingGroupMessages.sortedBy { it.timestamp }.forEach { chatState.addGroupMessage(it) }
                pendingMessages.clear()
                pendingGroupMessages.clear()
                LocalChatHistoryStore.clearPendingMessages()
            }

            hasMore
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            false
        } finally {
            isFetchingOfflineMessages = false
            isLoadingMore = false
            sessionScope.launch(Dispatchers.IO) {
                saveChatHistoryToLocal()
            }
        }
    }

    // ═══ 清理 ═════════════════════════════════════════════

    /**
     * 清除所有数据
     */
    open fun clear() {
        sessionScope.launch {
            chatState.clear()
        }
    }

    // ═══ 持久化 ═══════════════════════════════════════════

    private fun saveChatHistoryToLocal() {
        try {
            val userId = GlobalAppState.currentUserId ?: return
            val privateMessages = chatState.messages.value
            val groupMessages = chatState.groupMessages.value
            LocalChatHistoryStore.save(userId.toString(), privateMessages, groupMessages)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
    }
}
