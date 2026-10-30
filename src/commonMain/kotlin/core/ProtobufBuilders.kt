package core

import com.chatlite.proto.MessageProtos

/** 构建登录消息 */
fun buildLoginPayload(
    token: String?,
    deviceType: String = ServerConfig.DEVICE_TYPE,
    deviceId: String = generateDeviceId(),
): ByteArray {
    val login =
        MessageProtos.LoginMessage.newBuilder()
            .setToken(token ?: "")
            .setDeviceType(deviceType.toProtoEnum())
            .setDeviceId(deviceId)
            .build()
    return MessageProtos.MessageWrapper.newBuilder()
        .setType(MessageProtos.MessageWrapperType.LOGIN)
        .setLogin(login)
        .build()
        .toByteArray()
}

/** 生成或返回缓存的设备 ID（进程生命周期内持久，线程安全）。 */
private val cachedDeviceId: String by lazy {
    java.util.UUID.randomUUID().toString()
}

fun generateDeviceId(): String = cachedDeviceId

/** 构建心跳消息 */
fun buildHeartbeatPayload(): ByteArray {
    val hb =
        MessageProtos.HeartbeatMessage.newBuilder().setTimestamp(System.currentTimeMillis()).build()
    return MessageProtos.MessageWrapper.newBuilder()
        .setType(MessageProtos.MessageWrapperType.HEARTBEAT)
        .setHeartbeat(hb)
        .build()
        .toByteArray()
}

/** 构建登出消息 */
fun buildLogoutPayload(userId: String): ByteArray {
    val logout = MessageProtos.LogoutMessage.newBuilder().setUserId(userId.toLong()).build()
    return MessageProtos.MessageWrapper.newBuilder()
        .setType(MessageProtos.MessageWrapperType.LOGOUT)
        .setLogout(logout)
        .build()
        .toByteArray()
}

/** 构建普通聊天消息 */
fun buildChatPayload(
    targetClientId: String, // 数字 ID 字符串
    content: String,
    userId: Int = 0,
    timestamp: Long,
    replyToMessageId: String? = null,
    replyToContent: String? = null,
    replyToSender: String? = null,
    messageType: Int = 0,
    attachment: MessageAttachment = MessageAttachment(),
    messageId: String = java.util.UUID.randomUUID().toString(),
): ByteArray {
    val chatBuilder =
        MessageProtos.ChatMessage.newBuilder()
            .setTargetClientId(targetClientId.toLong())
            .setContent(content)
            .setUserId(userId.toLong())
            .setTimestamp(timestamp)
            .setMessageId(messageId)
            .setMessageType(MessageProtos.MessageType.forNumber(messageType))

    // 设置引用回复字段
    replyToMessageId?.let { chatBuilder.setReplyToMessageId(it) }
    replyToContent?.let { chatBuilder.setReplyToContent(it) }
    replyToSender?.let { chatBuilder.setReplyToSender(it) }

    // 设置文件字段
    attachment.fileUrl?.let { chatBuilder.setFileUrl(it) }
    attachment.fileName?.let { chatBuilder.setFileName(it) }
    attachment.fileSize?.let { chatBuilder.setFileSize(it) }

    val chat = chatBuilder.build()
    return MessageProtos.MessageWrapper.newBuilder()
        .setType(MessageProtos.MessageWrapperType.CHAT)
        .setChat(chat)
        .build()
        .toByteArray()
}

/** 构建群聊消息 */
fun buildGroupChatPayload(
    targetClientId: String,
    content: String,
    userId: Int = 0,
    replyToMessageId: String? = null,
    replyToContent: String? = null,
    replyToSender: String? = null,
    messageType: Int = 0,
    attachment: MessageAttachment = MessageAttachment(),
    messageId: String = java.util.UUID.randomUUID().toString(),
    timestamp: Long = System.currentTimeMillis(),
): ByteArray {
    val gmBuilder =
        MessageProtos.GroupChatMessage.newBuilder()
            .setTargetClientId(targetClientId.toLong())
            .setContent(content)
            .setUserId(userId.toLong())
            .setMessageId(messageId)
            .setTimestamp(timestamp)
            .setMessageType(MessageProtos.MessageType.forNumber(messageType))

    // 设置引用回复字段
    replyToMessageId?.let { gmBuilder.setReplyToMessageId(it) }
    replyToContent?.let { gmBuilder.setReplyToContent(it) }
    replyToSender?.let { gmBuilder.setReplyToSender(it) }

    // 设置文件字段
    attachment.fileUrl?.let { gmBuilder.setFileUrl(it) }
    attachment.fileName?.let { gmBuilder.setFileName(it) }
    attachment.fileSize?.let { gmBuilder.setFileSize(it) }

    val gm = gmBuilder.build()
    return MessageProtos.MessageWrapper.newBuilder()
        .setType(MessageProtos.MessageWrapperType.GROUP_CHAT)
        .setGroupChat(gm)
        .build()
        .toByteArray()
}

/** 构建在线状态检查消息 */
fun buildCheckPayload(targetClientId: String): ByteArray {
    val check =
        MessageProtos.CheckMessage.newBuilder()
            .setConversationType(MessageProtos.ConversationType.PRIVATE)
            .setTargetClientId(targetClientId.toLong())
            .build()
    return MessageProtos.MessageWrapper.newBuilder()
        .setType(MessageProtos.MessageWrapperType.CHECK)
        .setCheck(check)
        .build()
        .toByteArray()
}

fun buildAckPayload(messageId: String): ByteArray {
    val ack = MessageProtos.AckMessage.newBuilder().setMessageId(messageId).build()
    return MessageProtos.MessageWrapper.newBuilder()
        .setType(MessageProtos.MessageWrapperType.ACK)
        .setAck(ack)
        .build()
        .toByteArray()
}

/** 设备类型字符串 → proto wire 枚举（未知类型返回 UNSPECIFIED，服务端按未指定处理） */
fun String.toProtoEnum(): MessageProtos.DeviceType =
    when (lowercase()) {
        "mobile" -> MessageProtos.DeviceType.MOBILE
        "desktop" -> MessageProtos.DeviceType.DESKTOP
        "web" -> MessageProtos.DeviceType.WEB
        "cli" -> MessageProtos.DeviceType.CLI
        else -> MessageProtos.DeviceType.DEVICE_TYPE_UNSPECIFIED
    }
