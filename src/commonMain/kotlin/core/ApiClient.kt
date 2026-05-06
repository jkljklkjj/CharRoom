package core

import androidx.compose.runtime.mutableStateOf
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.core.writeFully
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import model.Group
import model.Message
import model.MessageType
import model.User

@Serializable
data class LoginTokenBundle(
    val accessToken: String = "",
    val refreshToken: String = ""
)

/**
 * 通用API响应结构
 */
@Serializable
data class ApiResponse<T>(
    val code: Int = 0,
    val message: String? = null,
    val data: T? = null
) {
    /**
     * 请求是否成功（code == 0）
     */
    val isSuccess: Boolean get() = code == 0
}

// 全局Json配置
val json = Json {
    ignoreUnknownKeys = true // 忽略未知字段
    isLenient = true // 宽松解析
    coerceInputValues = true // 类型不匹配时使用默认值
}

/**
 * 全局共享HttpClient实例
 * 统一配置序列化、超时、拦截器等
 */
val httpClient = HttpClient {
    followRedirects = true
    expectSuccess = false // 不抛出HTTP状态异常，我们自己处理响应

    // 超时配置
    install(HttpTimeout) {
        requestTimeoutMillis = 30000
        connectTimeoutMillis = 10000
        socketTimeoutMillis = 30000
    }

    // JSON序列化配置
    install(ContentNegotiation) {
        json(json)
    }

    // 默认请求头配置
    install(DefaultRequest) {
        header(HttpHeaders.Accept, ContentType.Application.Json.toString())
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
 * 发送HTTP请求，支持自动序列化/反序列化
 * @param path 接口路径
 * @param method 请求方法 GET/POST/PUT/DELETE等
 * @param body 请求体（数据类，自动序列化为JSON）
 * @param token 认证Token
 * @param timeoutSeconds 超时时间（秒）
 * @return 响应ApiResponse<T>
 */
suspend inline fun <reified T> sendRequest(
    path: String,
    method: String = "GET",
    body: Any? = null,
    token: String? = null,
    timeoutSeconds: Long = 30
): ApiResponse<T> {
    val normalizedMethod = HttpMethod.parse(method.uppercase())
    return try {
        val response = httpClient.request(ApiEndpoints.url(path)) {
            this.method = normalizedMethod
            headers {
                token?.let { append(HttpHeaders.Authorization, "Bearer $it") }
                body?.let { append(HttpHeaders.ContentType, ContentType.Application.Json) }
            }

            body?.let {
                when (it) {
                    // JsonElement类型直接转字符串发送，不需要额外序列化
                    is JsonElement -> setBody(it.toString())
                    // 其他类型让Ktor自动序列化
                    else -> setBody(it)
                }
            }

            timeout {
                requestTimeoutMillis = timeoutSeconds * 1000
            }
        }

        when {
            response.status.isSuccess() -> {
                response.body<ApiResponse<T>>()
            }
            else -> {
                ApiResponse(
                    code = response.status.value,
                    message = "HTTP Error: ${response.status.value}"
                )
            }
        }
    } catch (e: Exception) {
        ApiResponse(
            code = -1,
            message = e.message ?: "Unknown error"
        )
    }
}

// 旧的手动JSON解析方法已移除，现在使用Ktor内置的ContentNegotiation自动序列化

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
        val result = response.body<ApiResponse<String>>()
        result.data
    } catch (e: Exception) {
        null
    }
}

/**
 * 登录接口
 */
suspend fun loginTokens(account: String, password: String): LoginTokenBundle? {
    val requestBody = buildJsonObject {
        put("account", account)
        put("password", password)
    }

    val response = sendRequest<LoginTokenBundle>(
        path = ApiEndpoints.LOGIN,
        method = "POST",
        body = requestBody
    )

    if (response.isSuccess && response.data != null && response.data!!.accessToken.isNotBlank()) {
        return response.data
    }

    // 兼容旧后端：data 直接是字符串 token
    val stringResponse = sendRequest<String>(
        path = ApiEndpoints.LOGIN,
        method = "POST",
        body = requestBody
    )

    if (stringResponse.isSuccess && stringResponse.data != null && stringResponse.data!!.isNotBlank()) {
        return LoginTokenBundle(accessToken = stringResponse.data!!, refreshToken = "")
    }

    return null
}

