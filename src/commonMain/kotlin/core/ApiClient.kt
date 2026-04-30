package core

import androidx.compose.runtime.mutableStateOf
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.core.writeFully
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import model.Group
import model.Message
import model.MessageType
import model.User

// 简单 Json 工具（忽略未知字段）
val json = Json { ignoreUnknownKeys = true }

private val httpClient = HttpClient {
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = 30000
        connectTimeoutMillis = 10000
        socketTimeoutMillis = 30000
    }
}

private suspend fun attachActionHeader(builder: HttpRequestBuilder) {
    try {
        if (builder.url.encodedPath != ApiEndpoints.AGENT_NL && builder.url.encodedPath != ApiEndpoints.AGENT_NL_STREAM) {
            ActionLogger.log(
                Action(
                    type = ActionType.OTHER,
                    targetId = builder.url.encodedPath,
                    metadata = mapOf(
                        "source" to "sendRequest",
                        "method" to builder.method.value
                    )
                )
            )
        }
    } catch (_: Exception) {
        // keep existing request behavior
    }
}

/**
 * 发送HTTP请求
 * @param path 接口路径
 * @param method 请求方法 GET/POST/PUT/DELETE等
 * @param body 请求体（JSON字符串）
 * @param token 认证Token
 * @param timeoutSeconds 超时时间（秒）
 * @return 响应体字符串
 */
suspend fun sendRequest(
    path: String,
    method: String = "GET",
    body: String? = null,
    token: String? = null,
    timeoutSeconds: Long = 30
): String {
    val normalizedMethod = method.uppercase()
    return try {
        val response = httpClient.request(ApiEndpoints.url(path)) {
            this.method = HttpMethod.parse(normalizedMethod)
            headers {
                if (!body.isNullOrEmpty()) {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
                if (!token.isNullOrEmpty()) {
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
            }
            if (body != null) {
                setBody(body)
            }
            timeout {
                requestTimeoutMillis = timeoutSeconds * 1000
            }
        }
        response.bodyAsText()
    } catch (e: Exception) {
        ""
    }
}

/**
 * 统一解析 ApiResponse<T> 中的 data 字段
 */
private inline fun <reified T> parseApiResponseData(response: String): T? {
    return try {
        val jsonObject = json.parseToJsonElement(response).jsonObject
        if (jsonObject["code"]?.jsonPrimitive?.int != 0) return null
        val data = jsonObject["data"] ?: return null
        json.decodeFromJsonElement(serializer(), data)
    } catch (e: Exception) {
        null
    }
}

private inline fun <reified T> parseResponse(response: String): T? {
    return parseApiResponseData(response) ?: try {
        json.decodeFromString(serializer(), response)
    } catch (e: Exception) {
        null
    }
}

private fun isApiSuccess(response: String): Boolean {
    return try {
        val jsonObject = json.parseToJsonElement(response).jsonObject
        jsonObject["code"]?.jsonPrimitive?.int == 0
    } catch (e: Exception) {
        false
    }
}

/**
 * 上传文件
 * @param path 上传路径
 * @param fileBytes 文件字节数组
 * @param fileName 文件名
 * @param token 认证Token
 * @return 上传结果，成功返回文件URL，失败返回null
 */
suspend fun uploadFile(
    path: String,
    fileBytes: ByteArray,
    fileName: String,
    token: String? = null
): String? {
    return try {
        val response = httpClient.submitFormWithBinaryData(
            url = ApiEndpoints.url(path),
            formData = formData {
                append("file", fileName, ContentType.Application.OctetStream) {
                    writeFully(fileBytes)
                }
            }
        ) {
            if (!token.isNullOrEmpty()) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            timeout {
                requestTimeoutMillis = 30000
            }
        }
        if (!response.status.isSuccess()) return null
        parseApiResponseData(response.bodyAsText())
    } catch (e: Exception) {
        null
    }
}

/**
 * 登录接口
 */
suspend fun login(account: String, password: String): String? {
    val body = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("account", account)
            put("password", password)
        }
    )
    val response = sendRequest(ApiEndpoints.LOGIN, "POST", body)
    return parseApiResponseData(response)
}

/**
 * 注册接口
 */
suspend fun register(username: String, password: String, email: String = ""): Int? {
    val body = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("username", username)
            put("password", password)
            put("email", email)
        }
    )
    val response = sendRequest(ApiEndpoints.REGISTER, "POST", body)
    return parseApiResponseData(response)
}

