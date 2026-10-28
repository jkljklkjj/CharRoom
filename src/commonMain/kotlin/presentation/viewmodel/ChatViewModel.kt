package presentation.viewmodel

import core.Chat
import core.restoreRecentHistory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import core.GlobalApiService
import core.LocalChatHistoryStore
import core.AppLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import core.MsgType

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
import kotlinx.coroutines.withContext
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

private val AGENT_ASSISTANT_ID = core.ServerConfig.AGENT_ASSISTANT_ID

/**
 * 聊天ViewModel
 * 处理聊天相关的UI逻辑和状态
 */
private val logger = KotlinLogging.logger {}

// ── 本地历史写盘 debounce ────────────────────────────────
// 合并高频消息变更；写入队列仅持有尚未提交的最新修订，成功后释放正文。
private const val SAVE_HISTORY_DEBOUNCE_MS = 1000L


open class ChatViewModel(
    protected val chatRepository: ChatRepository,
    protected val chatState: ChatState
) {
    // 唯一的作用域嵌套：应用级（永不取消）→ 会话级（clear 时重建）
    private val appJob = SupervisorJob()
    private var sessionJob: Job = SupervisorJob(appJob)
    private val sessionScope get() = CoroutineScope(sessionJob + Dispatchers.Main.immediate)
    /** Android 子类需要访问 coroutineScope */
    protected val coroutineScope: CoroutineScope get() = sessionScope
    // 用户列表状态Flow
    val usersFlow: StateFlow<List<User>> = chatState.users

    // 私聊消息状态Flow
    val messagesFlow: StateFlow<List<Message>> = chatState.messages

    // 群聊消息状态Flow
    val groupMessagesFlow: StateFlow<List<GroupMessage>> = chatState.groupMessages

    // 当前选中的用户/群组Flow
    val selectedChatTargetFlow: StateFlow<User?> = chatState.selectedChatTarget

    // 会话预览状态Flow（最近消息时间、未读数）
    val conversationStatesFlow: StateFlow<Map<model.ConversationKey, ConversationPreviewState>> = chatState.conversationStates

    // 加载更多历史消息的状态Flow
    val isLoadingMoreFlow: StateFlow<Boolean> = chatState.isLoadingMore

    var selectedUser: User?
        get() = chatState.selectedChatTarget.value
        set(value) {
            AppLogger.d(" Setting selectedUser: ${value?.id} - ${value?.username}")
            sessionScope.launch {
                chatState.selectChatTarget(value)
                val owner=GlobalAppState.currentUserId?.toString() ?: return@launch
                if(value!=null) {
                    if(value.isGroup && chatState.groupMessagesFor(value.id).value.isEmpty()) {
                        val rows=withContext(Dispatchers.IO) { LocalChatHistoryStore.getGroupMessagesPage(owner,value.id,0,50) }
                        chatState.prependGroupMessages(rows)
                    } else if(!value.isGroup && chatState.messagesFor(value.id).value.isEmpty()) {
                        val rows=withContext(Dispatchers.IO) { LocalChatHistoryStore.getPrivateMessagesPage(owner,value.id,0,50) }
                        chatState.prependMessages(rows)
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
    private var isFetchingOfflineMessages = false
    // 离线消息拉取期间收到的临时消息缓存
    private val pendingMessages = mutableListOf<Message>()
    private val pendingGroupMessages = mutableListOf<GroupMessage>()

    /**
     * 更新用户在线状态
     */
    fun updateUserOnlineStatus(userId: Int, online: Boolean) {
        sessionScope.launch {
            chatState.updateUserOnlineStatus(userId, online)
        }
    }

    /**
     * 前置添加私聊消息（用于加载历史消息）
     */
    fun prependMessages(newMessages: List<Message>) {
        sessionScope.launch {
            chatState.prependMessages(newMessages)
            chatState.queueHistoryChanges(newMessages,emptyList())
            // 异步保存到本地存储
            saveChatHistoryToLocal()
        }
    }

    /**
     * 前置添加群聊消息（用于加载历史消息）
     */
    fun prependGroupMessages(newMessages: List<GroupMessage>) {
        sessionScope.launch {
            chatState.prependGroupMessages(newMessages)
            chatState.queueHistoryChanges(emptyList(),newMessages)
            // 异步保存到本地存储
            saveChatHistoryToLocal()
        }
    }

    /**
     * 添加新的私聊消息
     */
    fun addMessage(message: Message) {
        sessionScope.launch {
            // 离线消息拉取中，先缓存到临时队列，避免顺序混乱
            if (isFetchingOfflineMessages && !message.sender) {
                pendingMessages.add(message)
                AppLogger.d(" 离线消息拉取中，私聊消息已缓存: messageId=${message.messageId}")
                return@launch
            }

            // 收到陌生人的第一条私聊消息时，先补进联系人列表，避免必须重启才能看到
            if (!message.sender && message.senderId != (GlobalAppState.currentUserId ?: -1) && message.senderId != AGENT_ASSISTANT_ID) {
                val currentUsers = chatState.users.value
                if (currentUsers.none { !it.isGroup && it.id == message.senderId }) {
                    val senderUser = runCatching {
                        chatRepository.getUserDetail(message.senderId.toString())
                    }.getOrNull() ?: User(
                        id = message.senderId,
                        username = "用户${message.senderId}",
                        online = true
                    )
                    chatState.upsertUser(senderUser)
                }
            }

            chatState.addMessage(message)
            // 异步保存到本地存储
            saveChatHistoryToLocal()
        }
    }

    /**
     * 添加新的群聊消息
     */
    fun addGroupMessage(message: GroupMessage) {
        sessionScope.launch {
            chatState.addGroupMessage(message)
            // 异步保存到本地存储
            saveChatHistoryToLocal()
        }
    }

    /**
     * 更新私聊消息发送状态
     */
    private fun updateDeliveryState(id: String, state: model.MessageDeliveryState) {
        sessionScope.launch { chatState.updateDeliveryState(id, state); saveChatHistoryToLocal() }
    }

    fun confirmMessageId(clientId: String, serverId: String, seq: Long, conversation: String) {
        sessionScope.launch { chatState.confirmMessageId(clientId, serverId, seq, conversation) }
    }

    fun updateMessageSentStatus(messageId: String, isSent: Boolean) {
        sessionScope.launch {
            chatState.updateMessageSentStatus(messageId, isSent)
            // 异步保存到本地存储
            saveChatHistoryToLocal()
        }
    }

    /**
     * 重试发送失败的消息
     */
    fun retryMessage(message: Message) {
        if (message.deliveryState != model.MessageDeliveryState.FAILED) return
        val owner = GlobalAppState.currentUserId
        val sendingSession = sessionJob
        sessionScope.launch {
            val targetId = if (message.sender) message.receiverId else message.senderId
            val user = chatState.users.value.find { !it.isGroup && it.id == targetId }
            if (user == null) {
                logger.warn { "retryMessage: 找不到用户 $targetId" }
                return@launch
            }

            val timestamp = message.timestamp
            val messageId = message.messageId

            val payload = buildChatPayload(
                targetClientId = user.id.toString(),
                content = message.message,
                timestamp = timestamp,
                replyToMessageId = message.replyToMessageId,
                replyToContent = message.replyToContent,
                replyToSender = message.replyToSender,
                messageType = message.messageType.ordinal,
                fileUrl = message.fileUrl,
                fileName = message.fileName,
                fileSize = message.fileSize,
                messageId = messageId
            )

            updateDeliveryState(messageId, model.MessageDeliveryState.SENDING)
            Chat.send(
                payload = payload,
                type = MsgType.CHAT,
                targetClientId = user.id.toString(),
                expectedResponses = 1
            ) { success, _ ->
                if (owner == GlobalAppState.currentUserId && sendingSession === sessionJob) sessionScope.launch {
                    updateMessageSentStatus(messageId, success)
                }
            }
        }
    }

    fun retryGroupMessage(message: GroupMessage) {
        if (message.deliveryState != model.MessageDeliveryState.FAILED) return
        val owner = GlobalAppState.currentUserId
        val sendingSession = sessionJob
        val payload = buildGroupChatPayload(message.groupId.toString(), message.text,
            replyToMessageId = message.replyToMessageId, replyToContent = message.replyToContent,
            replyToSender = message.replyToSender, messageType = message.messageType.ordinal,
            fileUrl = message.fileUrl, fileName = message.fileName, fileSize = message.fileSize,
            messageId = message.messageId, timestamp = message.timestamp)
        updateDeliveryState(message.messageId, model.MessageDeliveryState.SENDING)
        Chat.send(payload, MsgType.GROUP_CHAT, message.groupId.toString(), 1) { success, _ ->
            if (owner == GlobalAppState.currentUserId && sendingSession === sessionJob)
                updateGroupMessageSentStatus(message.messageId, success)
        }
    }

    /**
     * 原位更新私聊消息内容（用于流式输出）
     */
    fun updateMessage(updatedMessage: Message) {
        sessionScope.launch {
            chatState.updateMessage(updatedMessage)
            // 异步保存到本地存储
            saveChatHistoryToLocal()
        }
    }

    /**
     * 聊天助手流式消息专用入口：
     * 1) 首块创建消息
     * 2) 后续块原位更新，不走删除+新增
     */
    private var streamUpdates:core.ConflatedStreamUpdates?=null
    private var streamSession:Job?=null
    fun upsertAgentStreamMessage(messageId:String,fullContent:String,done:Boolean=false,error:Boolean=false) {
        if(streamSession!==sessionJob) {
            streamUpdates?.clear();streamSession=sessionJob
            streamUpdates=core.ConflatedStreamUpdates(sessionScope) { snapshot ->
                val owner=GlobalAppState.currentUserId ?: return@ConflatedStreamUpdates
                chatState.applyAgentSnapshot(owner,snapshot)
                saveChatHistoryToLocal()
            }
        }
        streamUpdates!!.submit(core.AgentSnapshot(messageId,fullContent,done||error))
    }

    /**
     * 更新群聊消息发送状态
     */
    fun updateGroupMessageSentStatus(messageId: String, isSent: Boolean) {
        sessionScope.launch {
            chatState.updateGroupMessageSentStatus(messageId, isSent)
            // 异步保存到本地存储
            saveChatHistoryToLocal()
        }
    }

    // 当前选中的用户（用于好友管理等）
    private val _selectedUser = MutableStateFlow<User?>(null)
    // 好友/群请求状态Flow
    private val _friendRequests = MutableStateFlow<List<User>>(emptyList())
    val friendRequests: StateFlow<List<User>> = _friendRequests.asStateFlow()

    private val _groupRequests = MutableStateFlow<List<User>>(emptyList())
    val groupRequests: StateFlow<List<User>> = _groupRequests.asStateFlow()

    /**
     * 加载好友和群组列表；历史恢复独立于联系人加载。
     */
    fun loadContacts() {
        sessionScope.launch(Dispatchers.IO) {
            val cachedContacts = chatRepository.getCachedContacts()
            if (cachedContacts.isNotEmpty() && chatState.users.value.isEmpty()) {
                chatState.updateUsers(cachedContacts)
            }

            val contacts = chatRepository.fetchAllContacts()
            if (contacts.isNotEmpty()) {
                chatState.replaceUsersPreservingOrder(contacts)
                loadConversationSeqIds()
                saveConversationSeqIds()
                val token = GlobalAppState.currentToken
                if (token != null) {
                    try {
                        val ids = contacts.filterNot { it.isGroup }.map { it.id }.filter { it > 0 && it != AGENT_ASSISTANT_ID }
                        if (ids.isNotEmpty()) batchOnlineStatus(token, ids).forEach { (id, online) ->
                            id.toIntOrNull()?.let { chatState.updateUserOnlineStatus(it, online) }
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        AppLogger.w("加载联系人在线状态失败: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * 拉取好友和群聊请求
     */
    fun fetchRequests() {
        sessionScope.launch(Dispatchers.IO) {
            try {
                AppLogger.d(" 开始拉取好友和群聊请求")
                val friendRequests = chatRepository.fetchFriendRequests()
                val groupRequests = chatRepository.fetchGroupRequests()

                _friendRequests.value = friendRequests
                _groupRequests.value = groupRequests

                AppLogger.d(" 拉取请求完成: 好友请求 ${friendRequests.size} 条, 群聊请求 ${groupRequests.size} 条")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.d(" 拉取请求失败: ${e.message}")
                logger.error(e) { "ChatViewModel error" }
            }
        }
    }

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
                // 如果当前选中的就是这个好友，取消选中
                if (_selectedUser.value?.conversationKey == model.ConversationKey.privateChat(friendId)) {
                    _selectedUser.value = null
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
                // 重新加载联系人列表
                loadContacts()
            }
            onResult(success)
        }
    }

    private val historySyncMutex = Mutex()

    /** Restore the last seven days even when this device has no contacts or seq cursor. */
    open suspend fun syncAllConversations() = historySyncMutex.withLock {
        val owner = GlobalAppState.currentUserId ?: return@withLock
        val token = GlobalAppState.currentToken ?: return@withLock
        val ownerSession = sessionJob
        fun isCurrent() = GlobalAppState.currentUserId == owner && sessionJob === ownerSession && ownerSession.isActive
        try {
            restoreRecentHistory(::isCurrent, { cursor -> core.getRecentHistoryPage(token, cursor) }) { rows ->
                if (!isCurrent()) return@restoreRecentHistory
                val privateMessages = rows.filterNot { it.isGroup }.map { it.privateMessage(owner) }
                val groupMessages = rows.filter { it.isGroup }.map { it.groupMessage() }
                // Persist the whole page before proceeding; the UI LRU may keep fewer records.
                LocalChatHistoryStore.save(owner.toString(), privateMessages, groupMessages)
                if (!isCurrent()) return@restoreRecentHistory
                chatState.prependMessages(privateMessages, markAsUnread = false, isCurrentAccount = ::isCurrent)
                if (!isCurrent()) return@restoreRecentHistory
                chatState.prependGroupMessages(groupMessages, markAsUnread = false, isCurrentAccount = ::isCurrent)
            }
            AppLogger.d("最近七天历史恢复结束")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.w("最近历史恢复失败: ${e.message}")
        }
    }

    /**
     * 分页拉取离线消息（每次拉取50条，自动处理顺序问题）
     * 保留作为降级回退方案
     */
    open suspend fun fetchOfflineMessages(page: Int = 0, pageSize: Int = 50): Boolean {
        if (isFetchingOfflineMessages) {
            AppLogger.d(" 已有离线消息拉取任务在进行中，跳过重复请求")
            return false
        }

        isFetchingOfflineMessages = true
        isLoadingMore = page > 0 // 第一页不显示加载状态

        return try {
            AppLogger.d(" 开始拉取离线消息，页码: $page, 每页大小: $pageSize")

            // 分页拉取离线消息（需要API支持分页参数，这里假设接口已经支持）
            val messages = chatRepository.getOfflineMessagesPage(page, pageSize)

            if (messages.isEmpty()) {
                AppLogger.d(" 离线消息拉取完成，没有更多消息")
                return false
            }

            // 分页拉取的离线消息本身就是按时间从旧到新排序的，直接批量前置插入
            chatState.prependMessages(messages.sortedBy { it.timestamp }, markAsUnread = true)
            chatState.queueHistoryChanges(messages,emptyList())
            AppLogger.d(" 成功拉取离线消息 ${messages.size} 条，页码: $page，批量插入完成")

            // 递归拉取下一页
            val hasMore = fetchOfflineMessages(page + 1, pageSize)

            // 所有页面拉取完成后，处理缓存的临时消息
            if (!hasMore) {
                AppLogger.d(" 所有离线消息拉取完成，开始处理缓存的临时消息，共 ${pendingMessages.size} 条私聊, ${pendingGroupMessages.size} 条群聊")
                pendingMessages.sortedBy { it.timestamp }.forEach { chatState.addMessage(it) }
                pendingGroupMessages.sortedBy { it.timestamp }.forEach { chatState.addGroupMessage(it) }
                pendingMessages.clear()
                pendingGroupMessages.clear()
            }

            hasMore
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.d(" 拉取离线消息失败: ${e.message}")
            logger.error(e) { "ChatViewModel error" }
            false
        } finally {
            isFetchingOfflineMessages = false
            isLoadingMore = false
            // 保存到本地存储
            sessionScope.launch(Dispatchers.IO) {
                saveChatHistoryToLocal()
            }
        }
    }

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

    /**
     * 删除私聊消息
     */
    fun deleteMessage(messageId: String) {
        sessionScope.launch {
            val owner = GlobalAppState.currentUserId ?: return@launch
            flushLocalHistory()
            chatState.deleteMessage(messageId)
            withContext(Dispatchers.IO) { LocalChatHistoryStore.deleteMessage(owner.toString(), messageId, false) }
            // 异步保存到本地存储
            saveChatHistoryToLocal()
        }
    }

    /**
     * 删除群聊消息
     */
    fun deleteGroupMessage(messageId: String) {
        sessionScope.launch {
            val owner = GlobalAppState.currentUserId ?: return@launch
            flushLocalHistory()
            chatState.deleteGroupMessage(messageId)
            withContext(Dispatchers.IO) { LocalChatHistoryStore.deleteMessage(owner.toString(), messageId, true) }
            // 异步保存到本地存储
            saveChatHistoryToLocal()
        }
    }

    /**
     * 发送私聊消息
     */
    private val throttle = Throttle()

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
        if (throttle.shouldThrottle(ThrottleOp.PRIVATE_SEND)) {
            sessionScope.launch { onDone() }
            return
        }
        val currentUserId = GlobalAppState.currentUserId
        AppLogger.d(" 准备发送私聊消息，currentUserId: $currentUserId, 接收者: ${user.id}")

        val sendingSession = sessionJob
        val senderId = currentUserId ?: 0 // 0=未知，服务端从连接推导

        try {
            val timestamp = System.currentTimeMillis()
            val messageId = MessageIdGenerator.generateMessageId(senderId, messageText + fileUrl.orEmpty(), timestamp)

            // 创建消息对象
            val message = Message(
                senderId = senderId,
                message = messageText,
                sender = true,
                receiverId = user.id,
                timestamp = timestamp,
                isSent = false,
                deliveryState = model.MessageDeliveryState.QUEUED,
                messageId = messageId,
                replyToMessageId = replyToMessageId,
                replyToContent = replyToContent,
                replyToSender = replyToSender,
                messageType = messageType,
                fileUrl = fileUrl,
                fileName = fileName,
                fileSize = fileSize
            )

            // 发送前先把对方放入联系人列表，方便立即进入聊天并发送招呼消息
            sessionScope.launch {
                chatState.upsertUser(user)
            }

            // 添加到本地消息列表
            addMessage(message)
            AppLogger.d(" 本地消息已添加，messageId: $messageId")

            // 构建 protobuf 消息（userId 服务端会从 QUIC 连接推导，传 0 即可）
            val payload = buildChatPayload(
                targetClientId = user.id.toString(),
                content = messageText,
                timestamp = timestamp,
                replyToMessageId = replyToMessageId,
                replyToContent = replyToContent,
                replyToSender = replyToSender,
                messageType = messageType.ordinal,
                fileUrl = fileUrl,
                fileName = fileName,
                fileSize = fileSize,
                messageId = messageId
            )

            updateDeliveryState(messageId, model.MessageDeliveryState.SENDING)
            // 通过QUIC发送消息
            Chat.send(
                payload = payload,
                type = MsgType.CHAT,
                targetClientId = user.id.toString(),
                expectedResponses = 1
            ) callback@{ success, responses ->
                if (GlobalAppState.currentUserId != senderId || sessionJob !== sendingSession) return@callback
                AppLogger.d(" QUIC消息发送结果: $success, messageId: $messageId")
                sessionScope.launch {
                    if (success) {
                        updateMessageSentStatus(messageId, true)
                        AppLogger.d(" 消息状态已更新，success: $success, messageId: $messageId")
                    } else {
                        updateDeliveryState(messageId, model.MessageDeliveryState.QUEUED)
                        delay(1500)
                        updateDeliveryState(messageId, model.MessageDeliveryState.SENDING)
                        Chat.send(
                            payload = payload,
                            type = MsgType.CHAT,
                            targetClientId = user.id.toString(),
                            expectedResponses = 1
                        ) retry@{ retrySuccess, _ ->
                            if (GlobalAppState.currentUserId != senderId || sessionJob !== sendingSession) return@retry
                            sessionScope.launch {
                                updateMessageSentStatus(messageId, retrySuccess)
                                AppLogger.d(" 消息状态已更新，retrySuccess: $retrySuccess, messageId: $messageId")
                            }
                        }
                    }
                }
            }
            onDone() // Input submission is complete; server confirmation updates only message state.
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.d(" 发送消息流程异常: ${e.message}")
            logger.error(e) { "ChatViewModel error" }
            sessionScope.launch {
                onDone() // 发生任何异常都要恢复UI状态
            }
        }
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
        if (throttle.shouldThrottle(ThrottleOp.GROUP_SEND)) {
            sessionScope.launch { onDone() }
            return
        }
        // 获取当前用户信息
        val currentUserId = GlobalAppState.currentUserId
        AppLogger.d(" 准备发送群聊消息，currentUserId: $currentUserId, 群组: ${group.id}")

        val sendingSession = sessionJob
        val senderId = currentUserId ?: 0
        val senderName = if (currentUserId != null) {
            chatState.users.value.find { !it.isGroup && it.id == currentUserId }?.username ?: "我"
        } else {
            "我"
        }

        try {
            val timestamp = System.currentTimeMillis()
            val messageId = MessageIdGenerator.generateGroupMessageId(group.id, senderId, messageText + fileUrl.orEmpty(), timestamp)

            // 创建群聊消息对象
            val groupMessage = GroupMessage(
                groupId = group.id,
                senderName = senderName,
                text = messageText,
                senderId = senderId,
                timestamp = timestamp,
                isSent = false,
                deliveryState = model.MessageDeliveryState.QUEUED,
                messageId = messageId,
                replyToMessageId = replyToMessageId,
                replyToContent = replyToContent,
                replyToSender = replyToSender,
                messageType = messageType,
                fileUrl = fileUrl,
                fileName = fileName,
                fileSize = fileSize
            )

            // 添加到本地消息列表
            addGroupMessage(groupMessage)
            AppLogger.d(" 本地群消息已添加，messageId: $messageId")

            // 构建 protobuf 消息（userId 服务端会从 QUIC 连接推导）
            val payload = buildGroupChatPayload(
                targetClientId = group.id.toString(),
                content = messageText,
                replyToMessageId = replyToMessageId,
                replyToContent = replyToContent,
                replyToSender = replyToSender,
                messageType = messageType.ordinal,
                fileUrl = fileUrl,
                fileName = fileName,
                fileSize = fileSize,
                messageId = messageId,
                timestamp = timestamp
            )

            updateDeliveryState(messageId, model.MessageDeliveryState.SENDING)
            // 通过QUIC发送群聊消息
            Chat.send(
                payload = payload,
                type = MsgType.GROUP_CHAT,
                targetClientId = group.id.toString(),
                expectedResponses = 1
            ) callback@{ success, responses ->
                if (GlobalAppState.currentUserId != senderId || sessionJob !== sendingSession) return@callback
                AppLogger.d(" QUIC群消息发送结果: $success, messageId: $messageId")
                sessionScope.launch {
                    if (success) {
                        updateGroupMessageSentStatus(messageId, true)
                        AppLogger.d(" 群消息状态已更新，success: $success, messageId: $messageId")
                    } else {
                        updateDeliveryState(messageId, model.MessageDeliveryState.QUEUED)
                        delay(1500)
                        updateDeliveryState(messageId, model.MessageDeliveryState.SENDING)
                        Chat.send(
                            payload = payload,
                            type = MsgType.GROUP_CHAT,
                            targetClientId = group.id.toString(),
                            expectedResponses = 1
                        ) retry@{ retrySuccess, _ ->
                            if (GlobalAppState.currentUserId != senderId || sessionJob !== sendingSession) return@retry
                            sessionScope.launch {
                                updateGroupMessageSentStatus(messageId, retrySuccess)
                                AppLogger.d(" 群消息状态已更新，retrySuccess: $retrySuccess, messageId: $messageId")
                            }
                        }
                    }
                }
            }
            onDone() // Input submission is complete; server confirmation updates only message state.
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.d(" 发送群消息流程异常: ${e.message}")
            logger.error(e) { "ChatViewModel error" }
            sessionScope.launch {
                onDone() // 发生任何异常都要恢复UI状态
            }
        }
    }

    /**
     * 清空所有聊天数据（退出登录时调用）。
     * 取消旧协程作用域，重建新作用域，避免内存泄漏与过期任务。
     */
    open fun clear() {
        saveConversationSeqIds()
        streamUpdates?.clear();streamUpdates=null;streamSession=null
        historyWriter?.close(); historyWriter = null; historyOwner = null
        val clearingOwner = GlobalAppState.currentUserId?.toString()
        // 重置内部状态
        isFetchingOfflineMessages = false
        pendingMessages.clear()
        pendingGroupMessages.clear()
        _friendRequests.value = emptyList()
        _groupRequests.value = emptyList()

        // 重建会话作用域（取消旧会话 → 挂回 appJob）
        sessionJob.cancel()
        sessionJob = SupervisorJob(appJob)

        // 清除聊天数据
        sessionScope.launch(Dispatchers.IO) {
            chatState.clear()
            if (clearingOwner != null) LocalChatHistoryStore.clear(clearingOwner)
        }
    }

    /**
     * 仅清空内存中的消息，不删除本地存储（清空聊天记录时调用）
     */
    open fun clearMessages() {
        sessionScope.launch {
            chatState.clear()
        }
    }

    /**
     * 将 seqId 游标持久化到加密文件（重启后增量同步不中断）。
     */
    private fun saveConversationSeqIds() {
        val userId = GlobalAppState.currentUserId ?: return
        val accountId = userId.toString()
        LocalChatHistoryStore.saveConversationSeqIds(accountId, chatState.conversationSeqIds.value)
    }

    /**
     * 从加密文件恢复 seqId 游标。
     */
    private fun loadConversationSeqIds() {
        val userId = GlobalAppState.currentUserId ?: return
        val accountId = userId.toString()
        val restored = LocalChatHistoryStore.restoreConversationSeqIds(accountId)
        val ownerSession = sessionJob
        sessionScope.launch {
            if (GlobalAppState.currentUserId != userId || sessionJob !== ownerSession) return@launch
            restored.forEach { (conversation, seq) -> chatState.updateConversationSeqId(conversation, seq) }
        }
    }

    /**
     * 保存聊天历史到本地（debounce 合并：高频调用只触发一次落盘）。
     * 所有状态访问收敛到 sessionScope(Main) 上，避免 IO 线程并发访问。
     */
    private data class HistoryBatch(val owner: String, val privateRows: MutableMap<String,Message>, val groupRows: MutableMap<String,GroupMessage>)
    private var historyWriter: core.HistoryWriteQueue<HistoryBatch>? = null
    private var historyOwner: String? = null
    private fun saveChatHistoryToLocal() {
        sessionScope.launch {
            val owner = GlobalAppState.currentUserId?.toString() ?: return@launch
            if (historyOwner != owner) { historyWriter?.close(); historyWriter = null; historyOwner = owner }
            if (historyWriter == null) {
                historyWriter = core.HistoryWriteQueue(sessionScope, SAVE_HISTORY_DEBOUNCE_MS,
                    merge = { older,newer ->
                        check(older.owner==newer.owner)
                        older.apply { privateRows.putAll(newer.privateRows);groupRows.putAll(newer.groupRows) }
                    },failed={error -> logger.error(error){"Local history persistence failed"}},
                    persist={batch -> LocalChatHistoryStore.save(batch.owner,batch.privateRows.values.toList(),batch.groupRows.values.toList())})
            }
            val changed=chatState.drainHistoryChanges()
            if(changed.privateRows.isNotEmpty()||changed.groupRows.isNotEmpty())
                historyWriter!!.request(HistoryBatch(owner,changed.privateRows.associateBy { it.messageId }.toMutableMap(),changed.groupRows.associateBy { it.messageId }.toMutableMap()))
        }
    }

    suspend fun flushLocalHistory() {
        val writer = withContext(Dispatchers.Main.immediate) { saveChatHistoryToLocal(); historyWriter }
        writer?.flush()
    }

    /**
     * 从本地加载聊天历史
     */
    open fun loadLocalChatHistory() {
        sessionScope.launch(Dispatchers.IO) {
            val userId = GlobalAppState.currentUserId ?: return@launch
            val accountId = userId.toString()
            try {
                val history = LocalChatHistoryStore.restore(accountId)
                if (GlobalAppState.currentUserId != userId) return@launch
                if (history.privateMessages.isNotEmpty() || history.groupMessages.isNotEmpty()) {
                    // 加载到聊天状态
                    chatState.prependMessages(history.privateMessages.map { it.copy(deliveryState = it.deliveryState.afterRestart()) }, markAsUnread = false)
                    chatState.prependGroupMessages(history.groupMessages.map { it.copy(deliveryState = it.deliveryState.afterRestart()) }, markAsUnread = false)
                    AppLogger.d(" 从本地加载聊天历史，私聊消息: ${history.privateMessages.size}条, 群聊消息: ${history.groupMessages.size}条")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.d(" 从本地加载聊天历史失败: ${e.message}")
                logger.error(e) { "ChatViewModel error" }
            }
        }
    }
}
