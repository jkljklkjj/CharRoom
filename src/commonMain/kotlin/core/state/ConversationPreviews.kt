package core.state

import kotlinx.coroutines.flow.*
import model.ConversationKey
import model.GroupMessage
import model.Message

internal class ConversationPreviews {
    // 会话预览状态（最近消息时间、未读数）
    val previewStates = MutableStateFlow<Map<ConversationKey, ConversationPreviewState>>(emptyMap())

    val conversationStates: StateFlow<Map<ConversationKey, ConversationPreviewState>> =
        previewStates.asStateFlow()

    val previewLock = Any()

    fun privatePreview(message: Message) =
        preview(
            ConversationKey.privateChat(privateConversationId(message)),
            message.messageId,
            message.message,
            message.timestamp,
            "",
            message.sender,
        )

    fun groupPreview(message: GroupMessage) =
        preview(
            ConversationKey.group(message.groupId),
            message.messageId,
            message.text,
            message.timestamp,
            message.senderName,
            message.senderId == GlobalAppState.currentUserId,
        )

    fun preview(
        key: ConversationKey,
        id: String,
        text: String,
        time: Long,
        sender: String,
        own: Boolean,
    ) =
        synchronized(previewLock) {
            val previous = previewStates.value[key] ?: ConversationPreviewState()
            if (time < previous.previewTimestamp) return@synchronized
            val next =
                previous.copy(
                    previewMessageId = id,
                    previewText = text.replace('\n', ' ').take(60),
                    previewTimestamp = time,
                    previewSender = sender.take(80),
                    previewOwn = own,
                )
            if (next != previous) previewStates.value = previewStates.value + (key to next)
        }

    fun updateConversationStateLocked(
        conversationId: ConversationKey,
        lastIncomingMessageTime: Long? = null,
        unreadDelta: Int = 0,
        unreadCount: Int? = null,
    ) =
        synchronized(previewLock) {
            val currentStates = previewStates.value.toMutableMap()
            val previousState = currentStates[conversationId] ?: ConversationPreviewState()
            val updatedState =
                previousState.copy(
                    lastIncomingMessageTime =
                        maxOf(
                            previousState.lastIncomingMessageTime,
                            lastIncomingMessageTime ?: previousState.lastIncomingMessageTime,
                        ),
                    unreadCount =
                        unreadCount ?: (previousState.unreadCount + unreadDelta).coerceAtLeast(0),
                )

            if (updatedState != previousState) {
                currentStates[conversationId] = updatedState
                previewStates.value = currentStates
            }
        }

    /** 统一记录消息到会话历史状态。 私聊和群聊共用一个逻辑，差异通过 lambda 参数化。 */
    fun recordPrivateConversationHistoryLocked(
        messages: List<Message>,
        markAsUnread: Boolean,
    ) {
        val currentUserId = GlobalAppState.currentUserId
        val lastTimeByConversation = mutableMapOf<ConversationKey, Long>()
        val unreadByConversation = mutableMapOf<ConversationKey, Int>()

        messages.forEach { message ->
            val conversationId = ConversationKey.privateChat(privateConversationId(message))
            lastTimeByConversation[conversationId] =
                maxOf(lastTimeByConversation[conversationId] ?: 0L, message.timestamp)
            val incomingFromPeer = !message.sender && message.senderId != currentUserId
            if (markAsUnread && incomingFromPeer) {
                unreadByConversation[conversationId] =
                    (unreadByConversation[conversationId] ?: 0) + 1
            }
        }
        applyConversationUpdates(lastTimeByConversation, unreadByConversation)
    }

    fun recordGroupConversationHistoryLocked(
        messages: List<GroupMessage>,
        markAsUnread: Boolean,
    ) {
        val currentUserId = GlobalAppState.currentUserId
        val lastTimeByConversation = mutableMapOf<ConversationKey, Long>()
        val unreadByConversation = mutableMapOf<ConversationKey, Int>()

        messages.forEach { message ->
            val conversationId = ConversationKey.group(message.groupId)
            lastTimeByConversation[conversationId] =
                maxOf(lastTimeByConversation[conversationId] ?: 0L, message.timestamp)
            if (markAsUnread && message.senderId != currentUserId) {
                unreadByConversation[conversationId] =
                    (unreadByConversation[conversationId] ?: 0) + 1
            }
        }
        applyConversationUpdates(lastTimeByConversation, unreadByConversation)
    }

    /** 两个 record* 方法的公共尾：将统计结果刷入 previewStates。 */
    fun applyConversationUpdates(
        lastTimeByConversation: Map<ConversationKey, Long>,
        unreadByConversation: Map<ConversationKey, Int>,
    ) {
        lastTimeByConversation.forEach { (conversationId, lastTime) ->
            updateConversationStateLocked(conversationId, lastIncomingMessageTime = lastTime)
        }
        unreadByConversation.forEach { (conversationId, unreadCount) ->
            updateConversationStateLocked(conversationId, unreadDelta = unreadCount)
        }
    }
}

internal fun privateConversationId(message: Message): Int =
    if (message.sender) message.receiverId else message.senderId

internal fun groupConversationId(message: GroupMessage): Int = message.groupId