/**
 * 获取用户信息
 */
suspend fun getUserInfo(token: String): User? {
    val response = sendRequest(ApiEndpoints.USER_PROFILE, "GET", token = token)
    return parseApiResponseData(response)
}

/**
 * 获取好友列表
 */
suspend fun getFriendList(token: String): List<User> {
    val response = sendRequest(ApiEndpoints.FRIEND_GET, "POST", token = token)
    return parseResponse(response) ?: emptyList()
}

/**
 * 获取群组列表
 */
suspend fun getGroupList(token: String): List<Group> {
    val response = sendRequest(ApiEndpoints.GROUP_GET, "GET", token = token)
    return parseResponse(response) ?: emptyList()
}

/**
 * 获取历史消息
 */
suspend fun getHistoryMessages(token: String, userId: Int, page: Int = 1, limit: Int = 50): List<Message> {
    val path = "${ApiEndpoints.OFFLINE}?userId=$userId&page=$page&limit=$limit"
    val response = sendRequest(path, "GET", token = token)
    val messages = parseApiResponseData<List<MessageSerializer>>(response) ?: return emptyList()
    return convertMessages(messages)
}

/**
 * 获取群历史消息
 */
suspend fun getGroupHistoryMessages(token: String, groupId: Int, page: Int = 1, limit: Int = 50): List<Message> {
    val path = "${ApiEndpoints.OFFLINE}?groupId=$groupId&page=$page&limit=$limit"
    val response = sendRequest(path, "GET", token = token)
    val messages = parseApiResponseData<List<GroupMessageSerializer>>(response) ?: return emptyList()
    return convertMessages(messages, groupId)
}

/**
 * 发送好友申请
 */
suspend fun sendFriendRequest(token: String, targetUserId: Int, message: String = ""): Boolean {
    val body = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("targetUserId", targetUserId)
            put("message", message)
        }
    )
    val response = sendRequest(ApiEndpoints.FRIEND_ADD, "POST", body, token)
    return isApiSuccess(response)
}

/**
 * 处理好友申请
 */
suspend fun handleFriendRequest(token: String, requestId: Int, accept: Boolean): Boolean {
    val endpoint = if (accept) ApiEndpoints.FRIEND_ACCEPT else ApiEndpoints.FRIEND_REJECT
    val body = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("requestId", requestId)
        }
    )
    val response = sendRequest(endpoint, "POST", body, token)
    return isApiSuccess(response)
}

/**
 * 获取好友申请列表
 */
suspend fun getFriendRequests(token: String): List<FriendRequest> {
    val response = sendRequest(ApiEndpoints.FRIEND_REQUESTS, "GET", token = token)
    return parseApiResponseData(response) ?: emptyList()
}

/**
 * 获取群聊申请列表
 */
suspend fun getGroupRequests(token: String): List<FriendRequest> {
    val response = sendRequest(ApiEndpoints.GROUP_REQUESTS, "GET", token = token)
    return parseApiResponseData(response) ?: emptyList()
}

/**
 * 添加好友
 */
suspend fun addFriend(token: String, account: String): Boolean {
    val body = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("account", account)
        }
    )
    val response = sendRequest(ApiEndpoints.FRIEND_ADD, "POST", body, token)
    return isApiSuccess(response)
}

/**
 * 加入群组
 */
suspend fun addGroup(token: String, groupId: String): Boolean {
    val body = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("groupId", groupId)
        }
    )
    val response = sendRequest(ApiEndpoints.GROUP_ADD, "POST", body, token)
    return isApiSuccess(response)
}

/**
 * 查询用户详情
 */
suspend fun getUserDetail(token: String, userId: String): User? {
    val path = "${ApiEndpoints.USER_DETAIL}?account=$userId"
    val response = sendRequest(path, "GET", token = token)
    return parseApiResponseData(response)
}

/**
 * 查询群组详情
 */
suspend fun getGroupDetail(token: String, groupId: String): Group? {
    val path = "${ApiEndpoints.GROUP_DETAIL}?id=$groupId"
    val response = sendRequest(path, "GET", token = token)
    return parseResponse(response)
}

/**
 * 获取离线消息
 */
suspend fun getOfflineMessages(token: String): List<Message> {
    val response = sendRequest(ApiEndpoints.OFFLINE, "GET", token = token)
    val messages = parseApiResponseData<List<MessageSerializer>>(response) ?: return emptyList()
    return convertMessages(messages)
}

