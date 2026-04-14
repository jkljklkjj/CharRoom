package core

import androidx.compose.runtime.mutableStateOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import model.Group
import model.Message
import model.User
import model.convertMessages
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

// 接口路径常量集中管理
object ApiEndpoints {
    // Do not include explicit port in URLs; default HTTP port 80 is implied by scheme.
    private val BASE = "http://${ServerConfig.SERVER_IP}" // 运行期读取 ServerConfig
    const val LOGIN = "/user/login"
    const val REGISTER = "/user/register"
    const val VALIDATE_TOKEN = "/user/validateToken"
    const val FRIEND_GET = "/friend/get"
    const val GROUP_GET = "/group/get"
    const val FRIEND_ADD = "/friend/add"
    const val GROUP_ADD = "/user/addgroup"
    const val USER_DETAIL = "/user/get"          // ?id=xxx
    const val GROUP_DETAIL = "/group/getDetail"  // ?id=xxx
    const val OFFLINE = "/message/getOfflineMessage"
    const val AGENT_NL = "/agent/nl"
    const val AGENT_NL_STREAM = "/agent/nl/stream"

    fun url(path: String): String = BASE + path
}

// 简单 Json 工具（忽略未知字段）
private val json = Json { ignoreUnknownKeys = true }
private val apiHttp = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build()

private fun attachActionHeader(builder: HttpRequest.Builder): HttpRequest.Builder {
    // No longer attach client actions to every request header.
    // Kept as a no-op for backward compatibility with call sites.
    return builder
}

// 新的统一请求发送模板：构建请求、设置公共头、发送并返回响应体
private fun sendRequest(
    path: String,
    method: String = "GET",
    body: String? = null,
    token: String? = null,
    timeoutSeconds: Long = 10
): String {
    val normalizedMethod = method.uppercase()

    // Record outbound API calls for agent context, but skip /agent/nl itself.
    try {
        if (path != ApiEndpoints.AGENT_NL && path != ApiEndpoints.AGENT_NL_STREAM) {
            ActionLogger.log(
                Action(
                    type = ActionType.OTHER,
                    targetId = path,
                    metadata = mapOf(
                        "source" to "sendRequest",
                        "method" to normalizedMethod
                    )
                )
            )
        }
    } catch (_: Exception) {
        // keep existing request behavior
    }

    val builder = HttpRequest.newBuilder()
        .uri(URI.create(ApiEndpoints.url(path)))
        .timeout(Duration.ofSeconds(timeoutSeconds))

    if (!body.isNullOrEmpty()) {
        builder.header("Content-Type", "application/json")
    }
    if (!token.isNullOrEmpty()) {
        builder.header("Authorization", "Bearer $token")
    }

    when (normalizedMethod) {
        "GET" -> builder.GET()
        "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body ?: "{}"))
        else -> builder.method(normalizedMethod, if (body != null) HttpRequest.BodyPublishers.ofString(body) else HttpRequest.BodyPublishers.noBody())
    }

    val request = builder.build()
    return try {
        apiHttp.send(request, HttpResponse.BodyHandlers.ofString()).body()
    } catch (e: Exception) {
        // swallow and return empty string to preserve existing callers' behavior
        ""
    }
}

@Serializable
private data class LoginBody(val id: String, val password: String)
@Serializable
private data class RegisterBody(val username: String, val password: String)
@Serializable
private data class AddFriendBody(val friendId: String)
@Serializable
private data class AddGroupBody(val groupId: String)
@Serializable
private data class AgentActionDto(
    val id: String,
    val timestamp: Long,
    val type: String,
    val targetId: String? = null,
    val metadata: Map<String, String> = emptyMap()
)
@Serializable
private data class AgentRequestBody(val input: String, val clientActions: List<AgentActionDto>? = null)

