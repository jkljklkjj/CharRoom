package com.chatlite.charroom.core

import com.chatlite.proto.MessageProtos
import core.MsgType

fun buildLoginPayload(token: String?): ByteArray {
    val login = MessageProtos.LoginMessage.newBuilder()
        .setTargetClientId(token ?: "")
        .build()
    return MessageProtos.MessageWrapper.newBuilder()
        .setType(MsgType.LOGIN.wire)
        .setLogin(login)
        .build()
        .toByteArray()
}

fun buildHeartbeatPayload(): ByteArray {
    val hb = MessageProtos.HeartbeatMessage.newBuilder()
        .setTimestamp(System.currentTimeMillis())
        .build()
    return MessageProtos.MessageWrapper.newBuilder()
        .setType(MsgType.HEARTBEAT.wire)
        .setHeartbeat(hb)
        .build()
        .toByteArray()
}

fun buildLogoutPayload(userId: String): ByteArray {
    val logout = MessageProtos.LogoutMessage.newBuilder()
        .setUserId(userId)
        .build()
    return MessageProtos.MessageWrapper.newBuilder()
        .setType(MsgType.LOGOUT.wire)
        .setLogout(logout)
        .build()
        .toByteArray()
}

fun buildChatPayload(
    targetClientId: String,
    content: String,
    userId: Int,
    timestamp: Long,
    replyToMessageId: String?,
    replyToContent: String?,
    replyToSender: String?,
    messageType: Int,
    fileUrl: String?,
    fileName: String?,
    fileSize: Long?
): ByteArray {
    val chatBuilder = MessageProtos.ChatMessage.newBuilder()
        .setTargetClientId(targetClientId)
        .setContent(content)
        .setUserId(userId.toString())
        .setTimestamp(timestamp.toString())
        .setMessageType(MessageProtos.MessageType.forNumber(messageType))

    // 设置引用回复字段
    replyToMessageId?.let { chatBuilder.setReplyToMessageId(it) }
    replyToContent?.let { chatBuilder.setReplyToContent(it) }
    replyToSender?.let { chatBuilder.setReplyToSender(it) }

    // 设置文件字段
    fileUrl?.let { chatBuilder.setFileUrl(it) }
    fileName?.let { chatBuilder.setFileName(it) }
    fileSize?.let { chatBuilder.setFileSize(it) }

    val chat = chatBuilder.build()
    return MessageProtos.MessageWrapper.newBuilder()
        .setType(MsgType.CHAT.wire)
        .setChat(chat)
        .build()
        .toByteArray()
}

fun buildGroupChatPayload(
    targetClientId: String,
    content: String,
    userId: Int,
    replyToMessageId: String?,
    replyToContent: String?,
    replyToSender: String?,
    messageType: Int,
    fileUrl: String?,
    fileName: String?,
    fileSize: Long?
): ByteArray {
    val gmBuilder = MessageProtos.GroupChatMessage.newBuilder()
        .setTargetClientId(targetClientId)
        .setContent(content)
        .setUserId(userId.toString())
        .setMessageType(MessageProtos.MessageType.forNumber(messageType))

    // 设置引用回复字段
    replyToMessageId?.let { gmBuilder.setReplyToMessageId(it) }
    replyToContent?.let { gmBuilder.setReplyToContent(it) }
    replyToSender?.let { gmBuilder.setReplyToSender(it) }

    // 设置文件字段
    fileUrl?.let { gmBuilder.setFileUrl(it) }
    fileName?.let { gmBuilder.setFileName(it) }
    fileSize?.let { gmBuilder.setFileSize(it) }

    val gm = gmBuilder.build()
    return MessageProtos.MessageWrapper.newBuilder()
        .setType(MsgType.GROUP_CHAT.wire)
        .setGroupChat(gm)
        .build()
        .toByteArray()
}

fun buildCheckPayload(targetClientId: String): ByteArray {
    val check = MessageProtos.CheckMessage.newBuilder()
        .setTargetClientId(targetClientId)
        .build()
    return MessageProtos.MessageWrapper.newBuilder()
        .setType(MsgType.CHECK.wire)
        .setCheck(check)
        .build()
        .toByteArray()
}