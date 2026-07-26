package core

/**
 * API 客户端入口
 *
 * 拆分到 core/api/：
 * - AuthApi.kt    认证（登录、注册、Token）
 * - MessageApi.kt 消息（同步、发送、历史）
 * - UserApi.kt    用户（资料、好友、在线状态）
 * - GroupApi.kt   群组（创建、加入、退出）
 * - AgentApi.kt   AI Agent（配额、聊天）
 * - Models.kt     数据模型
 */
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
import kotlinx.serialization.json.*

// ── HTTP 客户端 ─────────────────────────────────────

val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

val httpClient = HttpClient {
    followRedirects = true
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = 30000
        connectTimeoutMillis = 10000
        socketTimeoutMillis = 30000
    }
    install(ContentNegotiation) { json(json) }
    install(DefaultRequest) {
        header(HttpHeaders.Accept, ContentType.Application.Json.toString())
    }
}

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
                    is JsonElement -> setBody(it.toString())
                    else -> setBody(it)
                }
            }
            timeout { requestTimeoutMillis = timeoutSeconds * 1000 }
        }
        when {
            response.status.isSuccess() -> response.body<ApiResponse<T>>()
            else -> ApiResponse(code = response.status.value, message = "HTTP Error: ${response.status.value}")
        }
    } catch (e: Exception) {
        ApiResponse(code = -1, message = e.message ?: "Unknown error")
    }
}

// ── 文件上传 ─────────────────────────────────────

suspend fun uploadFile(path: String, fileBytes: ByteArray, fileName: String, token: String? = null): String? = try {
    val response = httpClient.submitFormWithBinaryData(
        url = ApiEndpoints.url(path),
        formData = formData {
            append("file", fileName, ContentType.Application.OctetStream) { writeFully(fileBytes) }
        }
    ) {
        token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        timeout { requestTimeoutMillis = 30000 }
    }
    if (response.status.isSuccess()) response.body<ApiResponse<String>>().data else null
} catch (_: Exception) { null }

suspend fun uploadFile(token: String, fileBytes: ByteArray, fileName: String) = uploadFile(ApiEndpoints.FILE_UPLOAD, fileBytes, fileName, token)
suspend fun uploadAvatar(token: String, fileBytes: ByteArray, fileName: String) = uploadFile(ApiEndpoints.USER_AVATAR_UPLOAD, fileBytes, fileName, token)

suspend fun checkAppVersion(appVersion: Int, platform: String, channel: String = "official") = sendRequest<core.model.VersionCheckResult>(
    ApiEndpoints.APP_VERSION_CHECK, "POST",
    buildJsonObject { put("versionCode", appVersion); put("platform", platform); put("channel", channel) },
    token = null
).data
