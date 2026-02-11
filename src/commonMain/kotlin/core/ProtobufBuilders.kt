package core

// Builders to produce serialized protobuf payloads. Actual implementations live in platform-specific source sets.
expect fun buildLoginPayload(token: String?): ByteArray
expect fun buildHeartbeatPayload(): ByteArray
expect fun buildLogoutPayload(userId: String): ByteArray
expect fun buildChatPayload(targetClientId: String, content: String, userId: Int, timestamp: Long): ByteArray
expect fun buildGroupChatPayload(targetClientId: String, content: String, userId: Int): ByteArray
expect fun buildCheckPayload(targetClientId: String): ByteArray
