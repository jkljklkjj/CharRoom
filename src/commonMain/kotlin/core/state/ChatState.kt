package core.state
import core.AppLogger

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import model.GroupMessage
import model.Message
import model.User
import model.ConversationKey

data class ConversationPreviewState(
    val lastIncomingMessageTime: Long = 0L,
    val unreadCount: Int = 0,
    val previewText: String = "", val previewTimestamp: Long = 0,
    val previewSender: String = "", val previewOwn: Boolean = false, val previewMessageId: String = ""
)

/**
 * 聊天状态
 * 管理用户列表、消息列表等聊天相关状态
 */
class ChatState {
    companion object {
        /** 内存保留的最大私聊/群聊消息数，超出自动淘汰最旧消息 */
        const val MAX_MESSAGES = 200
    }

    // 用户列表（包含好友和群组）
    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()

    // 全量私聊消息（兼容 UserListScreen 等预览场景，限制总条数）
    private val _messageCache = MessageLruCache<Message>(
        MAX_MESSAGES, id = { it.messageId }, timestamp = { it.timestamp }
    )
    val messages: StateFlow<List<Message>> = _messageCache.state

    // 全量群聊消息
    private val _groupMessageCache = MessageLruCache<GroupMessage>(
        MAX_MESSAGES, id = { it.messageId }, timestamp = { it.timestamp }
    )
    val groupMessages: StateFlow<List<GroupMessage>> = _groupMessageCache.state

    // 按会话分桶的私聊消息（ChatScreen 按会话 O(1) 访问，不 filter 全量）
    private val _privateMessageCaches = java.util.concurrent.ConcurrentHashMap<Int, MessageLruCache<Message>>()

    // 按群组分桶的群聊消息
    private val _groupMessageCaches = java.util.concurrent.ConcurrentHashMap<Int, MessageLruCache<GroupMessage>>()

    // 当前选中的聊天对象
    private val _selectedChatTarget = MutableStateFlow<User?>(null)
    val selectedChatTarget: StateFlow<User?> = _selectedChatTarget.asStateFlow()

    // 会话预览状态（最近消息时间、未读数）
    private val _conversationStates = MutableStateFlow<Map<ConversationKey, ConversationPreviewState>>(emptyMap())
    val conversationStates: StateFlow<Map<ConversationKey, ConversationPreviewState>> = _conversationStates.asStateFlow()

    // 每个会话的 seqId 跟踪（用于消息去重和顺序保证）
    private val _conversationSeqIds = MutableStateFlow<Map<String, Long>>(emptyMap())
    val conversationSeqIds: StateFlow<Map<String, Long>> = _conversationSeqIds.asStateFlow()

