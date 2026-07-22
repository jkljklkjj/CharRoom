package presentation.viewmodel

import core.Chat
import core.MsgType
import core.buildChatPayload
import core.buildGroupChatPayload
import core.state.ChatState
import core.state.GlobalAppState
import core.Throttle
import core.ThrottleOp
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import model.Group
import model.GroupMessage
import model.Message
import model.MessageIdGenerator
import model.MessageType
import model.User

private val logger = KotlinLogging.logger {}

/**
 * 消息发送服务：负责私聊/群聊消息的构建、发送、重试。
 *
 * 从 ChatViewModel 中提取，降低 ViewModel 复杂度。
 */
class MessageSender(
    private val chatState: ChatState,
    private val scope: CoroutineScope
) {

    private val throttle = Throttle()

    /**
     * 发送私聊消息。
     */
    fun sendPrivateMessage(
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
            scope.launch { onDone() }
            return
        }

        val currentUserId = GlobalAppState.currentUserId
        val senderId = currentUserId ?: 0

        try {
            val timestamp = System.currentTimeMillis()
            val messageId = MessageIdGenerator.generateMessageId(senderId, messageText + fileUrl.orEmpty(), timestamp)

            val message = Message(
                senderId = senderId,
                message = messageText,
                sender = true,
                receiverId = user.id,
                timestamp = timestamp,
                isSent = true,
                messageId = messageId,
                replyToMessageId = replyToMessageId,
                replyToContent = replyToContent,
                replyToSender = replyToSender,
                messageType = messageType,
                fileUrl = fileUrl,
                fileName = fileName,
                fileSize = fileSize
            )

            scope.launch {
                chatState.upsertUser(user)
                chatState.addMessage(message)
            }

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
                fileSize = fileSize
            )

            Chat.send(
                payload = payload,
                type = MsgType.CHAT,
                targetClientId = user.id.toString(),
                expectedResponses = 1
            ) { success, _ ->
                scope.launch {
                    if (success) {
                        chatState.updateMessageSentStatus(messageId, true)
                        onDone()
                    } else {
                        onDone()
                        retryPrivateMessage(payload, messageId, user.id.toString())
                    }
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            println("[MessageSender] 发送私聊消息异常: ${e.message}")
            scope.launch { onDone() }
        }
    }

    /**
     * 发送群聊消息。
     */
    fun sendGroupMessage(
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
            scope.launch { onDone() }
            return
        }

        val currentUserId = GlobalAppState.currentUserId
        val senderId = currentUserId ?: 0
        val senderName = if (currentUserId != null) {
            chatState.users.value.find { it.id == currentUserId }?.username ?: "我"
        } else {
            "我"
        }

        try {
            val timestamp = System.currentTimeMillis()
            val messageId = MessageIdGenerator.generateGroupMessageId(group.id, senderId, messageText + fileUrl.orEmpty(), timestamp)

            val groupMessage = GroupMessage(
                groupId = group.id,
                senderName = senderName,
                text = messageText,
                senderId = senderId,
                timestamp = timestamp,
                isSent = true,
                messageId = messageId,
                replyToMessageId = replyToMessageId,
                replyToContent = replyToContent,
                replyToSender = replyToSender,
                messageType = messageType,
                fileUrl = fileUrl,
                fileName = fileName,
                fileSize = fileSize
            )

            scope.launch { chatState.addGroupMessage(groupMessage) }

            val payload = buildGroupChatPayload(
                targetClientId = group.id.toString(),
                content = messageText,
                replyToMessageId = replyToMessageId,
                replyToContent = replyToContent,
                replyToSender = replyToSender,
                messageType = messageType.ordinal,
                fileUrl = fileUrl,
                fileName = fileName,
                fileSize = fileSize
            )

            Chat.send(
                payload = payload,
                type = MsgType.GROUP_CHAT,
                targetClientId = group.id.toString(),
                expectedResponses = 1
            ) { success, _ ->
                scope.launch {
                    if (success) {
                        chatState.updateGroupMessageSentStatus(messageId, true)
                        onDone()
                    } else {
                        onDone()
                        retryGroupMessage(payload, messageId, group.id.toString())
                    }
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            println("[MessageSender] 发送群聊消息异常: ${e.message}")
            scope.launch { onDone() }
        }
    }

    private suspend fun retryPrivateMessage(payload: ByteArray, messageId: String, targetId: String) {
        delay(1500)
        Chat.send(payload, MsgType.CHAT, targetId, 1) { retrySuccess, _ ->
            scope.launch {
                chatState.updateMessageSentStatus(messageId, retrySuccess)
            }
        }
    }

    private suspend fun retryGroupMessage(payload: ByteArray, messageId: String, targetId: String) {
        delay(1500)
        Chat.send(payload, MsgType.GROUP_CHAT, targetId, 1) { retrySuccess, _ ->
            scope.launch {
                chatState.updateGroupMessageSentStatus(messageId, retrySuccess)
            }
        }
    }
}
