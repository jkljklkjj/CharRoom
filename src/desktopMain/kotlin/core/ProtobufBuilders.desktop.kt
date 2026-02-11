package core

import com.example.proto.MessageProtos

actual fun buildLoginPayload(token: String?): ByteArray {
    val login = MessageProtos.LoginMessage.newBuilder()
        .setTargetClientId(token ?: "")
        .build()
    return MessageProtos.MessageWrapper.newBuilder()
        .setType(MsgType.LOGIN.wire)
        .setLogin(login)
        .build()
        .toByteArray()
}

actual fun buildHeartbeatPayload(): ByteArray {
    val hb = MessageProtos.HeartbeatMessage.newBuilder()
        .setTimestamp(System.currentTimeMillis())
        .build()
    return MessageProtos.MessageWrapper.newBuilder()
        .setType(MsgType.HEARTBEAT.wire)
        .setHeartbeat(hb)
        .build()
        .toByteArray()
}

actual fun buildLogoutPayload(userId: String): ByteArray {
    val logout = MessageProtos.LogoutMessage.newBuilder()
        .setUserId(userId)
        .build()
    return MessageProtos.MessageWrapper.newBuilder()
        .setType(MsgType.LOGOUT.wire)
        .setLogout(logout)
        .build()
        .toByteArray()
}

actual fun buildChatPayload(targetClientId: String, content: String, userId: Int, timestamp: Long): ByteArray {
    val chat = MessageProtos.ChatMessage.newBuilder()
        .setTargetClientId(targetClientId)
        .setContent(content)
        .setUserId(userId.toString())
        .setTimestamp(timestamp.toString())
        .build()
    return MessageProtos.MessageWrapper.newBuilder()
        .setType(MsgType.CHAT.wire)
        .setChat(chat)
        .build()
        .toByteArray()
}

actual fun buildGroupChatPayload(targetClientId: String, content: String, userId: Int): ByteArray {
    val gm = MessageProtos.GroupChatMessage.newBuilder()
        .setTargetClientId(targetClientId)
        .setContent(content)
        .setUserId(userId.toString())
        .build()
    return MessageProtos.MessageWrapper.newBuilder()
        .setType(MsgType.GROUP_CHAT.wire)
        .setGroupChat(gm)
        .build()
        .toByteArray()
}

actual fun buildCheckPayload(targetClientId: String): ByteArray {
    val check = MessageProtos.CheckMessage.newBuilder()
        .setTargetClientId(targetClientId)
        .build()
    return MessageProtos.MessageWrapper.newBuilder()
        .setType(MsgType.CHECK.wire)
        .setCheck(check)
        .build()
        .toByteArray()
}