    // 加载更多历史消息状态
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val cacheBudget=ConversationCacheBudget()
    private val bucketLock=Any()
    private val previewLock=Any()
    private val dirtyLock=Any()
    private val dirtyPrivate=linkedMapOf<String,Message>()
    private val dirtyGroups=linkedMapOf<String,GroupMessage>()
    data class HistoryChanges(val privateRows:List<Message>,val groupRows:List<GroupMessage>)
    fun queueHistoryChanges(privateRows:List<Message>,groupRows:List<GroupMessage>)=synchronized(dirtyLock) {
        privateRows.forEach { dirtyPrivate[it.messageId]=it };groupRows.forEach { dirtyGroups[it.messageId]=it }
    }
    fun drainHistoryChanges()=synchronized(dirtyLock) {
        HistoryChanges(dirtyPrivate.values.toList(),dirtyGroups.values.toList()).also { dirtyPrivate.clear();dirtyGroups.clear() }
    }
    private fun findPrivate(predicate:(Message)->Boolean):Message? = _messageCache.snapshot.firstOrNull(predicate)
        ?: _privateMessageCaches.values.firstNotNullOfOrNull { it.snapshot.firstOrNull(predicate) }
    private fun findGroup(predicate:(GroupMessage)->Boolean):GroupMessage? = _groupMessageCache.snapshot.firstOrNull(predicate)
        ?: _groupMessageCaches.values.firstNotNullOfOrNull { it.snapshot.firstOrNull(predicate) }
    suspend fun applyAgentSnapshot(owner:Int,snapshot:core.AgentSnapshot) {
        val existing=messagesFor(core.ServerConfig.AGENT_ASSISTANT_ID).value.firstOrNull { it.messageId==snapshot.id }
        if(existing==null)addMessage(Message(senderId=core.ServerConfig.AGENT_ASSISTANT_ID,receiverId=owner,message=snapshot.text,
            sender=false,timestamp=System.currentTimeMillis(),isSent=true,messageId=snapshot.id))
        else if(existing.message!=snapshot.text)updateMessage(existing.copy(message=snapshot.text))
    }
    private fun privatePreview(message:Message) = preview(ConversationKey.privateChat(privateConversationId(message)),message.messageId,message.message,message.timestamp,"",message.sender)
    private fun groupPreview(message:GroupMessage) = preview(ConversationKey.group(message.groupId),message.messageId,message.text,message.timestamp,message.senderName,message.senderId==GlobalAppState.currentUserId)
    private fun preview(key:ConversationKey,id:String,text:String,time:Long,sender:String,own:Boolean)=synchronized(previewLock) {
        val previous=_conversationStates.value[key] ?: ConversationPreviewState()
        if(time<previous.previewTimestamp)return@synchronized
        val next=previous.copy(previewMessageId=id,previewText=text.replace('\n',' ').take(60),previewTimestamp=time,previewSender=sender.take(80),previewOwn=own)
        if(next!=previous)_conversationStates.value=_conversationStates.value+(key to next)
    }
    private fun putPrivate(message:Message) {
        synchronized(dirtyLock) { dirtyPrivate[message.messageId]=message }
        _messageCache.add(message);privatePreview(message)
    }
    private fun putGroup(message:GroupMessage) {
        synchronized(dirtyLock) { dirtyGroups[message.messageId]=message }
        _groupMessageCache.add(message);groupPreview(message)
    }
    private fun getOrCreatePrivateCache(userId:Int):MessageLruCache<Message> = synchronized(bucketLock) {
        val key=ConversationKey.privateChat(userId)
        val cache=_privateMessageCaches.getOrPut(userId) {
            val value=MessageLruCache<Message>(MAX_MESSAGES,{it.messageId},{it.timestamp}, { rows -> cacheBudget.changed(key,rows.size,rows.sumOf { it.message.length.toLong() }) })
            cacheBudget.register(key,{value.hasSubscribers||_selectedChatTarget.value?.conversationKey==key},{value.clear()})
            value
        }
        cacheBudget.touch(key);cache
    }
    private fun getOrCreateGroupCache(groupId:Int):MessageLruCache<GroupMessage> = synchronized(bucketLock) {
        val key=ConversationKey.group(groupId)
        val cache=_groupMessageCaches.getOrPut(groupId) {
            val value=MessageLruCache<GroupMessage>(MAX_MESSAGES,{it.messageId},{it.timestamp}, { rows -> cacheBudget.changed(key,rows.size,rows.sumOf { it.text.length.toLong() }) })
            cacheBudget.register(key,{value.hasSubscribers||_selectedChatTarget.value?.conversationKey==key},{value.clear()})
            value
        }
        cacheBudget.touch(key);cache
    }

    /**
     * 获取指定好友的私聊消息流（O(1)，首次访问创建空桶）。
     */
    fun messagesFor(userId: Int): StateFlow<List<Message>> {
        return getOrCreatePrivateCache(userId).state
    }

    fun groupMessagesFor(groupId: Int): StateFlow<List<GroupMessage>> {
        require(groupId > 0) { "Expected the server group ID" }
        return getOrCreateGroupCache(groupId).state
    }

    // 按领域拆分锁，减少互斥争用
    private val usersMutex = Mutex()
    private val messagesMutex = Mutex()
    // Eviction can clear another bucket; all message data mutations share one boundary.
    private val groupMessagesMutex = messagesMutex
    private val conversationStateMutex = Mutex()
    private val seqIdMutex = Mutex()
    private val loadingMutex = Mutex()
    private val clearMutex = Mutex()

    private fun updateConversationStateLocked(
        conversationId: ConversationKey,
        lastIncomingMessageTime: Long? = null,
        unreadDelta: Int = 0,
        unreadCount: Int? = null
    ) = synchronized(previewLock) {
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
        return message.groupId
    }

