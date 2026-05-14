package presentation.viewmodel

import core.Chat
import core.GlobalApiService
import core.LocalChatHistoryStore
import core.MsgType
import core.buildChatPayload
import core.buildGroupChatPayload
import core.state.ChatState
import core.state.GlobalAppState
import core.state.GlobalChatState
import data.repository.ChatRepository
import data.repository.GlobalChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import model.Group
import model.GroupMessage
import model.Message
import model.MessageIdGenerator
import model.MessageType
import model.User

private const val AGENT_ASSISTANT_ID = 900000001

/**
 * 聊天ViewModel
 * 处理聊天相关的UI逻辑和状态
 */
open class ChatViewModel(
    protected val chatRepository: ChatRepository = GlobalChatRepository,
    protected val chatState: ChatState = GlobalChatState,
    protected val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
) {
    // 用户列表状态Flow
    val usersFlow: StateFlow<List<User>> = chatState.users

    // 私聊消息状态Flow
    val messagesFlow: StateFlow<List<Message>> = chatState.messages

    // 群聊消息状态Flow
    val groupMessagesFlow: StateFlow<List<GroupMessage>> = chatState.groupMessages

    // 当前选中的用户/群组Flow
    val selectedChatTargetFlow: StateFlow<User?> = chatState.selectedChatTarget

    // 加载更多历史消息的状态Flow
    val isLoadingMoreFlow: StateFlow<Boolean> = chatState.isLoadingMore

    var selectedUser: User?
        get() = chatState.selectedChatTarget.value
        set(value) {
            println("[ChatViewModel DEBUG] Setting selectedUser: ${value?.id} - ${value?.username}")
            coroutineScope.launch {
                chatState.selectChatTarget(value)
            }
        }

    var isLoadingMore: Boolean
        get() = chatState.isLoadingMore.value
        set(value) {
            coroutineScope.launch {
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
        coroutineScope.launch {
            chatState.updateUserOnlineStatus(userId, online)
        }
    }

    /**
     * 前置添加私聊消息（用于加载历史消息）
     */
    fun prependMessages(newMessages: List<Message>) {
        coroutineScope.launch {
            chatState.prependMessages(newMessages)
            // 异步保存到本地存储
            launch(Dispatchers.IO) {
                saveChatHistoryToLocal()
            }
        }
    }

    /**
     * 前置添加群聊消息（用于加载历史消息）
     */
    fun prependGroupMessages(newMessages: List<GroupMessage>) {
        coroutineScope.launch {
            chatState.prependGroupMessages(newMessages)
            // 异步保存到本地存储
            launch(Dispatchers.IO) {
                saveChatHistoryToLocal()
            }
        }
    }

    /**
     * 添加新的私聊消息
     */
    fun addMessage(message: Message) {
        coroutineScope.launch {
            // 离线消息拉取中，先缓存到临时队列，避免顺序混乱
            if (isFetchingOfflineMessages && !message.sender) {
                pendingMessages.add(message)
                println("[ChatViewModel] 离线消息拉取中，私聊消息已缓存: messageId=${message.messageId}")
                return@launch
            }

            chatState.addMessage(message)
            // 异步保存到本地存储
            launch(Dispatchers.IO) {
                saveChatHistoryToLocal()
            }
        }
    }

    /**
     * 添加新的群聊消息
     */
    fun addGroupMessage(message: GroupMessage) {
        coroutineScope.launch {
            chatState.addGroupMessage(message)
            // 异步保存到本地存储
            launch(Dispatchers.IO) {
                saveChatHistoryToLocal()
            }
        }
    }

    /**
     * 更新私聊消息发送状态
     */
    fun updateMessageSentStatus(messageId: String, isSent: Boolean) {
        coroutineScope.launch {
            chatState.updateMessageSentStatus(messageId, isSent)
            // 异步保存到本地存储
            launch(Dispatchers.IO) {
                saveChatHistoryToLocal()
            }
        }
    }

    /**
     * 原位更新私聊消息内容（用于流式输出）
     */
    fun updateMessage(updatedMessage: Message) {
        coroutineScope.launch {
            chatState.updateMessage(updatedMessage)
            // 异步保存到本地存储
            launch(Dispatchers.IO) {
                saveChatHistoryToLocal()
            }
        }
    }

    /**
     * 聊天助手流式消息专用入口：
     * 1) 首块创建消息
     * 2) 后续块原位更新，不走删除+新增
     */
    fun upsertAgentStreamMessage(messageId: String, fullContent: String) {
        coroutineScope.launch {
            val currentUserId = GlobalAppState.currentUserId ?: return@launch
            val existingMessage = chatState.messages.value.find {
                it.messageId == messageId && it.senderId == AGENT_ASSISTANT_ID
            }

            if (existingMessage == null) {
                val agentMessage = Message(
                    senderId = AGENT_ASSISTANT_ID,
                    message = fullContent,
                    sender = false,
                    receiverId = currentUserId,
                    timestamp = System.currentTimeMillis(),
                    isSent = true,
                    messageId = messageId
                )
                chatState.addMessage(agentMessage)
            } else if (existingMessage.message != fullContent) {
                chatState.updateMessage(existingMessage.copy(message = fullContent))
            } else {
                // 内容未变化时跳过，避免无效重组
                return@launch
            }

            launch(Dispatchers.IO) {
                saveChatHistoryToLocal()
            }
        }
    }

    /**
     * 更新群聊消息发送状态
     */
    fun updateGroupMessageSentStatus(messageId: String, isSent: Boolean) {
        coroutineScope.launch {
            chatState.updateGroupMessageSentStatus(messageId, isSent)
            // 异步保存到本地存储
            launch(Dispatchers.IO) {
                saveChatHistoryToLocal()
            }
        }
    }

    // 好友/群请求状态Flow
    private val _friendRequests = MutableStateFlow<List<User>>(emptyList())
    val friendRequests: StateFlow<List<User>> = _friendRequests.asStateFlow()

    private val _groupRequests = MutableStateFlow<List<User>>(emptyList())
    val groupRequests: StateFlow<List<User>> = _groupRequests.asStateFlow()

    /**
     * 加载好友和群组列表
     */
    fun loadContacts() {
        coroutineScope.launch {
            val contacts = chatRepository.fetchAllContacts()
            chatState.updateUsers(contacts)
        }
    }

    /**
     * 拉取好友和群聊请求
     */
    fun fetchRequests() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                println("[ChatViewModel] 开始拉取好友和群聊请求")
                val friendRequests = chatRepository.fetchFriendRequests()
                val groupRequests = chatRepository.fetchGroupRequests()

                _friendRequests.value = friendRequests
                _groupRequests.value = groupRequests

                println("[ChatViewModel] 拉取请求完成: 好友请求 ${friendRequests.size} 条, 群聊请求 ${groupRequests.size} 条")
            } catch (e: Exception) {
                println("[ChatViewModel] 拉取请求失败: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * 获取当前用户信息
     */
    fun getCurrentUserProfile(onResult: (User?) -> Unit) {
        coroutineScope.launch {
            val user = chatRepository.getCurrentUserProfile()
            onResult(user)
        }
    }

    /**
     * 添加好友
     */
    fun addFriend(account: String, onResult: (Boolean) -> Unit) {
        coroutineScope.launch {
            val success = chatRepository.addFriend(account)
            if (success) {
                // 重新加载联系人列表
                loadContacts()
            }
            onResult(success)
        }
    }

    /**
     * 加入群组
     */
    fun addGroup(groupId: String, onResult: (Boolean) -> Unit) {
        coroutineScope.launch {
            val success = chatRepository.addGroup(groupId)
            if (success) {
                // 重新加载联系人列表
                loadContacts()
            }
            onResult(success)
        }
    }

    /**
     * 分页拉取离线消息（每次拉取50条，自动处理顺序问题）
     */
    open suspend fun fetchOfflineMessages(page: Int = 0, pageSize: Int = 50): Boolean {
        if (isFetchingOfflineMessages) {
            println("[ChatViewModel] 已有离线消息拉取任务在进行中，跳过重复请求")
            return false
        }

        isFetchingOfflineMessages = true
        isLoadingMore = page > 0 // 第一页不显示加载状态

        return try {
            println("[ChatViewModel] 开始拉取离线消息，页码: $page, 每页大小: $pageSize")

            // 分页拉取离线消息（需要API支持分页参数，这里假设接口已经支持）
            val messages = chatRepository.getOfflineMessagesPage(page, pageSize)

            if (messages.isEmpty()) {
                println("[ChatViewModel] 离线消息拉取完成，没有更多消息")
                return false
            }

            // 分页拉取的离线消息本身就是按时间从旧到新排序的，直接批量前置插入
            chatState.prependMessages(messages.sortedBy { it.timestamp })
            println("[ChatViewModel] 成功拉取离线消息 ${messages.size} 条，页码: $page，批量插入完成")

            // 递归拉取下一页
            val hasMore = fetchOfflineMessages(page + 1, pageSize)

            // 所有页面拉取完成后，处理缓存的临时消息
            if (!hasMore) {
                println("[ChatViewModel] 所有离线消息拉取完成，开始处理缓存的临时消息，共 ${pendingMessages.size} 条私聊, ${pendingGroupMessages.size} 条群聊")
                pendingMessages.sortedBy { it.timestamp }.forEach { chatState.addMessage(it) }
                pendingGroupMessages.sortedBy { it.timestamp }.forEach { chatState.addGroupMessage(it) }
                pendingMessages.clear()
                pendingGroupMessages.clear()
            }

            hasMore
        } catch (e: Exception) {
            println("[ChatViewModel] 拉取离线消息失败: ${e.message}")
            e.printStackTrace()
            false
        } finally {
            isFetchingOfflineMessages = false
            isLoadingMore = false
            // 保存到本地存储
            coroutineScope.launch(Dispatchers.IO) {
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
        coroutineScope.launch {
            val success = chatRepository.updateUserProfile(username, phone, signature, password)
            onResult(success)
        }
    }

    /**
     * 更新用户列表
     */
    fun updateUsers(users: List<User>) {
        coroutineScope.launch {
            chatState.updateUsers(users)
        }
    }

    /**
     * 删除私聊消息
     */
    fun deleteMessage(messageId: String) {
        coroutineScope.launch {
            chatState.deleteMessage(messageId)
            // 异步保存到本地存储
            launch(Dispatchers.IO) {
                saveChatHistoryToLocal()
            }
        }
    }

    /**
     * 删除群聊消息
     */
    fun deleteGroupMessage(messageId: String) {
        coroutineScope.launch {
            chatState.deleteGroupMessage(messageId)
            // 异步保存到本地存储
            launch(Dispatchers.IO) {
                saveChatHistoryToLocal()
            }
        }
    }

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
        val currentUserId = GlobalAppState.currentUserId
        println("[ChatViewModel] 准备发送私聊消息，currentUserId: $currentUserId, 接收者: ${user.id}")

        if (currentUserId == null) {
            println("[ChatViewModel] 错误：发送消息失败，当前用户ID为空")
            coroutineScope.launch {
                onDone() // 必须调用onDone恢复UI状态
            }
            return
        }

        if (!Chat.isConnected()) {
            println("[ChatViewModel] 错误：WebSocket未连接，消息发送失败")
            coroutineScope.launch {
                onDone()
            }
            return
        }

        try {
            val timestamp = System.currentTimeMillis()
            val messageId = MessageIdGenerator.generateMessageId(currentUserId, messageText + fileUrl.orEmpty(), timestamp)

            // 创建消息对象
            val message = Message(
                senderId = currentUserId,
                message = messageText,
                sender = true,
                receiverId = user.id,
                timestamp = timestamp,
                isSent = false, // 初始状态为未发送
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
            addMessage(message)
            println("[ChatViewModel] 本地消息已添加，messageId: $messageId")

            // 构建WebSocket消息
            val payload = buildChatPayload(
                targetClientId = user.id.toString(),
                content = messageText,
                userId = currentUserId,
                timestamp = timestamp,
                replyToMessageId = replyToMessageId,
                replyToContent = replyToContent,
                replyToSender = replyToSender,
                messageType = messageType.ordinal,
                fileUrl = fileUrl,
                fileName = fileName,
                fileSize = fileSize
            )

            // 通过WebSocket发送消息
            Chat.send(
                payload = payload,
                type = MsgType.CHAT,
                targetClientId = user.id.toString(),
                expectedResponses = 1
            ) { success, responses ->
                println("[ChatViewModel] WebSocket消息发送结果: $success, messageId: $messageId")
                // 更新消息发送状态
                coroutineScope.launch {
                    updateMessageSentStatus(messageId, success)
                    println("[ChatViewModel] 消息状态已更新，success: $success, messageId: $messageId")
                    onDone()
                }
            }
        } catch (e: Exception) {
            println("[ChatViewModel] 发送消息流程异常: ${e.message}")
            e.printStackTrace()
            coroutineScope.launch {
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
        // 获取当前用户信息
        val currentUserId = GlobalAppState.currentUserId
        println("[ChatViewModel] 准备发送群聊消息，currentUserId: $currentUserId, 群组: ${group.id}")

        if (currentUserId == null) {
            println("[ChatViewModel] 错误：发送群消息失败，当前用户ID为空")
            coroutineScope.launch {
                onDone() // 必须调用onDone恢复UI状态
            }
            return
        }

        if (!Chat.isConnected()) {
            println("[ChatViewModel] 错误：WebSocket未连接，群消息发送失败")
            coroutineScope.launch {
                onDone()
            }
            return
        }

        try {
            val currentUser = chatState.users.value.find { it.id == currentUserId }
            if (currentUser == null) {
                println("[ChatViewModel] 错误：发送群消息失败，找不到当前用户信息")
                coroutineScope.launch {
                    onDone()
                }
                return
            }

            val timestamp = System.currentTimeMillis()
            val messageId = MessageIdGenerator.generateGroupMessageId(group.id, currentUserId, messageText + fileUrl.orEmpty(), timestamp)

            // 创建群聊消息对象
            val groupMessage = GroupMessage(
                groupId = group.id,
                senderName = currentUser.username,
                text = messageText,
                senderId = currentUserId,
                timestamp = timestamp,
                isSent = false, // 初始状态为未发送
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
            println("[ChatViewModel] 本地群消息已添加，messageId: $messageId")

            // 构建WebSocket群聊消息
            val payload = buildGroupChatPayload(
                targetClientId = group.id.toString(),
                content = messageText,
                userId = currentUserId,
                replyToMessageId = replyToMessageId,
                replyToContent = replyToContent,
                replyToSender = replyToSender,
                messageType = messageType.ordinal,
                fileUrl = fileUrl,
                fileName = fileName,
                fileSize = fileSize
            )

            // 通过WebSocket发送群聊消息
            Chat.send(
                payload = payload,
                type = MsgType.GROUP_CHAT,
                targetClientId = group.id.toString(),
                expectedResponses = 1
            ) { success, responses ->
                println("[ChatViewModel] WebSocket群消息发送结果: $success, messageId: $messageId")
                // 更新消息发送状态
                coroutineScope.launch {
                    updateGroupMessageSentStatus(messageId, success)
                    println("[ChatViewModel] 群消息状态已更新，success: $success, messageId: $messageId")
                    onDone()
                }
            }
        } catch (e: Exception) {
            println("[ChatViewModel] 发送群消息流程异常: ${e.message}")
            e.printStackTrace()
            coroutineScope.launch {
                onDone() // 发生任何异常都要恢复UI状态
            }
        }
    }

    /**
     * 清空所有聊天数据
     */
    open fun clear() {
        coroutineScope.launch {
            chatState.clear()
            // 清除本地存储
            launch(Dispatchers.IO) {
                val userId = GlobalAppState.currentUserId ?: return@launch
                val accountId = userId.toString()
                LocalChatHistoryStore.clear(accountId)
            }
        }
    }

    /**
     * 仅清空内存中的消息，不删除本地存储（清空聊天记录时调用）
     */
    open fun clearMessages() {
        coroutineScope.launch {
            chatState.clear()
        }
    }

    /**
     * 保存聊天历史到本地存储
     */
    private suspend fun saveChatHistoryToLocal() {
        val userId = GlobalAppState.currentUserId ?: return
        val accountId = userId.toString()
        try {
            val privateMessages = chatState.messages.value
            val groupMessages = chatState.groupMessages.value
            LocalChatHistoryStore.save(accountId, privateMessages, groupMessages)
            println("[ChatViewModel] 聊天历史已保存到本地，私聊消息: ${privateMessages.size}条, 群聊消息: ${groupMessages.size}条")
        } catch (e: Exception) {
            println("[ChatViewModel] 保存聊天历史到本地失败: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 从本地加载聊天历史
     */
    open fun loadLocalChatHistory() {
        coroutineScope.launch(Dispatchers.IO) {
            val userId = GlobalAppState.currentUserId ?: return@launch
            val accountId = userId.toString()
            try {
                val history = LocalChatHistoryStore.restore(accountId)
                if (history.privateMessages.isNotEmpty() || history.groupMessages.isNotEmpty()) {
                    // 加载到聊天状态
                    chatState.prependMessages(history.privateMessages)
                    chatState.prependGroupMessages(history.groupMessages)
                    println("[ChatViewModel] 从本地加载聊天历史，私聊消息: ${history.privateMessages.size}条, 群聊消息: ${history.groupMessages.size}条")
                }
            } catch (e: Exception) {
                println("[ChatViewModel] 从本地加载聊天历史失败: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}

// 全局单例，兼容旧代码
val GlobalChatViewModel = ChatViewModel()
