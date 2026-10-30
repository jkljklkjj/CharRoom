package core

// 本文件由 ApiClient.kt 拆分而来：消息 API（历史/离线/同步/发送/在线状态）。
// 共享基础设施（httpClient / sendRequest / json / ApiResponse 等）见 ApiClient.kt。

import core.state.GlobalAppState
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import model.Message

suspend fun getHistoryMessages(
    token: String,
    userId: Int,
    page: Int = 1,
    limit: Int = 50,
): List<Message> {
    val path = "${ApiEndpoints.OFFLINE}?userId=$userId&page=$page&limit=$limit"

    val response =
        sendRequest<List<MessageSerializer>>(
            path = path,
            method = "GET",
            token = token,
        )

    return response.data?.let { convertMessages(it) } ?: emptyList()
}

suspend fun getGroupHistoryMessages(
    token: String,
    groupId: Int,
    page: Int = 1,
    limit: Int = 50,
): List<Message> {
    val path = "${ApiEndpoints.OFFLINE}?groupId=$groupId&page=$page&limit=$limit"

    val response =
        sendRequest<List<GroupMessageSerializer>>(
            path = path,
            method = "GET",
            token = token,
        )

    return response.data?.let { convertMessages(it, groupId) } ?: emptyList()
}

suspend fun getOfflineHistoryPage(token: String, limit: Int = 100): List<RecentHistoryMessage> {
    val result =
        sendRequest<List<RecentHistoryMessage>>(
            "${ApiEndpoints.OFFLINE}?pageSize=${limit.coerceIn(1,200)}",
            token = token,
        )
    check(result.isSuccess) { "离线消息读取失败（${result.code}）" }
    return checkNotNull(result.data) { "离线消息响应缺少正文" }
}

suspend fun confirmOfflineMessages(token: String, ids: List<String>) {
    if (ids.isEmpty()) return
    val result =
        sendRequest<kotlinx.serialization.json.JsonElement>(
            "${ApiEndpoints.OFFLINE}/ack",
            method = "POST",
            body = buildJsonObject { put("ids", JsonArray(ids.map(::JsonPrimitive))) },
            token = token,
        )
    check(result.isSuccess) { "离线消息确认失败（${result.code}）" }
}

suspend fun getOfflineMessages(token: String): List<Message> =
    getOfflineHistoryPage(token).map {
        it.toPrivateMessage(checkNotNull(GlobalAppState.currentUserId))
    }

suspend fun syncMessages(
    token: String,
    conversationId: String,
    lastSeqId: Long,
    limit: Int = 50,
): SyncMessagesResult {
    val requestBody = buildJsonObject {
        put("conversationId", conversationId)
        put("lastSeqId", lastSeqId)
        put("limit", limit)
    }
    val response =
        sendRequest<SyncMessagesResult>(
            path = ApiEndpoints.SYNC_MESSAGES,
            method = "POST",
            body = requestBody,
            token = token,
        )
    return response.data ?: SyncMessagesResult()
}

suspend fun sendPrivateMessage(
    token: String,
    receiverId: String,
    content: String,
    messageType: String,
    fileUrl: String? = null,
    fileName: String? = null,
    fileSize: Long? = null,
    replyToMessageId: String? = null,
): Boolean =
    core.recoverOperation(
        onFailure = { e ->
            AppLogger.w("发送消息异常: ${e.message}")
            false
        }
    ) {
        AppLogger.d("发送私聊消息请求: receiverId=$receiverId, messageType=$messageType")
        val response =
            httpClient.post(ApiEndpoints.url("/message/send/private")) {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                val body = buildJsonObject {
                    put("receiverId", receiverId)
                    put("content", content)
                    put("messageType", messageType)
                    fileUrl?.let { put("fileUrl", it) }
                    fileName?.let { put("fileName", it) }
                    fileSize?.let { put("fileSize", it) }
                    replyToMessageId?.let { put("replyToMessageId", it) }
                }
                setBody(body)
            }
        AppLogger.d("发送私聊消息响应状态: ${response.status}")
        val apiResponse = response.body<ApiResponse<Unit>>()
        AppLogger.d("发送私聊消息解析结果: isSuccess=${apiResponse.isSuccess}, code=${apiResponse.code}")
        apiResponse.isSuccess
    }

suspend fun sendGroupMessage(
    token: String,
    groupId: String,
    content: String,
    messageType: String,
    fileUrl: String? = null,
    fileName: String? = null,
    fileSize: Long? = null,
    replyToMessageId: String? = null,
): Boolean =
    core.recoverOperation(
        onFailure = { e ->
            AppLogger.e("Group message request failed", e)
            false
        }
    ) {
        val response =
            httpClient.post(ApiEndpoints.url("/message/send/group")) {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("groupId", groupId)
                        put("content", content)
                        put("messageType", messageType)
                        fileUrl?.let { put("fileUrl", it) }
                        fileName?.let { put("fileName", it) }
                        fileSize?.let { put("fileSize", it) }
                        replyToMessageId?.let { put("replyToMessageId", it) }
                    }
                )
            }
        val apiResponse = response.body<ApiResponse<Unit>>()
        apiResponse.isSuccess
    }

suspend fun batchOnlineStatus(token: String, userIds: List<Int>): Map<String, Boolean> {
    if (userIds.isEmpty()) return emptyMap()
    return core.recoverOperation(
        onFailure = { e ->
            AppLogger.w("批量在线状态查询失败: ${e.message}")
            emptyMap()
        }
    ) {
        val response =
            httpClient.post(ApiEndpoints.url("/users/online-status")) {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("userIds", userIds.joinToString(","))
                    }
                )
            }
        val apiResponse = response.body<ApiResponse<Map<String, Boolean>>>()
        apiResponse.data ?: emptyMap()
    }
}

/** Never treat an unavailable history endpoint as an empty, successfully restored history. */
suspend fun getRecentHistoryPage(token: String, cursor: RecentHistoryCursor): RecentHistoryPage {
    val response =
        httpClient.post(ApiEndpoints.url("/messages/recent")) {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(cursor)
        }
    check(response.status.isSuccess()) { "History request failed: ${response.status.value}" }
    val body = response.body<ApiResponse<RecentHistoryPage>>()
    check(body.isSuccess) { "History request rejected: ${body.code}" }
    return checkNotNull(body.data) { "History response is missing data" }
}