    /**
     * 统一记录消息到会话历史状态。
     * 私聊和群聊共用一个逻辑，差异通过 lambda 参数化。
     */
    private fun recordPrivateConversationHistoryLocked(messages: List<Message>, markAsUnread: Boolean) {
        val currentUserId = GlobalAppState.currentUserId
        val lastTimeByConversation = mutableMapOf<ConversationKey, Long>()
        val unreadByConversation = mutableMapOf<ConversationKey, Int>()

        messages.forEach { message ->
            val conversationId = ConversationKey.privateChat(privateConversationId(message))
            lastTimeByConversation[conversationId] = maxOf(lastTimeByConversation[conversationId] ?: 0L, message.timestamp)
            if (markAsUnread && !message.sender && message.senderId != currentUserId) {
                unreadByConversation[conversationId] = (unreadByConversation[conversationId] ?: 0) + 1
            }
        }
        applyConversationUpdates(lastTimeByConversation, unreadByConversation)
    }

    private fun recordGroupConversationHistoryLocked(messages: List<GroupMessage>, markAsUnread: Boolean) {
        val currentUserId = GlobalAppState.currentUserId
        val lastTimeByConversation = mutableMapOf<ConversationKey, Long>()
        val unreadByConversation = mutableMapOf<ConversationKey, Int>()

        messages.forEach { message ->
            val conversationId = ConversationKey.group(message.groupId)
            lastTimeByConversation[conversationId] = maxOf(lastTimeByConversation[conversationId] ?: 0L, message.timestamp)
            if (markAsUnread && message.senderId != currentUserId) {
                unreadByConversation[conversationId] = (unreadByConversation[conversationId] ?: 0) + 1
            }
        }
        applyConversationUpdates(lastTimeByConversation, unreadByConversation)
    }

    /** 两个 record* 方法的公共尾：将统计结果刷入 _conversationStates。 */
    private fun applyConversationUpdates(
        lastTimeByConversation: Map<ConversationKey, Long>,
        unreadByConversation: Map<ConversationKey, Int>
    ) {
        lastTimeByConversation.forEach { (conversationId, lastTime) ->
            updateConversationStateLocked(conversationId, lastIncomingMessageTime = lastTime)
        }
        unreadByConversation.forEach { (conversationId, unreadCount) ->
            updateConversationStateLocked(conversationId, unreadDelta = unreadCount)
        }
    }

