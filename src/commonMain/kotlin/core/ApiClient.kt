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

// 接口路径常量集中管理
object ApiEndpoints {
    private val BASE = "http://${ServerConfig.SERVER_IP}:${ServerConfig.SPRING_SERVER_PORT}" // 运行期读取 ServerConfig
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

    fun url(path: String): String = BASE + path
}

// 简单 Json 工具（忽略未知字段）
private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class LoginBody(val id: String, val password: String)
@Serializable
private data class RegisterBody(val username: String, val password: String)
@Serializable
private data class AddFriendBody(val friendId: String)
@Serializable
private data class AddGroupBody(val groupId: String)

// 统一 Spring的API 客户端封装
class ApiClient(
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
) {
    /** 登录，成功返回 token，否则空串 */
    fun login(id: String, password: String): String {
        val bodyJson = json.encodeToString(LoginBody.serializer(), LoginBody(id, password))
        val request = HttpRequest.newBuilder()
            .uri(URI.create(ApiEndpoints.url(ApiEndpoints.LOGIN)))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
            .timeout(Duration.ofSeconds(10))
            .build()
        return try {
            parseToken(http.send(request, HttpResponse.BodyHandlers.ofString()).body())
        } catch (e: Exception) {
            println("遇到错误"+e.message)
            ""
        }
    }

    /** 注册，成功返回账号 id，否则 -1 */
    fun register(username: String, password: String): Int {
        val bodyJson = json.encodeToString(RegisterBody.serializer(), RegisterBody(username, password))
        val request = HttpRequest.newBuilder()
            .uri(URI.create(ApiEndpoints.url(ApiEndpoints.REGISTER)))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
            .timeout(Duration.ofSeconds(20))
            .build()
        return try {
            parseIntData(http.send(request, HttpResponse.BodyHandlers.ofString()).body()) ?: -1
        } catch (_: Exception) {
            -1
        }
    }

    /** 验证 token 是否有效（code==0 且 data==true） */
    fun validateToken(token: String = ServerConfig.Token): Boolean {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(ApiEndpoints.url(ApiEndpoints.VALIDATE_TOKEN)))
            .header("Authorization", "Bearer $token")
            .GET()
            .build()
        return try {
            val body = http.send(request, HttpResponse.BodyHandlers.ofString()).body()
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
        val request = HttpRequest.newBuilder()
            .uri(URI.create(ApiEndpoints.url(ApiEndpoints.FRIEND_GET)))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build()
        parseUserList(http.send(request, HttpResponse.BodyHandlers.ofString()).body())
    } catch (_: Exception) {
        emptyList()
    }

    /** 获取群组列表 */
    fun fetchGroups(token: String = ServerConfig.Token): List<User> = try {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(ApiEndpoints.url(ApiEndpoints.GROUP_GET)))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .GET()
            .build()
        parseGroupList(http.send(request, HttpResponse.BodyHandlers.ofString()).body())
    } catch (_: Exception) {
        emptyList()
    }

    /** 添加好友 */
    fun addFriend(friendId: String, token: String = ServerConfig.Token): Boolean {
        val body = json.encodeToString(AddFriendBody.serializer(), AddFriendBody(friendId))
        val req = HttpRequest.newBuilder()
            .uri(URI.create(ApiEndpoints.url(ApiEndpoints.FRIEND_ADD)))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(10))
            .build()
        return try {
            interpretBooleanResponse(http.send(req, HttpResponse.BodyHandlers.ofString()).body())
        } catch (_: Exception) {
            false
        }
    }

    /** 加入群组 */
    fun addGroup(groupId: String, token: String = ServerConfig.Token): Boolean {
        val body = json.encodeToString(AddGroupBody.serializer(), AddGroupBody(groupId))
        val req = HttpRequest.newBuilder()
            .uri(URI.create(ApiEndpoints.url(ApiEndpoints.GROUP_ADD)))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(10))
            .build()
        return try {
            interpretBooleanResponse(http.send(req, HttpResponse.BodyHandlers.ofString()).body())
        } catch (_: Exception) {
            false
        }
    }

    /** 用户详情 */
    fun getUserDetail(userId: String, token: String = ServerConfig.Token): User? {
        val req = HttpRequest.newBuilder()
            .uri(URI.create(ApiEndpoints.url(ApiEndpoints.USER_DETAIL) + "?id=$userId"))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .GET()
            .timeout(Duration.ofSeconds(10))
            .build()
        return try {
            decodeUserFlexible(http.send(req, HttpResponse.BodyHandlers.ofString()).body())
        } catch (_: Exception) {
            null
        }
    }

    /** 群组详情（返回转为 User 供列表复用） */
    fun getGroupDetail(groupId: String, token: String = ServerConfig.Token): User? {
        val req = HttpRequest.newBuilder()
            .uri(URI.create(ApiEndpoints.url(ApiEndpoints.GROUP_DETAIL) + "?id=$groupId"))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .GET()
            .timeout(Duration.ofSeconds(10))
            .build()
        return try {
            decodeUserFlexible(http.send(req, HttpResponse.BodyHandlers.ofString()).body())
        } catch (_: Exception) {
            null
        }
    }

    /** 拉取离线消息，返回 List<Message>，code==0 时有效 */
    fun getOfflineMessages(token: String = ServerConfig.Token): List<Message> {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(ApiEndpoints.url(ApiEndpoints.OFFLINE)))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build()
            val body = http.send(request, HttpResponse.BodyHandlers.ofString()).body()
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
}