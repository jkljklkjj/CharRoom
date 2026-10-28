package core

import com.chatlite.proto.MessageProtos

/**
 * 构建AI助手聊天消息
 */
fun buildAgentChatPayload(
    targetClientId: String,
    content: String,
    userId: Int,
    timestamp: Long,
    replyToMessageId: String? = null,
    replyToContent: String? = null,
    replyToSender: String? = null,
    messageType: Int = 0,
    fileUrl: String? = null,
    fileName: String? = null,
    fileSize: Long? = null
): ByteArray {
    val actionBuilder = MessageProtos.ChatMessage.newBuilder()
        .setTargetClientId(targetClientId.toLong())
        .setContent(content)
        .setUserId(userId.toLong())
        .setTimestamp(timestamp)
        .setMessageType(MessageProtos.MessageType.forNumber(messageType))

    // 设置引用回复字段
    replyToMessageId?.let { actionBuilder.setReplyToMessageId(it) }
    replyToContent?.let { actionBuilder.setReplyToContent(it) }
    replyToSender?.let { actionBuilder.setReplyToSender(it) }

    // 设置文件字段
    fileUrl?.let { actionBuilder.setFileUrl(it) }
    fileName?.let { actionBuilder.setFileName(it) }
    fileSize?.let { actionBuilder.setFileSize(it) }

    val actions = ActionLogger.getSnapshot()
    for (action in actions) {
        val actionProto = MessageProtos.ClientAction.newBuilder()
            .setId(action.id)
            .setTimestamp(action.timestamp)
            .setType(action.type.name)
            .setTargetId(action.targetId ?: "")
            .putAllMetadata(action.metadata)
            .build()
        actionBuilder.addClientActions(actionProto)
    }

    return MessageProtos.MessageWrapper.newBuilder()
        .setType(MessageProtos.MessageWrapperType.AGENT_CHAT)
        .setChat(actionBuilder.build())
        .build()
        .toByteArray()
}
