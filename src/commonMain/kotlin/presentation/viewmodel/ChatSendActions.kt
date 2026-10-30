package presentation.viewmodel

import core.Chat
import core.MsgType
import core.ThrottleOp
import core.buildChatPayload
import core.buildGroupChatPayload
import core.state.GlobalAppState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import model.Group
import model.GroupMessage
import model.Message
import model.MessageIdGenerator
import model.MessageType
import model.User

internal fun ChatViewModel.enqueueSendPrivateMessage(
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
) {
    if (throttle.shouldThrottle(ThrottleOp.PRIVATE_SEND)) {
        sessionScope.launch { onDone() }
        return
    }
    val owner = GlobalAppState.currentUserId ?: 0
    val generation = sessionJob
    core.recoverOperation({ error -> failedSubmission(error, onDone) }) {
        val attachment = core.MessageAttachment(fileUrl, fileName, fileSize)
        val reply = core.MessageReply(replyToMessageId, replyToContent, replyToSender)
        val message = privateDraft(user.id, owner, messageText, messageType, attachment, reply)
        sessionScope.launch { chatState.upsertUser(user) }
        addMessage(message)
        submitSend(
            message.privatePayload(),
            MsgType.CHAT,
            user.id.toString(),
            message.messageId,
            owner,
            generation,
        ) {
            updateMessageSentStatus(message.messageId, it)
        }
        onDone()
    }
}

internal fun ChatViewModel.enqueueSendGroupMessage(
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
) {
    if (throttle.shouldThrottle(ThrottleOp.GROUP_SEND)) {
        sessionScope.launch { onDone() }
        return
    }
    val owner = GlobalAppState.currentUserId
    val generation = sessionJob
    val senderName =
        owner?.let { id -> chatState.users.value.find { !it.isGroup && it.id == id }?.username }
            ?: "我"
    core.recoverOperation({ error -> failedSubmission(error, onDone) }) {
        val attachment = core.MessageAttachment(fileUrl, fileName, fileSize)
        val reply = core.MessageReply(replyToMessageId, replyToContent, replyToSender)
        val message =
            groupDraft(
                group.id,
                owner ?: 0,
                senderName,
                messageText,
                messageType,
                attachment,
                reply,
            )
        addGroupMessage(message)
        submitSend(
            message.groupPayload(),
            MsgType.GROUP_CHAT,
            group.id.toString(),
            message.messageId,
            owner ?: 0,
            generation,
        ) {
            updateGroupMessageSentStatus(message.messageId, it)
        }
        onDone()
    }
}

private fun ChatViewModel.failedSubmission(failure: Exception, onDone: () -> Unit) {
    logger.error(failure) { "Message submission failed" }
    sessionScope.launch { onDone() }
}

private fun privateDraft(
    target: Int,
    owner: Int,
    text: String,
    kind: MessageType,
    attachment: core.MessageAttachment,
    reply: core.MessageReply,
): Message {
    val timestamp = System.currentTimeMillis()
    val id =
        MessageIdGenerator.generateMessageId(owner, text + attachment.fileUrl.orEmpty(), timestamp)
    return Message(
        senderId = owner,
        receiverId = target,
        message = text,
        sender = true,
        timestamp = timestamp,
        isSent = false,
        deliveryState = model.MessageDeliveryState.QUEUED,
        messageId = id,
        messageType = kind,
        fileUrl = attachment.fileUrl,
        fileName = attachment.fileName,
        fileSize = attachment.fileSize,
        replyToMessageId = reply.id,
        replyToContent = reply.content,
        replyToSender = reply.sender,
    )
}

private fun groupDraft(
    target: Int,
    owner: Int,
    senderName: String,
    text: String,
    kind: MessageType,
    attachment: core.MessageAttachment,
    reply: core.MessageReply,
): GroupMessage {
    val timestamp = System.currentTimeMillis()
    val id =
        MessageIdGenerator.generateGroupMessageId(
            target,
            owner,
            text + attachment.fileUrl.orEmpty(),
            timestamp,
        )
    return GroupMessage(
        groupId = target,
        senderId = owner,
        senderName = senderName,
        text = text,
        timestamp = timestamp,
        isSent = false,
        deliveryState = model.MessageDeliveryState.QUEUED,
        messageId = id,
        messageType = kind,
        fileUrl = attachment.fileUrl,
        fileName = attachment.fileName,
        fileSize = attachment.fileSize,
        replyToMessageId = reply.id,
        replyToContent = reply.content,
        replyToSender = reply.sender,
    )
}

private fun Message.privatePayload(): ByteArray =
    buildChatPayload(
        targetClientId = receiverId.toString(),
        content = message,
        timestamp = timestamp,
        messageId = messageId,
        messageType = messageType.ordinal,
        attachment = core.MessageAttachment(fileUrl, fileName, fileSize),
        replyToMessageId = replyToMessageId,
        replyToContent = replyToContent,
        replyToSender = replyToSender,
    )

private fun GroupMessage.groupPayload(): ByteArray =
    buildGroupChatPayload(
        targetClientId = groupId.toString(),
        content = text,
        timestamp = timestamp,
        messageId = messageId,
        messageType = messageType.ordinal,
        attachment = core.MessageAttachment(fileUrl, fileName, fileSize),
        replyToMessageId = replyToMessageId,
        replyToContent = replyToContent,
        replyToSender = replyToSender,
    )

private fun ChatViewModel.sendOwnerCurrent(owner: Int, generation: Job): Boolean =
    GlobalAppState.currentUserId == owner && sessionJob === generation

private fun ChatViewModel.submitSend(
    payload: ByteArray,
    type: MsgType,
    target: String,
    id: String,
    owner: Int,
    generation: Job,
    status: (Boolean) -> Unit,
) {
    updateDeliveryState(id, model.MessageDeliveryState.SENDING)
    Chat.send(payload, type, target, 1) callback@{ success, _ ->
        if (!sendOwnerCurrent(owner, generation)) return@callback
        sessionScope.launch {
            if (success) status(true)
            else {
                updateDeliveryState(id, model.MessageDeliveryState.QUEUED)
                delay(1500)
                updateDeliveryState(id, model.MessageDeliveryState.SENDING)
                retrySend(payload, type, target, owner, generation, status)
            }
        }
    }
}

private fun ChatViewModel.retrySend(
    payload: ByteArray,
    type: MsgType,
    target: String,
    owner: Int,
    generation: Job,
    status: (Boolean) -> Unit,
) {
    Chat.send(payload, type, target, 1) retry@{ success, _ ->
        if (!sendOwnerCurrent(owner, generation)) return@retry
        sessionScope.launch { status(success) }
    }
}
