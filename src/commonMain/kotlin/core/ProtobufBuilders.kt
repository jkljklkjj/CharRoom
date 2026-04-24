package core

// Builders to produce serialized protobuf payloads. Actual implementations live in platform-specific source sets.
expect fun buildLoginPayload(token: String?): ByteArray
expect fun buildHeartbeatPayload(): ByteArray
expect fun buildLogoutPayload(userId: String): ByteArray
expect fun buildChatPayload(
    targetClientId: String,
    content: String,
    userId: Int,
    timestamp: Long,
    replyToMessageId: String? = null,
    replyToContent: String? = null,
    replyToSender: String? = null,
    messageType: Int = 0, // 0: TEXT, 1: IMAGE, 2: FILE
    fileUrl: String? = null,
    fileName: String? = null,
    fileSize: Long? = null
): ByteArray
expect fun buildAgentChatPayload(
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
): ByteArray
expect fun buildGroupChatPayload(
    targetClientId: String,
    content: String,
    userId: Int,
    replyToMessageId: String? = null,
    replyToContent: String? = null,
    replyToSender: String? = null,
    messageType: Int = 0,
    fileUrl: String? = null,
    fileName: String? = null,
    fileSize: Long? = null
): ByteArray
expect fun buildCheckPayload(targetClientId: String): ByteArray
