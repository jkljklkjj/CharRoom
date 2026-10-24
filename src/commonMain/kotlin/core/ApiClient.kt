package core
import core.AppLogger

import androidx.compose.runtime.mutableStateOf
import core.state.GlobalAppState
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
import core.model.AppVersionInfo
import core.model.VersionCheckResult
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

/** 后端 StatusCode.FORBIDDEN — 群组加入需要管理员审核 */
const val GROUP_JOIN_PENDING_CODE = 1005

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
/**
 * 使用 refresh token 刷新 access token（并轮换 refresh token）
 */
/**
 * 验证token有效性接口，验证成功返回新的token对，验证失败/过期/错误统一返回null
 */
/**
 * 注册接口
 */
/**
 * 验证注册接口（与网页端逻辑一致）
 */
/**
 * 发送注册验证码
 */
/**
 * 获取用户信息
 */
/**
 * 获取好友列表
 */
/**
 * 获取群组列表
 */
/**
 * 获取历史消息
 */
/**
 * 获取群历史消息
 */
/**
 * 发送好友申请
 */
/**
 * 获取 Token 配额信息
 */
/**
 * 获取 Token 定价
 */
/**
 * 购买 Token — 返回下订单结果（含支付二维码/prepayId）
 */
/**
 * 确认支付（测试用 Mock）
 */
/**
 * 删除好友 — DELETE /friends/{friendId}
 */
/**
 * 处理好友申请
 */
/**
 * 获取好友申请列表
 */
/**
 * 获取群聊申请列表
 */
/**
 * 添加好友
 */
/**
 * 加入群组
 * @return 完整响应，包含 code/message/data。code=1005 表示需要审核（群组需管理员批准）
 */
/**
 * 查询用户详情
 */
/**
 * 查询群组详情
 */
/**
 * 获取离线消息
 */
/**
 * 增量同步消息（基于 seqId 游标）。
 * @return SyncMessagesResult 包含消息列表和下一个游标
 */
@Serializable
data class SyncMessagesResult(
    val messages: List<Message> = emptyList(),
    val nextSeqId: Long = 0L,
    val serverSeqId: Long = 0L,
    val hasMore: Boolean = false
)

/**
 * 发送邮箱更新验证码
 */
/**
 * 更新用户个人资料
 */
/**
 * 更新邮箱
 */
/**
 * 同意好友申请
 */
/**
 * 拒绝好友申请
 */
/**
 * 同意群聊申请
 */
/**
 * 拒绝群聊申请
 */
/**
 * 创建群组
 */
/**
 * 邀请加入群组
 */
/**
 * 退出群组
 */
/**
 * 修改用户信息
 */
/**
 * 修改密码
 */
/**
 * 搜索用户
 */
/**
 * 检查应用版本更新
 * @param appVersion 当前应用版本号
 * @param platform 平台：android/desktop/web
 * @param channel 渠道：official/debug等
 */
/**
 * 文件上传
 */
suspend fun uploadFile(token: String, fileBytes: ByteArray, fileName: String): String? {
    return uploadFile(ApiEndpoints.FILE_UPLOAD, fileBytes, fileName, token)
}

/**
 * 上传头像
 */
/**
 * AI聊天接口
 */
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
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
data class FriendRequest(
    val id: Int,
    val senderId: Int = 0,
    @JsonNames("username") val senderName: String = "",
    @JsonNames("avatarUrl")
    val senderAvatar: String? = null,
    @JsonNames("reason") val message: String = "",
    val status: Int = 0, // 0:待处理 1:已同意 2:已拒绝
    val createTime: Long = 0L,
    val avatarKey: String? = null
)

/**
 * 消息序列化器，用于从服务端获取历史消息
 */
@Serializable
data class MessageSerializer(
    val id: Int? = null,
    val senderId: String,
    val receiverId: String,
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
    val senderId: String,
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
        val senderIdInt = msg.senderId.toIntOrNull() ?: 0
        val receiverIdInt = msg.receiverId.toIntOrNull() ?: 0
        Message(
            senderId = senderIdInt,
            receiverId = receiverIdInt,
            message = msg.content,
            sender = msg.senderId == GlobalAppState.currentAccount,
            timestamp = msg.timestamp,
            isSent = true,
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
        val senderIdInt = msg.senderId.toIntOrNull() ?: 0
        Message(
            senderId = senderIdInt,
            receiverId = -groupId, // 群消息receiverId用负的groupId表示
            message = msg.content,
            sender = msg.senderId == GlobalAppState.currentAccount,
            timestamp = msg.timestamp,
            isSent = true,
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

/**
 * 发送私聊消息
 */
/**
 * 发送群聊消息
 */
/**
 * 批量查询用户在线状态。
 * @return userId → online 映射
 */