/**
 * 发送邮箱更新验证码
 */
suspend fun sendEmailUpdateVerifyCode(token: String, email: String): Boolean {
    val body = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("email", email)
        }
    )
    val response = sendRequest(ApiEndpoints.SEND_EMAIL_UPDATE_VERIFY_CODE, "POST", body, token)
    return isApiSuccess(response)
}

/**
 * 更新用户个人资料
 */
suspend fun updateUserProfile(token: String, username: String, phone: String, signature: String, password: String? = null): Boolean {
    val body = buildJsonObject {
        put("username", username)
        put("phone", phone)
        put("signature", signature)
        password?.let { put("password", it) }
    }.toString()
    val response = sendRequest(ApiEndpoints.USER_PROFILE_UPDATE, "POST", body, token)
    return isApiSuccess(response)
}

/**
 * 更新邮箱
 */
suspend fun updateEmail(token: String, newEmail: String, verifyCode: String): Boolean {
    val body = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("email", newEmail)
            put("verifyCode", verifyCode)
        }
    )
    val response = sendRequest(ApiEndpoints.USER_PROFILE_UPDATE_EMAIL, "POST", body, token)
    return isApiSuccess(response)
}

/**
 * 同意好友申请
 */
suspend fun acceptFriend(token: String, requestId: String): Boolean {
    val body = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("requestId", requestId)
        }
    )
    val response = sendRequest(ApiEndpoints.FRIEND_ACCEPT, "POST", body, token)
    return isApiSuccess(response)
}

/**
 * 拒绝好友申请
 */
suspend fun rejectFriend(token: String, requestId: String): Boolean {
    val body = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("requestId", requestId)
        }
    )
    val response = sendRequest(ApiEndpoints.FRIEND_REJECT, "POST", body, token)
    return isApiSuccess(response)
}

/**
 * 同意群聊申请
 */
suspend fun acceptGroupApplication(token: String, groupId: String, userId: String): Boolean {
    val body = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("groupId", groupId)
            put("userId", userId)
        }
    )
    val response = sendRequest(ApiEndpoints.GROUP_ACCEPT, "POST", body, token)
    return isApiSuccess(response)
}

/**
 * 拒绝群聊申请
 */
suspend fun rejectGroupApplication(token: String, groupId: String, userId: String): Boolean {
    val body = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("groupId", groupId)
            put("userId", userId)
        }
    )
    val response = sendRequest(ApiEndpoints.GROUP_REJECT, "POST", body, token)
    return isApiSuccess(response)
}

/**
 * 创建群组
 */
suspend fun createGroup(token: String, name: String, memberIds: List<Int>): Group? {
    val body = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("name", name)
            putJsonArray("memberIds") {
                memberIds.forEach { add(it) }
            }
        }
    )
    val response = sendRequest(ApiEndpoints.GROUP_ADD, "POST", body, token)
    return parseApiResponseData(response)
}

/**
 * 邀请加入群组
 */
suspend fun inviteToGroup(token: String, groupId: Int, userId: Int): Boolean {
    val body = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("groupId", groupId)
            put("userId", userId)
        }
    )
    // 暂用现有接口
    val response = sendRequest(ApiEndpoints.GROUP_ADD, "POST", body, token)
    return isApiSuccess(response)
}

/**
 * 退出群组
 */
suspend fun leaveGroup(token: String, groupId: Int): Boolean {
    val body = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("groupId", groupId)
        }
    )
    // 暂用现有接口
    val response = sendRequest(ApiEndpoints.GROUP_GET, "POST", body, token)
    return isApiSuccess(response)
}

/**
 * 修改用户信息
 */
suspend fun updateUserInfo(
    token: String,
    nickname: String? = null,
    avatar: String? = null,
    signature: String? = null
): Boolean {
    val body = buildJsonObject {
        nickname?.let { put("username", it) }
        signature?.let { put("signature", it) }
    }.toString()
    val response = sendRequest(ApiEndpoints.USER_PROFILE_UPDATE, "POST", body, token)
    return isApiSuccess(response)
}

/**
 * 修改密码
 */
suspend fun changePassword(token: String, oldPassword: String, newPassword: String): Boolean {
    val body = buildJsonObject {
        put("password", newPassword)
    }.toString()
    val response = sendRequest(ApiEndpoints.USER_PROFILE_UPDATE, "POST", body, token)
    return isApiSuccess(response)
}