    /**
     * 更新指定会话的 seqId，仅在新 seqId 大于旧 seqId 时更新。
     */
    suspend fun updateConversationSeqId(conversationId: String, seqId: Long) = seqIdMutex.withLock {
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
    suspend fun getConversationSeqId(conversationId: String): Long = seqIdMutex.withLock {
        _conversationSeqIds.value[conversationId] ?: 0L
    }

    /**
     * 清空所有会话的 seqId 跟踪。
     */
    suspend fun clearConversationSeqIds() = seqIdMutex.withLock {
        _conversationSeqIds.value = emptyMap()
    }

    /**
     * 更新用户列表
     */
    suspend fun updateUsers(newUsers: List<User>) = usersMutex.withLock {
        if (_users.value == newUsers) return@withLock
        _users.value = newUsers
    }

    /**
     * 全量替换联系人列表（保留顺序）。
     * 触发全量重组，仅用于初始加载。
     */
    suspend fun replaceUsersPreservingOrder(newUsers: List<User>) = usersMutex.withLock {
        val currentUsers = _users.value
        if (currentUsers == newUsers) return@withLock

        val incomingById = newUsers.associateBy { it.conversationKey }
        val mergedUsers = buildList {
            currentUsers.forEach { existingUser ->
                incomingById[existingUser.conversationKey]?.let { add(it) }
            }
            newUsers.forEach { incomingUser ->
                if (currentUsers.none { it.conversationKey == incomingUser.conversationKey }) {
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
    suspend fun mergeUsers(incoming: List<User>) = usersMutex.withLock {
        if (incoming.isEmpty()) return@withLock
        val current = _users.value.toMutableList()
        val existingMap = current.associateBy { it.conversationKey }.toMutableMap()
        var changed = false

        for (user in incoming) {
            val existing = existingMap[user.conversationKey]
            if (existing == null) {
                existingMap[user.conversationKey] = user
                changed = true
            } else if (existing.online != user.online
                    || existing.username != user.username
                    || existing.avatarUrl != user.avatarUrl) {
                existingMap[user.conversationKey] = user
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
    suspend fun upsertUser(user: User) = usersMutex.withLock {
        val currentUsers = _users.value.toMutableList()
        val index = currentUsers.indexOfFirst { it.conversationKey == user.conversationKey }
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
    suspend fun upsertUsers(users: List<User>) = usersMutex.withLock {
        if (users.isEmpty()) return@withLock
        val currentUsers = _users.value.toMutableList()
        users.forEach { user ->
            val index = currentUsers.indexOfFirst { it.conversationKey == user.conversationKey }
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
    suspend fun updateUserOnlineStatus(userId: Int, online: Boolean) = usersMutex.withLock {
        _users.value = _users.value.map { user ->
            if (!user.isGroup && user.id == userId) user.copy(online = online) else user
        }

        // 如果当前选中的是这个用户，也更新选中用户的状态
        if (_selectedChatTarget.value?.conversationKey == ConversationKey.privateChat(userId)) {
            _selectedChatTarget.value = _selectedChatTarget.value?.copy(online = online)
        }
    }

    /**
     * 添加新的私聊消息（自动按时间戳排序插入）
     */
    suspend fun addMessage(message: Message) = messagesMutex.withLock {
        AppLogger.d("[ChatState] 添加私聊消息: messageId=${message.messageId}, senderId=${message.senderId}, receiverId=${message.receiverId}, isSent=${message.isSent}, timestamp=${message.timestamp}")

        putPrivate(message)                           // 全量镜像
        val convUserId = privateConversationId(message)
        getOrCreatePrivateCache(convUserId).add(message)     // 分桶存储（O(1) 按会话访问）

        val shouldIncreaseUnread = !message.sender && _selectedChatTarget.value?.conversationKey != ConversationKey.privateChat(convUserId)
        conversationStateMutex.withLock {
            updateConversationStateLocked(
                conversationId = ConversationKey.privateChat(convUserId),
                lastIncomingMessageTime = if (!message.sender) message.timestamp else null,
                unreadDelta = if (shouldIncreaseUnread) 1 else 0
            )
        }
        AppLogger.d("[ChatState] 添加完成")
    }

    /**
     * 批量添加私聊消息（用于加载历史消息，智能插入保证顺序）
     */
    suspend fun prependMessages(
        newMessages: List<Message>,
        markAsUnread: Boolean = false,
        isCurrentAccount: () -> Boolean = { true }
    ) = messagesMutex.withLock {
        if (!isCurrentAccount()) return@withLock
        val inserted = _messageCache.prepend(newMessages)
        newMessages.forEach(::privatePreview)
        if (newMessages.isNotEmpty()) {
            val affectedUsers = newMessages.map { privateConversationId(it) }.distinct()
            affectedUsers.forEach { uid ->
                getOrCreatePrivateCache(uid).prepend(
                    newMessages.filter { privateConversationId(it) == uid }
                )
            }
            conversationStateMutex.withLock {
                recordPrivateConversationHistoryLocked(inserted, markAsUnread)
            }
        }
    }

    /**
     * 更新消息发送状态
     */
    suspend fun updateDeliveryState(id: String, status: model.MessageDeliveryState) {
        messagesMutex.withLock {
            findPrivate { it.messageId == id }?.let { old ->
                if (old.deliveryState == model.MessageDeliveryState.ACCEPTED && status != model.MessageDeliveryState.ACCEPTED) return@withLock
                val updated = old.copy(isSent = status == model.MessageDeliveryState.ACCEPTED, deliveryState = status)
                putPrivate(updated); getOrCreatePrivateCache(privateConversationId(updated)).add(updated)
            }
        }
        groupMessagesMutex.withLock {
            findGroup { it.messageId == id }?.let { old ->
                if (old.deliveryState == model.MessageDeliveryState.ACCEPTED && status != model.MessageDeliveryState.ACCEPTED) return@withLock
                val updated = old.copy(isSent = status == model.MessageDeliveryState.ACCEPTED, deliveryState = status)
                putGroup(updated); getOrCreateGroupCache(updated.groupId).add(updated)
            }
        }
    }

    suspend fun confirmMessageId(clientId: String, serverId: String, seq: Long, conversation: String) {
        messagesMutex.withLock {
            findPrivate { it.messageId == clientId && !conversation.startsWith("group:") &&
                conversation.removePrefix("user:") == listOf(it.senderId.toString(), it.receiverId.toString()).sorted().joinToString(":") }?.let { old ->
                val updated = old.copy(messageId = serverId, seqId = seq, conversationId = conversation, isSent = true, deliveryState = model.MessageDeliveryState.ACCEPTED)
                _messageCache.remove(clientId)
                getOrCreatePrivateCache(privateConversationId(old)).remove(clientId)
                putPrivate(updated)
                getOrCreatePrivateCache(privateConversationId(updated)).add(updated)
            }
        }
        groupMessagesMutex.withLock {
            findGroup { it.messageId == clientId && conversation == "group:${it.groupId}" }?.let { old ->
                val updated = old.copy(messageId = serverId, seqId = seq, conversationId = conversation, isSent = true, deliveryState = model.MessageDeliveryState.ACCEPTED)
                _groupMessageCache.remove(clientId)
                getOrCreateGroupCache(old.groupId).remove(clientId)
                putGroup(updated)
                getOrCreateGroupCache(updated.groupId).add(updated)
            }
        }
    }

    suspend fun updateMessageSentStatus(messageId: String, isSent: Boolean) {
        messagesMutex.withLock {
            AppLogger.d("[ChatState] 更新私聊消息发送状态: messageId=$messageId, isSent=$isSent")
            val msg = findPrivate { it.messageId == messageId } ?: return@withLock
            if (!isSent && msg.deliveryState == model.MessageDeliveryState.ACCEPTED) return@withLock
            val updated = msg.copy(isSent = isSent, deliveryState = if (isSent) model.MessageDeliveryState.ACCEPTED else model.MessageDeliveryState.FAILED)
            putPrivate(updated)
            getOrCreatePrivateCache(privateConversationId(updated)).add(updated)
        }
    }

    /**
     * 原位更新私聊消息。
     */
    suspend fun updateMessage(updatedMessage: Message) = messagesMutex.withLock {
        putPrivate(updatedMessage)
        getOrCreatePrivateCache(privateConversationId(updatedMessage)).add(updatedMessage)
    }

    /**
     * 添加新的群聊消息（自动按时间戳排序插入）
     */
    suspend fun addGroupMessage(message: GroupMessage) = groupMessagesMutex.withLock {
        AppLogger.d("[ChatState] 添加群聊消息: messageId=${message.messageId}, groupId=${message.groupId}, senderId=${message.senderId}, senderName=${message.senderName}, isSent=${message.isSent}, timestamp=${message.timestamp}")

        val gid = groupConversationId(message)
        putGroup(message)                       // 全量镜像
        getOrCreateGroupCache(gid).add(message)                // 分桶

        val shouldIncreaseUnread = message.senderId != GlobalAppState.currentUserId && _selectedChatTarget.value?.conversationKey != ConversationKey.group(gid)
        conversationStateMutex.withLock {
            updateConversationStateLocked(
                conversationId = ConversationKey.group(gid),
                lastIncomingMessageTime = if (message.senderId != GlobalAppState.currentUserId) message.timestamp else null,
                unreadDelta = if (shouldIncreaseUnread) 1 else 0
            )
        }
        AppLogger.d("[ChatState] 添加群聊消息完成, 当前群消息总数: ${_groupMessageCache.size}")
    }

    /**
     * 批量添加群聊消息（用于加载历史消息，智能插入保证顺序）
     */
    suspend fun prependGroupMessages(
        newMessages: List<GroupMessage>,
        markAsUnread: Boolean = false,
        isCurrentAccount: () -> Boolean = { true }
    ) = groupMessagesMutex.withLock {
        if (!isCurrentAccount()) return@withLock
        val inserted = _groupMessageCache.prepend(newMessages)
        newMessages.forEach(::groupPreview)
        if (newMessages.isNotEmpty()) {
            val affectedGroups = newMessages.map { groupConversationId(it) }.distinct()
            affectedGroups.forEach { gid ->
                getOrCreateGroupCache(gid).prepend(
                    newMessages.filter { groupConversationId(it) == gid }
                )
            }
            conversationStateMutex.withLock {
                recordGroupConversationHistoryLocked(inserted, markAsUnread)
            }
        }
    }

    /**
     * 更新群聊消息发送状态
     */
    suspend fun updateGroupMessageSentStatus(messageId: String, isSent: Boolean) {
        groupMessagesMutex.withLock {
            AppLogger.d("[ChatState] 更新群聊消息发送状态: messageId=$messageId, isSent=$isSent")
            val msg = findGroup { it.messageId == messageId } ?: return@withLock
            if (!isSent && msg.deliveryState == model.MessageDeliveryState.ACCEPTED) return@withLock
            val updated = msg.copy(isSent = isSent, deliveryState = if (isSent) model.MessageDeliveryState.ACCEPTED else model.MessageDeliveryState.FAILED)
            putGroup(updated)
            getOrCreateGroupCache(updated.groupId).add(updated)
        }
    }

    /**
     * 选择聊天对象
     */
    suspend fun selectChatTarget(user: User?) = conversationStateMutex.withLock {
        _selectedChatTarget.value = user
        user?.let { targetUser ->
            val previousState = _conversationStates.value[targetUser.conversationKey]
            if (previousState != null && previousState.unreadCount != 0) {
                updateConversationStateLocked(targetUser.conversationKey, unreadCount = 0)
            }
        }
    }

    /**
     * 自适应缓存容量：应用切前后台时调用，立即触发 LRU 淘汰。
     * @param foreground true=保持最大容量(200)，false=缩减(50)释放内存
     */
    suspend fun setAdaptiveCache(foreground: Boolean) {
        val target = if (foreground) MAX_MESSAGES else MAX_MESSAGES / 4
        messagesMutex.withLock {
            _messageCache.maxSize = target
            _privateMessageCaches.values.forEach { it.maxSize = target }
        }
        groupMessagesMutex.withLock {
            _groupMessageCache.maxSize = target
            _groupMessageCaches.values.forEach { it.maxSize = target }
        }
    }

    /**
     * 标记私聊消息为最近访问（提升 LRU 位置，不改变时间排序）。
     */
    suspend fun touchMessage(messageId: String) = messagesMutex.withLock {
        _messageCache.touch(messageId)
    }

    /**
     * 标记群聊消息为最近访问。
     */
    suspend fun touchGroupMessage(messageId: String) = groupMessagesMutex.withLock {
        _groupMessageCache.touch(messageId)
    }

    /**
     * 设置加载更多状态
     */
    suspend fun setLoadingMore(loading: Boolean) = loadingMutex.withLock {
        _isLoadingMore.value = loading
    }

    /**
     * 删除私聊消息
     */
    suspend fun deleteMessage(messageId: String) = messagesMutex.withLock {
        val msg = findPrivate { it.messageId == messageId }
        _messageCache.remove(messageId)
        synchronized(dirtyLock) { dirtyPrivate.remove(messageId) }
        if (msg != null) getOrCreatePrivateCache(privateConversationId(msg)).remove(messageId)
        if(msg!=null) {
            val key=ConversationKey.privateChat(privateConversationId(msg))
            synchronized(previewLock) {
                val old=_conversationStates.value[key]
                if(old?.previewMessageId==messageId) {
                    _conversationStates.value=_conversationStates.value+(key to old.copy(previewText="",previewTimestamp=0,previewMessageId=""))
                    getOrCreatePrivateCache(key.id).snapshot.lastOrNull()?.let(::privatePreview)
                }
            }
        }
    }

    /**
     * 删除群聊消息
     */
    suspend fun deleteGroupMessage(messageId: String) = groupMessagesMutex.withLock {
        val msg = findGroup { it.messageId == messageId }
        _groupMessageCache.remove(messageId)
        synchronized(dirtyLock) { dirtyGroups.remove(messageId) }
        if (msg != null) getOrCreateGroupCache(msg.groupId).remove(messageId)
        if(msg!=null) {
            val key=ConversationKey.group(msg.groupId)
            synchronized(previewLock) {
                val old=_conversationStates.value[key]
                if(old?.previewMessageId==messageId) {
                    _conversationStates.value=_conversationStates.value+(key to old.copy(previewText="",previewTimestamp=0,previewMessageId=""))
                    getOrCreateGroupCache(key.id).snapshot.lastOrNull()?.let(::groupPreview)
                }
            }
        }
    }

    /**
     * 清空所有聊天数据（退出登录时调用）
     */
    suspend fun clear() = clearMutex.withLock { messagesMutex.withLock {
        _users.value = emptyList()
        _messageCache.clear()
        _groupMessageCache.clear()
        _privateMessageCaches.values.forEach { it.clear() }
        _groupMessageCaches.values.forEach { it.clear() }
        _privateMessageCaches.clear()
        cacheBudget.clear();synchronized(dirtyLock) { dirtyPrivate.clear();dirtyGroups.clear() }
        _groupMessageCaches.clear()
        _selectedChatTarget.value = null
        _isLoadingMore.value = false
        _conversationStates.value = emptyMap()
        _conversationSeqIds.value = emptyMap()
    }
    }
}

// 全局单例ChatState，用于兼容旧代码，后续逐步改为依赖注入
val GlobalChatState = ChatState()
