package core

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.client.call.*
import kotlinx.serialization.json.*
import model.Message
import model.MessageType

suspend fun getOfflineMessages(token: String): List<Message> = sendRequest<List<MessageSerializer>>(ApiEndpoints.OFFLINE, "GET", token = token).data
    ?.map { it.toMessage() } ?: emptyList()

suspend fun getHistoryMessages(token: String, userId: Int, page: Int = 1, limit: Int = 50): List<Message> =
    sendRequest<List<MessageSerializer>>("${ApiEndpoints.OFFLINE}?userId=$userId&page=$page&limit=$limit", "GET", token = token).data
        ?.map { it.toMessage() } ?: emptyList()

suspend fun getGroupHistoryMessages(token: String, groupId: Int, page: Int = 1, limit: Int = 50): List<Message> =
    sendRequest<List<GroupMessageSerializer>>("${ApiEndpoints.OFFLINE}?groupId=$groupId&page=$page&limit=$limit", "GET", token = token).data
        ?.map { it.toMessage(groupId) } ?: emptyList()

suspend fun syncMessages(token: String, conversationId: String, lastSeqId: Long, limit: Int = 50): SyncMessagesResult {
    val body = buildJsonObject { put("conversationId", conversationId); put("lastSeqId", lastSeqId); put("limit", limit) }
    return sendRequest<SyncMessagesResult>(ApiEndpoints.SYNC_MESSAGES, "POST", body, token).data ?: SyncMessagesResult()
}

suspend fun syncGlobal(token: String, lastGlobalSeqId: Long, limit: Int = 100): GlobalSyncResult {
    val body = buildJsonObject { put("lastGlobalSeqId", lastGlobalSeqId); put("limit", limit) }
    return sendRequest<GlobalSyncResult>(ApiEndpoints.SYNC_GLOBAL, "POST", body, token).data ?: GlobalSyncResult()
}

suspend fun markAsRead(token: String, conversationId: String, lastReadSeqId: Long): Boolean {
    return sendRequest<Unit>(ApiEndpoints.SYNC_READ, "POST", buildJsonObject { put("conversationId", conversationId); put("lastReadSeqId", lastReadSeqId) }, token).isSuccess
}

suspend fun getReadStatus(token: String): Map<String, Long> = sendRequest<Map<String, Long>>(ApiEndpoints.SYNC_READ_STATUS, "GET", token = token).data ?: emptyMap()

suspend fun sendPrivateMessage(token: String, receiverId: String, content: String, messageType: String,
    fileUrl: String? = null, fileName: String? = null, fileSize: Long? = null, replyToMessageId: String? = null): Boolean = try {
    httpClient.post(ApiEndpoints.url("/message/send/private")) {
        header("Authorization", "Bearer $token"); contentType(ContentType.Application.Json)
        setBody(buildJsonObject {
            put("receiverId", receiverId); put("content", content); put("messageType", messageType)
            fileUrl?.let { put("fileUrl", it) }; fileName?.let { put("fileName", it) }
            fileSize?.let { put("fileSize", it) }; replyToMessageId?.let { put("replyToMessageId", it) }
        })
    }.body<ApiResponse<Unit>>().isSuccess
} catch (_: Exception) { false }

suspend fun sendGroupMessage(token: String, groupId: String, content: String, messageType: String,
    fileUrl: String? = null, fileName: String? = null, fileSize: Long? = null, replyToMessageId: String? = null): Boolean = try {
    httpClient.post(ApiEndpoints.url("/message/send/group")) {
        header("Authorization", "Bearer $token"); contentType(ContentType.Application.Json)
        setBody(buildJsonObject {
            put("groupId", groupId); put("content", content); put("messageType", messageType)
            fileUrl?.let { put("fileUrl", it) }; fileName?.let { put("fileName", it) }
            fileSize?.let { put("fileSize", it) }; replyToMessageId?.let { put("replyToMessageId", it) }
        })
    }.body<ApiResponse<Unit>>().isSuccess
} catch (_: Exception) { false }

fun MessageSerializer.toMessage() = Message(
    senderId = senderId, receiverId = receiverId, message = content, timestamp = timestamp, isSent = true,
    messageType = try { MessageType.valueOf(messageType) } catch (_: Exception) { MessageType.TEXT },
    fileUrl = fileUrl, fileName = fileName, fileSize = fileSize,
    replyToMessageId = replyToMessageId, replyToContent = replyToContent, replyToSender = replyToSender,
    messageId = messageId ?: "${senderId}_${receiverId}_${timestamp}"
)

fun GroupMessageSerializer.toMessage(groupId: Int = this.groupId) = Message(
    senderId = senderId, receiverId = -groupId, message = content, timestamp = timestamp, isSent = true,
    messageType = try { MessageType.valueOf(messageType) } catch (_: Exception) { MessageType.TEXT },
    fileUrl = fileUrl, fileName = fileName, fileSize = fileSize,
    replyToMessageId = replyToMessageId, replyToContent = replyToContent, replyToSender = replyToSender,
    messageId = messageId ?: "${groupId}_${senderId}_${timestamp}"
)