/**
 * 搜索用户
 */
suspend fun searchUser(token: String, keyword: String): List<User> {
    val path = "${ApiEndpoints.USER_DETAIL}?keyword=$keyword"
    val response = sendRequest(path, "GET", token = token)
    return parseApiResponseData(response) ?: emptyList()
}

/**
 * 文件上传
 */
suspend fun uploadFile(token: String, fileBytes: ByteArray, fileName: String): String? {
    return uploadFile(ApiEndpoints.FILE_UPLOAD, fileBytes, fileName, token)
}

/**
 * 上传头像
 */
suspend fun uploadAvatar(token: String, fileBytes: ByteArray, fileName: String): String? {
    return uploadFile(ApiEndpoints.USER_AVATAR_UPLOAD, fileBytes, fileName, token)
}

/**
 * AI聊天接口
 */
suspend fun agentChat(token: String, message: String, stream: Boolean = false): String {
    val body = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("message", message)
            put("stream", stream)
        }
    )
    return sendRequest(ApiEndpoints.AGENT_NL, "POST", body, token)
}

// ==================================
// 数据类定义
// ==================================

@Serializable
data class LoginResponse(
    val code: Int,
    val message: String,
    val data: String? = null
)

@Serializable
data class RegisterResponse(
    val code: Int,
    val message: String,
    val data: Int? = null
)

@Serializable
data class UserInfoResponse(
    val code: Int,
    val message: String,
    val data: User? = null
)

@Serializable
data class FriendRequest(
    val id: Int,
    val senderId: Int,
    val senderName: String,
    val senderAvatar: String? = null,
    val message: String,
    val status: Int, // 0:待处理 1:已同意 2:已拒绝
    val createTime: Long
)

/**
 * 消息序列化器，用于从服务端获取历史消息
 */
@Serializable
data class MessageSerializer(
    val id: Int? = null,
    val senderId: Int,
    val receiverId: Int,
    val content: String,
    val timestamp: Long,
    val messageType: String = "TEXT",
    val fileUrl: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val replyToMessageId: String? = null,
    val replyToContent: String? = null,
    val replyToSender: String? = null,
    val messageId: String? = null
)

/**
 * 群消息序列化器
 */
@Serializable
data class GroupMessageSerializer(
    val id: Int? = null,
    val groupId: Int,
    val senderId: Int,
    val senderName: String,
    val content: String,
    val timestamp: Long,
    val messageType: String = "TEXT",
    val fileUrl: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val replyToMessageId: String? = null,
    val replyToContent: String? = null,
    val replyToSender: String? = null,
    val messageId: String? = null
)

/**
 * 转换服务端消息到App使用的Message格式
 */
fun convertMessages(messages: List<MessageSerializer>): List<Message> {
    return messages.map { msg ->
        Message(
            senderId = msg.senderId,
            receiverId = msg.receiverId,
            message = msg.content,
            sender = msg.senderId == ServerConfig.id.toIntOrNull(),
            timestamp = msg.timestamp,
            isSent = mutableStateOf(true),
            messageType = MessageType.valueOf(msg.messageType),
            fileUrl = msg.fileUrl,
            fileName = msg.fileName,
            fileSize = msg.fileSize,
            replyToMessageId = msg.replyToMessageId,
            replyToContent = msg.replyToContent,
            replyToSender = msg.replyToSender,
            messageId = msg.messageId ?: "${msg.senderId}_${msg.receiverId}_${msg.timestamp}"
        )
    }
}

/**
 * 转换服务端群消息到App使用的Message格式
 */
fun convertMessages(messages: List<GroupMessageSerializer>, groupId: Int): List<Message> {
    return messages.map { msg ->
        Message(
            senderId = msg.senderId,
            receiverId = -groupId, // 群消息receiverId用负的groupId表示
            message = msg.content,
            sender = msg.senderId == ServerConfig.id.toIntOrNull(),
            timestamp = msg.timestamp,
            isSent = mutableStateOf(true),
            messageType = MessageType.valueOf(msg.messageType),
            fileUrl = msg.fileUrl,
            fileName = msg.fileName,
            fileSize = msg.fileSize,
            replyToMessageId = msg.replyToMessageId,
            replyToContent = msg.replyToContent,
            replyToSender = msg.replyToSender,
            messageId = msg.messageId ?: "${msg.groupId}_${msg.senderId}_${msg.timestamp}"
        )
    }
}