// 统一 Spring的API 客户端封装
class ApiClient(
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
) {
    private fun buildAgentRequestBody(input: String): String {
        val actions = ActionLogger.getSnapshot().map { a ->
            AgentActionDto(
                id = a.id,
                timestamp = a.timestamp,
                type = a.type.name,
                targetId = a.targetId,
                metadata = a.metadata
            )
        }
        return json.encodeToString(
            AgentRequestBody.serializer(),
            AgentRequestBody(input, if (actions.isEmpty()) null else actions)
        )
    }

    /** 登录，成功返回 token，否则空串 */
    fun login(id: String, password: String): String {
        val bodyJson = json.encodeToString(LoginBody.serializer(), LoginBody(id, password))
        val resp = sendRequest(ApiEndpoints.LOGIN, method = "POST", body = bodyJson, timeoutSeconds = 10)
        return try {
            parseToken(resp)
        } catch (e: Exception) {
            println("遇到错误" + e.message)
            ""
        }
    }

    /** 注册，成功返回账号 id，否则 -1 */
    fun register(username: String, password: String): Int {
        val bodyJson = json.encodeToString(RegisterBody.serializer(), RegisterBody(username, password))
        val resp = sendRequest(ApiEndpoints.REGISTER, method = "POST", body = bodyJson, timeoutSeconds = 20)
        return try {
            parseIntData(resp) ?: -1
        } catch (_: Exception) {
            -1
        }
    }

    /** 验证 token 是否有效（code==0 且 data==true） */
    fun validateToken(token: String = ServerConfig.Token): Boolean {
        val body = sendRequest(ApiEndpoints.VALIDATE_TOKEN, method = "GET", token = token)
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val code = root["code"]?.jsonPrimitive?.intOrNull
            val dataBool = root["data"]?.jsonPrimitive?.booleanOrNull
            code == 0 && dataBool == true
        } catch (_: Exception) {
            false
        }
    }

    /** 获取好友列表 */
    fun fetchFriends(token: String = ServerConfig.Token): List<User> = try {
        val body = sendRequest(ApiEndpoints.FRIEND_GET, method = "POST", body = "{}", token = token)
        parseUserList(body)
    } catch (_: Exception) {
        emptyList()
    }

    /** 获取群组列表 */
    fun fetchGroups(token: String = ServerConfig.Token): List<User> = try {
        val body = sendRequest(ApiEndpoints.GROUP_GET, method = "GET", token = token)
        parseGroupList(body)
    } catch (_: Exception) {
        emptyList()
    }

    /** 添加好友 */
    fun addFriend(friendId: String, token: String = ServerConfig.Token): Boolean {
        val body = json.encodeToString(AddFriendBody.serializer(), AddFriendBody(friendId))
        val resp = sendRequest(ApiEndpoints.FRIEND_ADD, method = "POST", body = body, token = token, timeoutSeconds = 10)
        return interpretBooleanResponse(resp)
    }

    /** 加入群组 */
    fun addGroup(groupId: String, token: String = ServerConfig.Token): Boolean {
        val body = json.encodeToString(AddGroupBody.serializer(), AddGroupBody(groupId))
        val resp = sendRequest(ApiEndpoints.GROUP_ADD, method = "POST", body = body, token = token, timeoutSeconds = 10)
        return interpretBooleanResponse(resp)
    }

    /** 用户详情 */
    fun getUserDetail(userId: String, token: String = ServerConfig.Token): User? {
        val resp = sendRequest(ApiEndpoints.USER_DETAIL + "?id=$userId", method = "GET", token = token, timeoutSeconds = 10)
        return try {
            decodeUserFlexible(resp)
        } catch (_: Exception) {
            null
        }
    }

    /** 群组详情（返回转为 User 供列表复用） */
    fun getGroupDetail(groupId: String, token: String = ServerConfig.Token): User? {
        val resp = sendRequest(ApiEndpoints.GROUP_DETAIL + "?id=$groupId", method = "GET", token = token, timeoutSeconds = 10)
        return try {
            decodeUserFlexible(resp)
        } catch (_: Exception) {
            null
        }
    }

    /** 拉取离线消息，返回 List<Message>，code==0 时有效 */
    fun getOfflineMessages(token: String = ServerConfig.Token): List<Message> {
        return try {
            val body = sendRequest(ApiEndpoints.OFFLINE, method = "GET", token = token, timeoutSeconds = 10)
            val root = json.parseToJsonElement(body).jsonObject
            val code = root["code"]?.jsonPrimitive?.intOrNull
            if (code != 0) {
                return emptyList()
            }
            val dataEl = root["data"] ?: return emptyList()
            val messages = json.decodeFromJsonElement(ListSerializer(Message.serializer()), dataEl)
            messages.forEach { it.isSent = mutableStateOf(true) } // 离线消息均视为已发送
            return messages
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 调用 agent/nl，发送 JSON body，可以包含可选的 clientActions */
    fun callAgent(input: String, token: String = ServerConfig.Token): String {
        return try {
            val bodyJson = buildAgentRequestBody(input)
            val respBody = sendRequest(ApiEndpoints.AGENT_NL, method = "POST", body = bodyJson, token = token, timeoutSeconds = 30)
            // ApiResponse 包装解析（复用 parseToken 风格）
            val root = json.parseToJsonElement(respBody).jsonObject
            val code = root["code"]?.jsonPrimitive?.intOrNull ?: -1
            if (code != 0) return ""
            return root["data"]?.jsonPrimitive?.content ?: ""
        } catch (e: Exception) {
            println("callAgent error: ${e.message}")
            ""
        }
    }

    /**
     * 调用 agent/nl/stream（SSE），按 token 回调并返回完整拼接结果。
     */
    fun callAgentStream(
        input: String,
        token: String = ServerConfig.Token,
        onToken: ((String) -> Unit)? = null
    ): String {
        val collected = StringBuilder()
        try {
            val bodyJson = buildAgentRequestBody(input)
            val builder = HttpRequest.newBuilder()
                .uri(URI.create(ApiEndpoints.url(ApiEndpoints.AGENT_NL_STREAM)))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")

            if (token.isNotEmpty()) {
                builder.header("Authorization", "Bearer $token")
            }

            val request = builder
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                .build()

            val response = http.send(request, HttpResponse.BodyHandlers.ofLines())
            var currentEvent = "message"
            val pendingData = StringBuilder()

            fun flushEvent(): Boolean {
                if (pendingData.isEmpty()) {
                    return false
                }
                val data = pendingData.toString()
                pendingData.clear()

                if (currentEvent == "error") {
                    return true
                }

                if (currentEvent == "done" || data == "[DONE]") {
                    return true
                }

                if (currentEvent == "token" || currentEvent == "message") {
                    if (data.isNotEmpty()) {
                        collected.append(data)
                        onToken?.invoke(data)
                    }
                }
                return false
            }

            response.body().use { lines ->
                val iterator = lines.iterator()
                loop@ while (iterator.hasNext()) {
                    val line = iterator.next()
                    when {
                        line.startsWith("event:") -> {
                            currentEvent = line.removePrefix("event:").trim().ifEmpty { "message" }
                        }

                        line.startsWith("data:") -> {
                            if (pendingData.isNotEmpty()) {
                                pendingData.append('\n')
                            }
                            pendingData.append(line.removePrefix("data:").trimStart())
                        }

                        line.isBlank() -> {
                            if (flushEvent()) {
                                break@loop
                            }
                            currentEvent = "message"
                        }
                    }
                }

                flushEvent()
            }

            return collected.toString()
        } catch (e: Exception) {
            println("callAgentStream error: ${e.message}")
            return collected.toString()
        }
    }

    // 辅助：解析 Date 字符串为毫秒
    private fun parseDateToMillis(dateStr: String): Long {
        return try {
            java.time.OffsetDateTime.parse(dateStr).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.LocalDateTime.parse(dateStr).atZone(java.time.ZoneId.systemDefault()).toInstant()
                    .toEpochMilli()
            } catch (_: Exception) {
                0L
            }
        }
    }

    // ---------------- 私有解析辅助 ----------------
    private fun parseToken(body: String): String {
        val root = json.parseToJsonElement(body).jsonObject
        val code = root["code"]?.jsonPrimitive?.intOrNull ?: -1
        if (code != 0) return ""
        return root["data"]?.jsonPrimitive?.content ?: ""
    }

    private fun parseIntData(body: String): Int? {
        val root = json.parseToJsonElement(body).jsonObject
        val code = root["code"]?.jsonPrimitive?.intOrNull ?: -1
        if (code != 0) return null
        return root["data"]?.jsonPrimitive?.intOrNull
    }

    private fun parseUserList(body: String): List<User> {
        val root = json.parseToJsonElement(body).jsonObject
        val code = root["code"]?.jsonPrimitive?.intOrNull
        if (code != 0) return emptyList()
        val dataEl = root["data"] ?: return emptyList()
        return runCatching {
            json.decodeFromJsonElement(
                ListSerializer(User.serializer()),
                dataEl
            )
        }.getOrElse { emptyList() }
    }

    private fun parseGroupList(body: String): List<User> {
        val root = json.parseToJsonElement(body).jsonObject
        val code = root["code"]?.jsonPrimitive?.intOrNull
        if (code != 0) return emptyList()
        val dataEl = root["data"] ?: return emptyList()
        val groups = runCatching {
            json.decodeFromJsonElement(
                ListSerializer(Group.serializer()),
                dataEl
            )
        }.getOrElse { emptyList() }
        return convertMessages(groups)
    }

    private fun interpretBooleanResponse(body: String): Boolean {
        val trimmed = body.trim()
        if (trimmed.equals("true", true)) return true
        if (trimmed.equals("false", true)) return false
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val code = root["code"]?.jsonPrimitive?.intOrNull
            val dataBool = root["data"]?.jsonPrimitive?.booleanOrNull
            code == 0 && dataBool == true
        }.getOrElse { false }
    }

    private fun decodeUserFlexible(body: String): User? = runCatching {
        val el = json.parseToJsonElement(body)
        if (el is JsonObject && el["code"] != null && el["data"] != null) {
            val dataEl = el["data"]!!
            json.decodeFromJsonElement(User.serializer(), dataEl)
        } else {
            json.decodeFromJsonElement(User.serializer(), el)
        }
    }.getOrNull()
}

object ApiService {
    private val client = ApiClient()
    fun login(id: String, password: String) = client.login(id, password)
    fun register(username: String, password: String) = client.register(username, password)
    fun validateToken(token: String = ServerConfig.Token) = client.validateToken(token)
    fun fetchFriends(token: String = ServerConfig.Token) = client.fetchFriends(token)
    fun fetchGroups(token: String = ServerConfig.Token) = client.fetchGroups(token)
    fun addFriend(friendId: String, token: String = ServerConfig.Token) = client.addFriend(friendId, token)
    fun addGroup(groupId: String, token: String = ServerConfig.Token) = client.addGroup(groupId, token)
    fun getUserDetail(userId: String, token: String = ServerConfig.Token) = client.getUserDetail(userId, token)
    fun getGroupDetail(groupId: String, token: String = ServerConfig.Token) = client.getGroupDetail(groupId, token)
    fun getOfflineMessages(token: String = ServerConfig.Token) = client.getOfflineMessages(token)
    fun callAgent(input: String, token: String = ServerConfig.Token) = client.callAgent(input, token)
    fun callAgentStream(input: String, token: String = ServerConfig.Token, onToken: ((String) -> Unit)? = null) =
        client.callAgentStream(input, token, onToken)
}