suspend fun login(account: String, password: String): String? {
    return loginTokens(account, password)?.accessToken
}

/**
 * 使用 refresh token 刷新 access token（并轮换 refresh token）
 */
suspend fun refreshTokenBundle(refreshToken: String): LoginTokenBundle? {
    if (refreshToken.isBlank()) return null

    val requestBody = buildJsonObject {
        put("refreshToken", refreshToken)
    }

    val response = sendRequest<LoginTokenBundle>(
        path = ApiEndpoints.REFRESH_TOKEN,
        method = "POST",
        body = requestBody
    )

    return response.data?.takeIf { it.accessToken.isNotBlank() }
}

suspend fun refreshAccessToken(refreshToken: String): String? {
    return refreshTokenBundle(refreshToken)?.accessToken
}

/**
 * 验证token有效性接口，验证成功返回新的token对，验证失败/过期/错误统一返回null
 */
suspend fun validateToken(token: String): LoginTokenBundle? {
    if (token.isBlank()) return null

    val response = sendRequest<LoginTokenBundle>(
        path = ApiEndpoints.VALIDATE_TOKEN,
        method = "GET",
        token = token
    )

    // 只有code为0才认为成功，其他所有情况都返回null
    return response.data?.takeIf { response.isSuccess && it.accessToken.isNotBlank() }
}

/**
 * 注册接口
 */
suspend fun register(username: String, password: String, email: String = ""): Int? {
    val requestBody = buildJsonObject {
        put("username", username)
        put("password", password)
        put("email", email)
    }

    val response = sendRequest<Int>(
        path = ApiEndpoints.REGISTER,
        method = "POST",
        body = requestBody
    )

    return response.data
}

/**
 * 获取用户信息
 */
suspend fun getUserInfo(token: String): User? {
    val response = sendRequest<User>(
        path = ApiEndpoints.USER_PROFILE,
        method = "GET",
        token = token
    )

    return response.data
}

/**
 * 获取好友列表
 */
suspend fun getFriendList(token: String): List<User> {
    val response = sendRequest<List<User>>(
        path = ApiEndpoints.FRIEND_GET,
        method = "POST",
        token = token
    )

    return response.data ?: emptyList()
}

/**
 * 获取群组列表
 */
suspend fun getGroupList(token: String): List<Group> {
    val response = sendRequest<List<Group>>(
        path = ApiEndpoints.GROUP_GET,
        method = "GET",
        token = token
    )

    return response.data ?: emptyList()
}

/**
 * 获取历史消息
 */
suspend fun getHistoryMessages(token: String, userId: Int, page: Int = 1, limit: Int = 50): List<Message> {
    val path = "${ApiEndpoints.OFFLINE}?userId=$userId&page=$page&limit=$limit"

    val response = sendRequest<List<MessageSerializer>>(
        path = path,
        method = "GET",
        token = token
    )

    return response.data?.let { convertMessages(it) } ?: emptyList()
}

/**
 * 获取群历史消息
 */
suspend fun getGroupHistoryMessages(token: String, groupId: Int, page: Int = 1, limit: Int = 50): List<Message> {
    val path = "${ApiEndpoints.OFFLINE}?groupId=$groupId&page=$page&limit=$limit"

    val response = sendRequest<List<GroupMessageSerializer>>(
        path = path,
        method = "GET",
        token = token
    )

    return response.data?.let { convertMessages(it, groupId) } ?: emptyList()
}

/**
 * 发送好友申请
 */
suspend fun sendFriendRequest(token: String, targetUserId: Int, message: String = ""): Boolean {
    val requestBody = buildJsonObject {
        put("targetUserId", targetUserId)
        put("message", message)
    }

    val response = sendRequest<Unit>(
        path = ApiEndpoints.FRIEND_ADD,
        method = "POST",
        body = requestBody,
        token = token
    )

    return response.isSuccess
}

/**
 * 处理好友申请
 */
