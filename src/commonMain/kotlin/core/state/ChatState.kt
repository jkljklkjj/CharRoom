package core.state

import core.AppLogger
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import model.ConversationKey
import model.GroupMessage
import model.Message
import model.User

data class ConversationPreviewState(
    val lastIncomingMessageTime: Long = 0L,
    val unreadCount: Int = 0,
    val previewText: String = "",
    val previewTimestamp: Long = 0,
    val previewSender: String = "",
    val previewOwn: Boolean = false,
    val previewMessageId: String = "",
)

/** 聊天状态 管理用户列表、消息列表等聊天相关状态 */
class ChatState
private constructor(
    private val contacts: ChatContacts,
    private val cursors: ChatCursors,
    private val historyChanges: HistoryChangeBuffer,
    private val previews: ConversationPreviews,
) : ChatContactState by contacts, ChatCursorState by cursors, ChatHistoryChanges by historyChanges {
    constructor() :
        this(ChatContacts(), ChatCursors(), HistoryChangeBuffer(), ConversationPreviews())

    private val buckets =
        MessageBuckets(previews, historyChanges) {
            contacts.selectedTargetState.value?.conversationKey
        }
    val messages
        get() = buckets.messages

    val groupMessages
        get() = buckets.groupMessages

    val conversationStates
        get() = previews.conversationStates

    companion object {
        /** 内存保留的最大私聊/群聊消息数，超出自动淘汰最旧消息 */
        const val MAX_MESSAGES = 200
    }

    // 加载更多历史消息状态
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    data class HistoryChanges(val privateRows: List<Message>, val groupRows: List<GroupMessage>)

    suspend fun applyAgentSnapshot(owner: Int, snapshot: core.AgentSnapshot) {
        val existing =
            messagesFor(core.ServerConfig.AGENT_ASSISTANT_ID).value.firstOrNull {
                it.messageId == snapshot.id
            }
        if (existing == null)
            addMessage(
                Message(
                    senderId = core.ServerConfig.AGENT_ASSISTANT_ID,
                    receiverId = owner,
                    message = snapshot.text,
                    sender = false,
                    timestamp = System.currentTimeMillis(),
                    isSent = true,
                    messageId = snapshot.id,
                )
            )
        else if (existing.message != snapshot.text)
            updateMessage(existing.copy(message = snapshot.text))
    }

    /** 获取指定好友的私聊消息流（O(1)，首次访问创建空桶）。 */
    fun messagesFor(userId: Int): StateFlow<List<Message>> =
        buckets.getOrCreatePrivateCache(userId).state

    fun groupMessagesFor(groupId: Int): StateFlow<List<GroupMessage>> {
        require(groupId > 0) { "Expected the server group ID" }
        return buckets.getOrCreateGroupCache(groupId).state
    }

    private val messagesMutex = Mutex()
    // Eviction can clear another bucket; all message data mutations share one boundary.
    private val groupMessagesMutex = messagesMutex
    private val conversationStateMutex = Mutex()

    private val loadingMutex = Mutex()
    private val clearMutex = Mutex()

    /** 添加新的私聊消息（自动按时间戳排序插入） */
    suspend fun addMessage(message: Message) = messagesMutex.withLock {
        AppLogger.d(
            "[ChatState] 添加私聊消息: messageId=${message.messageId}, senderId=${message.senderId}, " +
                "receiverId=${message.receiverId}, isSent=${message.isSent}, timestamp=${message.timestamp}"
        )

        buckets.putPrivate(message) // 全量镜像
        val convUserId = privateConversationId(message)
        buckets.getOrCreatePrivateCache(convUserId).add(message) // 分桶存储（O(1) 按会话访问）

        val shouldIncreaseUnread =
            !message.sender &&
                contacts.selectedTargetState.value?.conversationKey !=
                    ConversationKey.privateChat(convUserId)
        conversationStateMutex.withLock {
            previews.updateConversationStateLocked(
                conversationId = ConversationKey.privateChat(convUserId),
                lastIncomingMessageTime = if (!message.sender) message.timestamp else null,
                unreadDelta = if (shouldIncreaseUnread) 1 else 0,
            )
        }
        AppLogger.d("[ChatState] 添加完成")
    }

    /** 批量添加私聊消息（用于加载历史消息，智能插入保证顺序） */
    suspend fun prependMessages(
        newMessages: List<Message>,
        markAsUnread: Boolean = false,
        isCurrentAccount: () -> Boolean = { true },
    ) = messagesMutex.withLock {
        if (!isCurrentAccount()) return@withLock
        val inserted = buckets.messageCache.prepend(newMessages)
        newMessages.forEach(previews::privatePreview)
        if (newMessages.isNotEmpty()) {
            val affectedUsers = newMessages.map { privateConversationId(it) }.distinct()
            affectedUsers.forEach { uid ->
                buckets
                    .getOrCreatePrivateCache(uid)
                    .prepend(newMessages.filter { privateConversationId(it) == uid })
            }
            conversationStateMutex.withLock {
                previews.recordPrivateConversationHistoryLocked(inserted, markAsUnread)
            }
        }
    }

    /** 更新消息发送状态 */
    suspend fun updateDeliveryState(id: String, status: model.MessageDeliveryState) {
        messagesMutex.withLock {
            buckets
                .findPrivate { it.messageId == id }
                ?.let { old ->
                    if (
                        old.deliveryState == model.MessageDeliveryState.ACCEPTED &&
                            status != model.MessageDeliveryState.ACCEPTED
                    )
                        return@withLock
                    val updated =
                        old.copy(
                            isSent = status == model.MessageDeliveryState.ACCEPTED,
                            deliveryState = status,
                        )
                    buckets.putPrivate(updated)
                    buckets.getOrCreatePrivateCache(privateConversationId(updated)).add(updated)
                }
        }
        groupMessagesMutex.withLock {
            buckets
                .findGroup { it.messageId == id }
                ?.let { old ->
                    if (
                        old.deliveryState == model.MessageDeliveryState.ACCEPTED &&
                            status != model.MessageDeliveryState.ACCEPTED
                    )
                        return@withLock
                    val updated =
                        old.copy(
                            isSent = status == model.MessageDeliveryState.ACCEPTED,
                            deliveryState = status,
                        )
                    buckets.putGroup(updated)
                    buckets.getOrCreateGroupCache(updated.groupId).add(updated)
                }
        }
    }

    suspend fun confirmMessageId(
        clientId: String,
        serverId: String,
        seq: Long,
        conversation: String,
    ) {
        messagesMutex.withLock {
            buckets
                .findPrivate {
                    it.messageId == clientId &&
                        !conversation.startsWith("group:") &&
                        conversation.removePrefix("user:") ==
                            listOf(it.senderId.toString(), it.receiverId.toString())
                                .sorted()
                                .joinToString(":")
                }
                ?.let { old ->
                    val updated =
                        old.copy(
                            messageId = serverId,
                            seqId = seq,
                            conversationId = conversation,
                            isSent = true,
                            deliveryState = model.MessageDeliveryState.ACCEPTED,
                        )
                    buckets.messageCache.remove(clientId)
                    buckets.getOrCreatePrivateCache(privateConversationId(old)).remove(clientId)
                    buckets.putPrivate(updated)
                    buckets.getOrCreatePrivateCache(privateConversationId(updated)).add(updated)
                }
        }
        groupMessagesMutex.withLock {
            buckets
                .findGroup { it.messageId == clientId && conversation == "group:${it.groupId}" }
                ?.let { old ->
                    val updated =
                        old.copy(
                            messageId = serverId,
                            seqId = seq,
                            conversationId = conversation,
                            isSent = true,
                            deliveryState = model.MessageDeliveryState.ACCEPTED,
                        )
                    buckets.groupMessageCache.remove(clientId)
                    buckets.getOrCreateGroupCache(old.groupId).remove(clientId)
                    buckets.putGroup(updated)
                    buckets.getOrCreateGroupCache(updated.groupId).add(updated)
                }
        }
    }

    suspend fun updateMessageSentStatus(messageId: String, isSent: Boolean) {
        messagesMutex.withLock {
            AppLogger.d("[ChatState] 更新私聊消息发送状态: messageId=$messageId, isSent=$isSent")
            val msg = buckets.findPrivate { it.messageId == messageId } ?: return@withLock
            if (!isSent && msg.deliveryState == model.MessageDeliveryState.ACCEPTED) return@withLock
            val updated =
                msg.copy(
                    isSent = isSent,
                    deliveryState =
                        if (isSent) model.MessageDeliveryState.ACCEPTED
                        else model.MessageDeliveryState.FAILED,
                )
            buckets.putPrivate(updated)
            buckets.getOrCreatePrivateCache(privateConversationId(updated)).add(updated)
        }
    }

    /** 原位更新私聊消息。 */
    suspend fun updateMessage(updatedMessage: Message) = messagesMutex.withLock {
        buckets.putPrivate(updatedMessage)
        buckets.getOrCreatePrivateCache(privateConversationId(updatedMessage)).add(updatedMessage)
    }

    /** 添加新的群聊消息（自动按时间戳排序插入） */
    suspend fun addGroupMessage(message: GroupMessage) = groupMessagesMutex.withLock {
        AppLogger.d(
            "[ChatState] 添加群聊消息: messageId=${message.messageId}, groupId=${message.groupId}," +
                " senderId=${message.senderId}," +
                " senderName=${message.senderName}, isSent=${message.isSent}, timestamp=${message.timestamp}"
        )

        val gid = groupConversationId(message)
        buckets.putGroup(message) // 全量镜像
        buckets.getOrCreateGroupCache(gid).add(message) // 分桶

        val shouldIncreaseUnread =
            message.senderId != GlobalAppState.currentUserId &&
                contacts.selectedTargetState.value?.conversationKey != ConversationKey.group(gid)
        conversationStateMutex.withLock {
            previews.updateConversationStateLocked(
                conversationId = ConversationKey.group(gid),
                lastIncomingMessageTime =
                    if (message.senderId != GlobalAppState.currentUserId) message.timestamp
                    else null,
                unreadDelta = if (shouldIncreaseUnread) 1 else 0,
            )
        }
        AppLogger.d("[ChatState] 添加群聊消息完成, 当前群消息总数: ${buckets.groupMessageCache.size}")
    }

    /** 批量添加群聊消息（用于加载历史消息，智能插入保证顺序） */
    suspend fun prependGroupMessages(
        newMessages: List<GroupMessage>,
        markAsUnread: Boolean = false,
        isCurrentAccount: () -> Boolean = { true },
    ) = groupMessagesMutex.withLock {
        if (!isCurrentAccount()) return@withLock
        val inserted = buckets.groupMessageCache.prepend(newMessages)
        newMessages.forEach(previews::groupPreview)
        if (newMessages.isNotEmpty()) {
            val affectedGroups = newMessages.map { groupConversationId(it) }.distinct()
            affectedGroups.forEach { gid ->
                buckets
                    .getOrCreateGroupCache(gid)
                    .prepend(newMessages.filter { groupConversationId(it) == gid })
            }
            conversationStateMutex.withLock {
                previews.recordGroupConversationHistoryLocked(inserted, markAsUnread)
            }
        }
    }

    /** 更新群聊消息发送状态 */
    suspend fun updateGroupMessageSentStatus(messageId: String, isSent: Boolean) {
        groupMessagesMutex.withLock {
            AppLogger.d("[ChatState] 更新群聊消息发送状态: messageId=$messageId, isSent=$isSent")
            val msg = buckets.findGroup { it.messageId == messageId } ?: return@withLock
            if (!isSent && msg.deliveryState == model.MessageDeliveryState.ACCEPTED) return@withLock
            val updated =
                msg.copy(
                    isSent = isSent,
                    deliveryState =
                        if (isSent) model.MessageDeliveryState.ACCEPTED
                        else model.MessageDeliveryState.FAILED,
                )
            buckets.putGroup(updated)
            buckets.getOrCreateGroupCache(updated.groupId).add(updated)
        }
    }

    /** 选择聊天对象 */
    suspend fun selectChatTarget(user: User?) = conversationStateMutex.withLock {
        contacts.selectedTargetState.value = user
        user?.let { targetUser ->
            val previousState = previews.previewStates.value[targetUser.conversationKey]
            if (previousState != null && previousState.unreadCount != 0) {
                previews.updateConversationStateLocked(targetUser.conversationKey, unreadCount = 0)
            }
        }
    }

    /**
     * 自适应缓存容量：应用切前后台时调用，立即触发 LRU 淘汰。
     *
     * @param foreground true=保持最大容量(200)，false=缩减(50)释放内存
     */
    suspend fun setAdaptiveCache(foreground: Boolean) {
        val target = if (foreground) MAX_MESSAGES else MAX_MESSAGES / 4
        messagesMutex.withLock {
            buckets.messageCache.maxSize = target
            buckets.privateMessageCaches.values.forEach { it.maxSize = target }
        }
        groupMessagesMutex.withLock {
            buckets.groupMessageCache.maxSize = target
            buckets.groupMessageCaches.values.forEach { it.maxSize = target }
        }
    }

    /** 设置加载更多状态 */
    suspend fun setLoadingMore(loading: Boolean) = loadingMutex.withLock {
        _isLoadingMore.value = loading
    }

    /** 删除私聊消息 */
    suspend fun deleteMessage(messageId: String) = messagesMutex.withLock {
        val msg = buckets.findPrivate { it.messageId == messageId }
        buckets.messageCache.remove(messageId)
        synchronized(historyChanges.dirtyLock) { historyChanges.dirtyPrivate.remove(messageId) }
        if (msg != null)
            buckets.getOrCreatePrivateCache(privateConversationId(msg)).remove(messageId)
        if (msg != null) {
            val key = ConversationKey.privateChat(privateConversationId(msg))
            synchronized(previews.previewLock) {
                val old = previews.previewStates.value[key]
                if (old?.previewMessageId == messageId) {
                    previews.previewStates.value =
                        previews.previewStates.value +
                            (key to
                                old.copy(
                                    previewText = "",
                                    previewTimestamp = 0,
                                    previewMessageId = "",
                                ))
                    buckets
                        .getOrCreatePrivateCache(key.id)
                        .snapshot
                        .lastOrNull()
                        ?.let(previews::privatePreview)
                }
            }
        }
    }

    /** 删除群聊消息 */
    suspend fun deleteGroupMessage(messageId: String) = groupMessagesMutex.withLock {
        val msg = buckets.findGroup { it.messageId == messageId }
        buckets.groupMessageCache.remove(messageId)
        synchronized(historyChanges.dirtyLock) { historyChanges.dirtyGroups.remove(messageId) }
        if (msg != null) buckets.getOrCreateGroupCache(msg.groupId).remove(messageId)
        if (msg != null) {
            val key = ConversationKey.group(msg.groupId)
            synchronized(previews.previewLock) {
                val old = previews.previewStates.value[key]
                if (old?.previewMessageId == messageId) {
                    previews.previewStates.value =
                        previews.previewStates.value +
                            (key to
                                old.copy(
                                    previewText = "",
                                    previewTimestamp = 0,
                                    previewMessageId = "",
                                ))
                    buckets
                        .getOrCreateGroupCache(key.id)
                        .snapshot
                        .lastOrNull()
                        ?.let(previews::groupPreview)
                }
            }
        }
    }

    /** 清空所有聊天数据（退出登录时调用） */
    suspend fun clear() = clearMutex.withLock {
        messagesMutex.withLock {
            contacts.reset()
            buckets.messageCache.clear()
            buckets.groupMessageCache.clear()
            buckets.privateMessageCaches.values.forEach { it.clear() }
            buckets.groupMessageCaches.values.forEach { it.clear() }
            buckets.privateMessageCaches.clear()
            buckets.cacheBudget.clear()
            synchronized(historyChanges.dirtyLock) {
                historyChanges.dirtyPrivate.clear()
                historyChanges.dirtyGroups.clear()
            }
            buckets.groupMessageCaches.clear()
            contacts.selectedTargetState.value = null
            _isLoadingMore.value = false
            previews.previewStates.value = emptyMap()
            cursors.reset()
        }
    }
}

// 全局单例ChatState，用于兼容旧代码，后续逐步改为依赖注入
val GlobalChatState = ChatState()
