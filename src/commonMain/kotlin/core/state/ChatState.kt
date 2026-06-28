package core.state

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import model.GroupMessage
import model.Message
import model.User

data class ConversationPreviewState(
    val lastIncomingMessageTime: Long = 0L,
    val unreadCount: Int = 0
)

/**
 * 聊天状态
 * 管理用户列表、消息列表等聊天相关状态
 */
class ChatState(
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
) {
    // 用户列表（包含好友和群组）
    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()

    // 私聊消息列表
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    // 群聊消息列表
    private val _groupMessages = MutableStateFlow<List<GroupMessage>>(emptyList())
    val groupMessages: StateFlow<List<GroupMessage>> = _groupMessages.asStateFlow()

    // 当前选中的聊天对象
    private val _selectedChatTarget = MutableStateFlow<User?>(null)
    val selectedChatTarget: StateFlow<User?> = _selectedChatTarget.asStateFlow()

    // 会话预览状态（最近消息时间、未读数）
    private val _conversationStates = MutableStateFlow<Map<Int, ConversationPreviewState>>(emptyMap())
    val conversationStates: StateFlow<Map<Int, ConversationPreviewState>> = _conversationStates.asStateFlow()

    // 每个会话的 seqId 跟踪（用于消息去重和顺序保证）
    private val _conversationSeqIds = MutableStateFlow<Map<String, Long>>(emptyMap())
    val conversationSeqIds: StateFlow<Map<String, Long>> = _conversationSeqIds.asStateFlow()

    // 加载更多历史消息状态
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    // 状态修改互斥锁，保证线程安全
    private val mutex = Mutex()

    private fun updateConversationStateLocked(
        conversationId: Int,
        lastIncomingMessageTime: Long? = null,
        unreadDelta: Int = 0,
        unreadCount: Int? = null
    ) {
        val currentStates = _conversationStates.value.toMutableMap()
        val previousState = currentStates[conversationId] ?: ConversationPreviewState()
        val updatedState = previousState.copy(
            lastIncomingMessageTime = maxOf(previousState.lastIncomingMessageTime, lastIncomingMessageTime ?: previousState.lastIncomingMessageTime),
            unreadCount = unreadCount ?: (previousState.unreadCount + unreadDelta).coerceAtLeast(0)
        )

        if (updatedState != previousState) {
            currentStates[conversationId] = updatedState
            _conversationStates.value = currentStates
        }
    }

    private fun privateConversationId(message: Message): Int {
        return if (message.sender) message.receiverId else message.senderId
    }

    private fun groupConversationId(message: GroupMessage): Int {
        return -message.groupId
    }

    private fun recordPrivateConversationHistoryLocked(messages: List<Message>, markAsUnread: Boolean) {
        val currentUserId = GlobalAppState.currentUserId
        val lastTimeByConversation = mutableMapOf<Int, Long>()
        val unreadByConversation = mutableMapOf<Int, Int>()

        messages.forEach { message ->
            val conversationId = privateConversationId(message)
            lastTimeByConversation[conversationId] = maxOf(lastTimeByConversation[conversationId] ?: 0L, message.timestamp)

            if (markAsUnread && !message.sender && message.senderId != currentUserId) {
                unreadByConversation[conversationId] = (unreadByConversation[conversationId] ?: 0) + 1
            }
        }

        lastTimeByConversation.forEach { (conversationId, lastTime) ->
            updateConversationStateLocked(conversationId, lastIncomingMessageTime = lastTime)
        }
        if (markAsUnread) {
            unreadByConversation.forEach { (conversationId, unreadCount) ->
                updateConversationStateLocked(conversationId, unreadDelta = unreadCount)
            }
        }
    }

    private fun recordGroupConversationHistoryLocked(messages: List<GroupMessage>, markAsUnread: Boolean) {
        val currentUserId = GlobalAppState.currentUserId
        val lastTimeByConversation = mutableMapOf<Int, Long>()
        val unreadByConversation = mutableMapOf<Int, Int>()

        messages.forEach { message ->
            val conversationId = groupConversationId(message)
            lastTimeByConversation[conversationId] = maxOf(lastTimeByConversation[conversationId] ?: 0L, message.timestamp)

            if (markAsUnread && message.senderId != currentUserId) {
                unreadByConversation[conversationId] = (unreadByConversation[conversationId] ?: 0) + 1
            }
        }

        lastTimeByConversation.forEach { (conversationId, lastTime) ->
            updateConversationStateLocked(conversationId, lastIncomingMessageTime = lastTime)
        }
        if (markAsUnread) {
            unreadByConversation.forEach { (conversationId, unreadCount) ->
                updateConversationStateLocked(conversationId, unreadDelta = unreadCount)
            }
        }
    }

    /**
     * 更新指定会话的 seqId，仅在新 seqId 大于旧 seqId 时更新。
     */
    suspend fun updateConversationSeqId(conversationId: String, seqId: Long) = mutex.withLock {
        val currentMap = _conversationSeqIds.value.toMutableMap()
        val oldSeqId = currentMap[conversationId]
        if (oldSeqId == null || seqId > oldSeqId) {
            currentMap[conversationId] = seqId
            _conversationSeqIds.value = currentMap
        }
    }

    /**
     * 获取指定会话的当前 seqId，若不存在则返回 0。
     */
    suspend fun getConversationSeqId(conversationId: String): Long = mutex.withLock {
        _conversationSeqIds.value[conversationId] ?: 0L
    }

    /**
     * 清空所有会话的 seqId 跟踪。
     */
    suspend fun clearConversationSeqIds() = mutex.withLock {
        _conversationSeqIds.value = emptyMap()
    }

    /**
     * 更新用户列表
     */
    suspend fun updateUsers(newUsers: List<User>) = mutex.withLock {
        if (_users.value == newUsers) return@withLock
        _users.value = newUsers
    }

    /**
     * 全量替换联系人列表（保留顺序）。
     * 触发全量重组，仅用于初始加载。
     */
    suspend fun replaceUsersPreservingOrder(newUsers: List<User>) = mutex.withLock {
        val currentUsers = _users.value
        if (currentUsers == newUsers) return@withLock

        val incomingById = newUsers.associateBy { it.id }
        val mergedUsers = buildList {
            currentUsers.forEach { existingUser ->
                incomingById[existingUser.id]?.let { add(it) }
            }
            newUsers.forEach { incomingUser ->
                if (currentUsers.none { it.id == incomingUser.id }) {
                    add(incomingUser)
                }
            }
        }

        if (mergedUsers != currentUsers) {
            _users.value = mergedUsers
        }
    }

    /**
     * 增量合并联系人列表。
     * 只更新有变化的字段，不重建列表。
     * Compose LazyColumn + key 可保持 item 复用。
     */
    suspend fun mergeUsers(incoming: List<User>) = mutex.withLock {
        if (incoming.isEmpty()) return@withLock
        val current = _users.value.toMutableList()
        val existingMap = current.associateBy { it.id }.toMutableMap()
        var changed = false

        for (user in incoming) {
            val existing = existingMap[user.id]
            if (existing == null) {
                existingMap[user.id] = user
                changed = true
            } else if (existing.online != user.online
                    || existing.username != user.username
                    || existing.avatarUrl != user.avatarUrl) {
                existingMap[user.id] = user
                changed = true
            }
        }

        if (changed) {
            _users.value = existingMap.values.toList()
        }
    }

    /**
     * 插入或更新单个联系人
     */
    suspend fun upsertUser(user: User) = mutex.withLock {
        val currentUsers = _users.value.toMutableList()
        val index = currentUsers.indexOfFirst { it.id == user.id }
        if (index == -1) {
            currentUsers.add(user)
        } else {
            currentUsers[index] = user
        }
        _users.value = currentUsers
    }

    /**
     * 批量插入或更新联系人
     */
    suspend fun upsertUsers(users: List<User>) = mutex.withLock {
        if (users.isEmpty()) return@withLock
        val currentUsers = _users.value.toMutableList()
        users.forEach { user ->
            val index = currentUsers.indexOfFirst { it.id == user.id }
            if (index == -1) {
                currentUsers.add(user)
            } else {
                currentUsers[index] = user
            }
        }
        _users.value = currentUsers
    }

    /**
     * 更新用户在线状态
     */
    suspend fun updateUserOnlineStatus(userId: Int, online: Boolean) = mutex.withLock {
        _users.value = _users.value.map { user ->
            if (user.id == userId) user.copy(online = online) else user
        }

        // 如果当前选中的是这个用户，也更新选中用户的状态
        if (_selectedChatTarget.value?.id == userId) {
            _selectedChatTarget.value = _selectedChatTarget.value?.copy(online = online)
        }
    }

    /**
     * 添加新的私聊消息（自动按时间戳排序插入）
     */
    suspend fun addMessage(message: Message) = mutex.withLock {
        println("[ChatState] 添加私聊消息到状态: messageId=${message.messageId}, senderId=${message.senderId}, receiverId=${message.receiverId}, content=${message.message.take(50)}, isSent=${message.isSent}, timestamp=${message.timestamp}")

        val currentMessages = _messages.value.toMutableList()

        // 查找插入位置：找到第一个时间戳比当前消息大的位置，插入到它前面
        val insertIndex = currentMessages.indexOfFirst { it.timestamp > message.timestamp }
        if (insertIndex == -1) {
            // 所有消息都比当前早，追加到末尾
            currentMessages.add(message)
        } else {
            // 插入到正确位置
            currentMessages.add(insertIndex, message)
        }

        _messages.value = currentMessages
        val conversationId = privateConversationId(message)
        val shouldIncreaseUnread = !message.sender && _selectedChatTarget.value?.id != conversationId
        updateConversationStateLocked(
            conversationId = conversationId,
            lastIncomingMessageTime = if (!message.sender) message.timestamp else null,
            unreadDelta = if (shouldIncreaseUnread) 1 else 0
        )
        println("[ChatState] 插入位置: $insertIndex, 当前消息总数: ${currentMessages.size}")
    }

    /**
     * 批量添加私聊消息（用于加载历史消息，智能插入保证顺序）
     */
    suspend fun prependMessages(
        newMessages: List<Message>,
        markAsUnread: Boolean = false
    ) = mutex.withLock {
        if (newMessages.isEmpty()) return@withLock

        val existingIds = _messages.value.map { it.messageId }.toSet()
        val messagesToAdd = newMessages.filter { it.messageId !in existingIds }
        if (messagesToAdd.isEmpty()) return@withLock

        val currentMessages = _messages.value
        if (currentMessages.isEmpty()) {
            _messages.value = messagesToAdd
            recordPrivateConversationHistoryLocked(messagesToAdd, markAsUnread)
            println("[ChatState] 消息列表为空，直接添加 ${messagesToAdd.size} 条历史消息")
            return@withLock
        }

        // 找到用户自己发的最早的消息位置（拉取离线期间用户发的消息）
        val firstUserMessageIndex = currentMessages.indexOfFirst { it.senderId == GlobalAppState.currentUserId }

        if (firstUserMessageIndex == -1) {
            // 没有用户发的消息，直接插到最前面
            _messages.value = messagesToAdd + currentMessages
            recordPrivateConversationHistoryLocked(messagesToAdd, markAsUnread)
            println("[ChatState] 无用户发送的消息，历史消息插入到最前面，总数: ${_messages.value.size}")
        } else {
            // 有用户发的消息，插到这些消息的前面，保证历史消息在旧消息和新消息之间
            val firstPart = currentMessages.subList(0, firstUserMessageIndex)
            val secondPart = currentMessages.subList(firstUserMessageIndex, currentMessages.size)
            _messages.value = firstPart + messagesToAdd + secondPart
            recordPrivateConversationHistoryLocked(messagesToAdd, markAsUnread)
            println("[ChatState] 找到用户消息位置 $firstUserMessageIndex，历史消息插入到中间，总数: ${_messages.value.size}")
        }
    }

    /**
     * 更新消息发送状态
     */
    suspend fun updateMessageSentStatus(messageId: String, isSent: Boolean) = mutex.withLock {
        println("[ChatState] 更新私聊消息发送状态: messageId=$messageId, isSent=$isSent")
        _messages.value = _messages.value.map { message ->
            if (message.messageId == messageId) {
                message.copy(isSent = isSent)
            } else {
                message
            }
        }
    }

    /**
     * 原位更新私聊消息（保持原有顺序，避免删除+新增导致的闪烁与排序抖动）
     */
    suspend fun updateMessage(updatedMessage: Message) = mutex.withLock {
        val currentMessages = _messages.value.toMutableList()
        val index = currentMessages.indexOfFirst { it.messageId == updatedMessage.messageId }
        if (index == -1) return@withLock

        currentMessages[index] = updatedMessage
        _messages.value = currentMessages
    }

    /**
     * 添加新的群聊消息（自动按时间戳排序插入）
     */
    suspend fun addGroupMessage(message: GroupMessage) = mutex.withLock {
        println("[ChatState] 添加群聊消息到状态: messageId=${message.messageId}, groupId=${message.groupId}, senderId=${message.senderId}, senderName=${message.senderName}, content=${message.text.take(50)}, isSent=${message.isSent}, timestamp=${message.timestamp}")

        val currentMessages = _groupMessages.value.toMutableList()

        // 查找插入位置：找到第一个时间戳比当前消息大的位置，插入到它前面
        val insertIndex = currentMessages.indexOfFirst { it.timestamp > message.timestamp }
        if (insertIndex == -1) {
            // 所有消息都比当前早，追加到末尾
            currentMessages.add(message)
        } else {
            // 插入到正确位置
            currentMessages.add(insertIndex, message)
        }

        _groupMessages.value = currentMessages
        val conversationId = groupConversationId(message)
        val shouldIncreaseUnread = message.senderId != GlobalAppState.currentUserId && _selectedChatTarget.value?.id != conversationId
        updateConversationStateLocked(
            conversationId = conversationId,
            lastIncomingMessageTime = if (message.senderId != GlobalAppState.currentUserId) message.timestamp else null,
            unreadDelta = if (shouldIncreaseUnread) 1 else 0
        )
        println("[ChatState] 插入位置: $insertIndex, 当前群消息总数: ${currentMessages.size}")
    }

    /**
     * 批量添加群聊消息（用于加载历史消息，智能插入保证顺序）
     */
    suspend fun prependGroupMessages(
        newMessages: List<GroupMessage>,
        markAsUnread: Boolean = false
    ) = mutex.withLock {
        if (newMessages.isEmpty()) return@withLock

        val existingIds = _groupMessages.value.map { it.messageId }.toSet()
        val messagesToAdd = newMessages.filter { it.messageId !in existingIds }
        if (messagesToAdd.isEmpty()) return@withLock

        val currentMessages = _groupMessages.value
        if (currentMessages.isEmpty()) {
            _groupMessages.value = messagesToAdd
            recordGroupConversationHistoryLocked(messagesToAdd, markAsUnread)
            println("[ChatState] 群消息列表为空，直接添加 ${messagesToAdd.size} 条历史消息")
            return@withLock
        }

        // 找到用户自己发的最早的消息位置（拉取离线期间用户发的消息）
        val firstUserMessageIndex = currentMessages.indexOfFirst { it.senderId == GlobalAppState.currentUserId }

        if (firstUserMessageIndex == -1) {
            // 没有用户发的消息，直接插到最前面
            _groupMessages.value = messagesToAdd + currentMessages
            recordGroupConversationHistoryLocked(messagesToAdd, markAsUnread)
            println("[ChatState] 无用户发送的群消息，历史消息插入到最前面，总数: ${_groupMessages.value.size}")
        } else {
            // 有用户发的消息，插到这些消息的前面，保证历史消息在旧消息和新消息之间
            val firstPart = currentMessages.subList(0, firstUserMessageIndex)
            val secondPart = currentMessages.subList(firstUserMessageIndex, currentMessages.size)
            _groupMessages.value = firstPart + messagesToAdd + secondPart
            recordGroupConversationHistoryLocked(messagesToAdd, markAsUnread)
            println("[ChatState] 找到用户群消息位置 $firstUserMessageIndex，历史消息插入到中间，总数: ${_groupMessages.value.size}")
        }
    }

    /**
     * 更新群聊消息发送状态
     */
    suspend fun updateGroupMessageSentStatus(messageId: String, isSent: Boolean) = mutex.withLock {
        println("[ChatState] 更新群聊消息发送状态: messageId=$messageId, isSent=$isSent")
        _groupMessages.value = _groupMessages.value.map { message ->
            if (message.messageId == messageId) {
                message.copy(isSent = isSent)
            } else {
                message
            }
        }
    }

    /**
     * 选择聊天对象
     */
    suspend fun selectChatTarget(user: User?) = mutex.withLock {
        _selectedChatTarget.value = user
        user?.let { targetUser ->
            val previousState = _conversationStates.value[targetUser.id]
            if (previousState != null && previousState.unreadCount != 0) {
                updateConversationStateLocked(targetUser.id, unreadCount = 0)
            }
        }
    }

    /**
     * 设置加载更多状态
     */
    suspend fun setLoadingMore(loading: Boolean) = mutex.withLock {
        _isLoadingMore.value = loading
    }

    /**
     * 删除私聊消息
     */
    suspend fun deleteMessage(messageId: String) = mutex.withLock {
        _messages.value = _messages.value.filter { it.messageId != messageId }
    }

    /**
     * 删除群聊消息
     */
    suspend fun deleteGroupMessage(messageId: String) = mutex.withLock {
        _groupMessages.value = _groupMessages.value.filter { it.messageId != messageId }
    }

    /**
     * 清空所有聊天数据（退出登录时调用）
     */
    suspend fun clear() = mutex.withLock {
        _users.value = emptyList()
        _messages.value = emptyList()
        _groupMessages.value = emptyList()
        _selectedChatTarget.value = null
        _isLoadingMore.value = false
        _conversationStates.value = emptyMap()
        _conversationSeqIds.value = emptyMap()
    }
}

// 全局单例ChatState，用于兼容旧代码，后续逐步改为依赖注入
val GlobalChatState = ChatState()