suspend fun handleFriendRequest(token: String, requestId: Int, accept: Boolean): Boolean {
    val endpoint = if (accept) ApiEndpoints.FRIEND_ACCEPT else ApiEndpoints.FRIEND_REJECT
    val requestBody = buildJsonObject {
        put("requestId", requestId)
    }

    val response = sendRequest<Unit>(
        path = endpoint,
        method = "POST",
        body = requestBody,
        token = token
    )

    return response.isSuccess
}

/**
 * 获取好友申请列表
 */
suspend fun getFriendRequests(token: String): List<FriendRequest> {
    val response = sendRequest<List<FriendRequest>>(
        path = ApiEndpoints.FRIEND_REQUESTS,
        method = "GET",
        token = token
    )

    return response.data ?: emptyList()
}

/**
 * 获取群聊申请列表
 */
suspend fun getGroupRequests(token: String): List<FriendRequest> {
    val response = sendRequest<List<FriendRequest>>(
        path = ApiEndpoints.GROUP_REQUESTS,
        method = "GET",
        token = token
    )

    return response.data ?: emptyList()
}

/**
 * 添加好友
 */
suspend fun addFriend(token: String, account: String): Boolean {
    val requestBody = buildJsonObject {
        put("account", account)
    }

    val response = sendRequest<Unit>(
        path = ApiEndpoints.FRIEND_ADD,
        method = "POST",
        body = requestBody,
        token = token
    )

    return response.isSuccess
}

/**
 * 加入群组
 */
suspend fun addGroup(token: String, groupId: String): Boolean {
    val requestBody = buildJsonObject {
        put("groupId", groupId)
    }

    val response = sendRequest<Unit>(
        path = ApiEndpoints.GROUP_ADD,
        method = "POST",
        body = requestBody,
        token = token
    )

    return response.isSuccess
}

/**
 * 查询用户详情
 */
suspend fun getUserDetail(token: String, userId: String): User? {
    val path = "${ApiEndpoints.USER_DETAIL}?account=$userId"
    val response = sendRequest<User>(
        path = path,
        method = "GET",
        token = token
    )

    return response.data
}

/**
 * 查询群组详情
 */
suspend fun getGroupDetail(token: String, groupId: String): Group? {
    val path = "${ApiEndpoints.GROUP_DETAIL}?id=$groupId"
    val response = sendRequest<Group>(
        path = path,
        method = "GET",
        token = token
    )

    return response.data
}

/**
 * 获取离线消息
 */
suspend fun getOfflineMessages(token: String): List<Message> {
    val response = sendRequest<List<MessageSerializer>>(
        path = ApiEndpoints.OFFLINE,
        method = "GET",
        token = token
    )

    return response.data?.let { convertMessages(it) } ?: emptyList()
}

/**
 * 发送邮箱更新验证码
 */
suspend fun sendEmailUpdateVerifyCode(token: String, email: String): Boolean {
    val requestBody = buildJsonObject {
        put("email", email)
    }

    val response = sendRequest<Unit>(
        path = ApiEndpoints.SEND_EMAIL_UPDATE_VERIFY_CODE,
        method = "POST",
        body = requestBody,
        token = token
    )

    return response.isSuccess
}

/**
 * 更新用户个人资料
 */
suspend fun updateUserProfile(token: String, username: String, phone: String, signature: String, password: String? = null): Boolean {
    val requestBody = buildJsonObject {
        put("username", username)
        put("phone", phone)
        put("signature", signature)
        password?.let { put("password", it) }
    }

    val response = sendRequest<Unit>(
        path = ApiEndpoints.USER_PROFILE_UPDATE,
        method = "POST",
        body = requestBody,
        token = token
    )

    return response.isSuccess
}

/**
 * 更新邮箱
 */
suspend fun updateEmail(token: String, newEmail: String, verifyCode: String): Boolean {
    val requestBody = buildJsonObject {
        put("email", newEmail)
        put("verifyCode", verifyCode)
    }

    val response = sendRequest<Unit>(
        path = ApiEndpoints.USER_PROFILE_UPDATE_EMAIL,
        method = "POST",
        body = requestBody,
        token = token
    )

    return response.isSuccess
}

/**
 * 同意好友申请
 */
suspend fun acceptFriend(token: String, requestId: String): Boolean {
    val requestBody = buildJsonObject {
        put("requestId", requestId)
    }

    val response = sendRequest<Unit>(
        path = ApiEndpoints.FRIEND_ACCEPT,
        method = "POST",
        body = requestBody,
        token = token
    )

    return response.isSuccess
}

/**
 * 拒绝好友申请
 */
suspend fun rejectFriend(token: String, requestId: String): Boolean {
    val requestBody = buildJsonObject {
        put("requestId", requestId)
    }

    val response = sendRequest<Unit>(
        path = ApiEndpoints.FRIEND_REJECT,
        method = "POST",
        body = requestBody,
        token = token
    )

    return response.isSuccess
}

/**
 * 同意群聊申请
 */
suspend fun acceptGroupApplication(token: String, groupId: String, userId: String): Boolean {
    val requestBody = buildJsonObject {
        put("groupId", groupId)
        put("userId", userId)
    }

    val response = sendRequest<Unit>(
        path = ApiEndpoints.GROUP_ACCEPT,
        method = "POST",
        body = requestBody,
        token = token
    )

    return response.isSuccess
}

/**
 * 拒绝群聊申请
 */
suspend fun rejectGroupApplication(token: String, groupId: String, userId: String): Boolean {
    val requestBody = buildJsonObject {
        put("groupId", groupId)
        put("userId", userId)
    }

    val response = sendRequest<Unit>(
        path = ApiEndpoints.GROUP_REJECT,
        method = "POST",
        body = requestBody,
        token = token
    )

    return response.isSuccess
}

/**
 * 创建群组
 */
suspend fun createGroup(token: String, name: String, memberIds: List<Int>): Group? {
    val requestBody = buildJsonObject {
        put("name", name)
        putJsonArray("memberIds") {
            memberIds.forEach { add(it) }
        }
    }

    val response = sendRequest<Group>(
        path = ApiEndpoints.GROUP_ADD,
        method = "POST",
        body = requestBody,
        token = token
    )

    return response.data
}

/**
 * 邀请加入群组
 */
suspend fun inviteToGroup(token: String, groupId: Int, userId: Int): Boolean {
    val requestBody = buildJsonObject {
        put("groupId", groupId)
        put("userId", userId)
    }

    // 暂用现有接口
    val response = sendRequest<Unit>(
        path = ApiEndpoints.GROUP_ADD,
        method = "POST",
        body = requestBody,
        token = token
    )

    return response.isSuccess
}

/**
 * 退出群组
 */
suspend fun leaveGroup(token: String, groupId: Int): Boolean {
    val requestBody = buildJsonObject {
        put("groupId", groupId)
    }

    // 暂用现有接口
    val response = sendRequest<Unit>(
        path = ApiEndpoints.GROUP_GET,
        method = "POST",
        body = requestBody,
        token = token
    )

    return response.isSuccess
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
    val requestBody = buildJsonObject {
        nickname?.let { put("username", it) }
        signature?.let { put("signature", it) }
    }

    val response = sendRequest<Unit>(
        path = ApiEndpoints.USER_PROFILE_UPDATE,
        method = "POST",
        body = requestBody,
        token = token
    )

    return response.isSuccess
}

/**
 * 修改密码
 */
suspend fun changePassword(token: String, oldPassword: String, newPassword: String): Boolean {
    val requestBody = buildJsonObject {
        put("password", newPassword)
    }

    val response = sendRequest<Unit>(
        path = ApiEndpoints.USER_PROFILE_UPDATE,
        method = "POST",
        body = requestBody,
        token = token
    )

    return response.isSuccess
}

/**
 * 搜索用户
 */
suspend fun searchUser(token: String, keyword: String): List<User> {
    val path = "${ApiEndpoints.USER_DETAIL}?keyword=$keyword"
    val response = sendRequest<List<User>>(
        path = path,
        method = "GET",
        token = token
    )

    return response.data ?: emptyList()
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
    val requestBody = buildJsonObject {
        put("message", message)
        put("stream", stream)
    }

    return try {
        val response = httpClient.request(ApiEndpoints.url(ApiEndpoints.AGENT_NL)) {
            method = HttpMethod.Post
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(requestBody)
            timeout {
                requestTimeoutMillis = 30000
            }
        }
        response.bodyAsText()
    } catch (e: Exception) {
        ""
    }
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
