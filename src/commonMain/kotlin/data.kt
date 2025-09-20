import androidx.compose.runtime.*
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
data class User(val id: Int, var username: String)
@Serializable
data class Message(
    val id: Int,                // 消息的发送用户
    val text: String,           // 消息内容
    val sender: Boolean,        // 是否是发送者
    val timestamp: Long,        // 消息的时间戳
    var isSent: MutableState<Boolean>,  // 消息是否发送成功
    var messageId: String = ""// 消息ID
) {
    init {
        if (messageId.isEmpty()) {
            val minuteTimestamp = timestamp / 60000 // 转换为分钟级时间戳
            val generatedId = (id.toString() + text.hashCode() + minuteTimestamp).hashCode()
            this.messageId = generatedId.toString()
        }
    }
}
@Serializable
data class GroupMessage(
    val groupId: Int,           // 群组ID
    val senderName: String,     // 发送者名称
    val text: String,           // 消息内容
    val sender: Int,            // 发送者ID
    val timestamp: Long,        // 消息的时间戳
    var isSent: MutableState<Boolean>,  // 消息是否发送成功
    var messageId: String = ""  // 消息ID
) {
    init {
        if (messageId.isEmpty()) {
            val minuteTimestamp = timestamp / 60000 // 转换为分钟级时间戳
            val generatedId = (groupId.toString() + sender.toString() + text.hashCode() + minuteTimestamp).hashCode()
            this.messageId = generatedId.toString()
        }
    }
}
@Serializable
data class Group(val id: Int, val name: String)

fun convertMessages(messages: List<Group>): List<User> {
    return messages.map { message ->
        User(id = -message.id, username = message.name)
    }
}

var users by mutableStateOf(listOf(
    User(1, "Alice"),
    User(2, "Bob"),
    User(3, "Charlie"),
    User(-1, "fucking group")
))

var messages = mutableStateListOf(
    Message(1, "Hello from Alice", false, timestamp = 1698765600000, isSent = mutableStateOf(true)),
    Message(2, "Hello from Bob", false, timestamp = 1698765660000, isSent = mutableStateOf(true)),
    Message(3, "Hello from Charlie", false, timestamp = 1698765720000, isSent = mutableStateOf(true)),
    Message(1, "How are you?", false, timestamp = 1698765780000, isSent = mutableStateOf(true)),
    Message(2, "I'm fine, thanks!", false, timestamp = 1698765840000, isSent = mutableStateOf(true)),
    Message(1, "I'm fine, thanks!", true, timestamp = 1698765900000, isSent = mutableStateOf(false))
)

var groupMessages = mutableStateListOf(
    GroupMessage(1, "Alice", "Hello from Alice", 1, timestamp = 1698765600000, isSent = mutableStateOf(true)),
    GroupMessage(1, "Bob", "Hello from Bob", 2, timestamp = 1698765660000, isSent = mutableStateOf(true)),
    GroupMessage(1, "Charlie", "Hello from Charlie", 3, timestamp = 1698765720000, isSent = mutableStateOf(true)),
    GroupMessage(1, "Alice", "How are you?", 1, timestamp = 1698765780000, isSent = mutableStateOf(true)),
    GroupMessage(1, "Bob", "I'm fine, thanks!", 2, timestamp = 1698765840000, isSent = mutableStateOf(false))
)

/**
 * 获取好友列表
 *
 * @param token 用户的token
 * @return 好友列表
 */
suspend fun fetchFriends(token: String): List<User> {
    return try {
        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://${ServerConfig.SERVER_IP}:${ServerConfig.SPRING_SERVER_PORT}/friend/get"))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build()

        val response = withContext(Dispatchers.IO) {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        }
        val json = Json { ignoreUnknownKeys = true }
        if(response.statusCode() != 200) {
            println("拉取好友失败，状态码：${response.statusCode()}")
            return emptyList()
        }
        println("拉取好友的结果：${response.body()}")
        json.decodeFromString(response.body())
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

/**
 * 获取群组列表
 *
 * @param token 用户的token
 * @return List<User> 返回群组列表
 */
suspend fun fetchGroups(token: String): List<User> {
    return try {
        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://${ServerConfig.SERVER_IP}:${ServerConfig.SPRING_SERVER_PORT}/group/get"))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .build()

        val response = withContext(Dispatchers.IO) {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        }
        val json = Json { ignoreUnknownKeys = true }
        if(response.statusCode() != 200) {
            println("拉取群组失败，状态码：${response.statusCode()}")
            return emptyList()
        }
        println(response.body())
        val groups = json.decodeFromString<List<Group>>(response.body())
        convertMessages(groups)
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

/**
 * 更新好友列表
 *
 * @param token 用户的token
 */
suspend fun updateFriendList(token: String): List<User> {
    val tmp = fetchFriends(token)
    users = users + tmp
    return tmp
}

/**
 * 更新群组列表
 *
 * @param token 用户的token
 */
suspend fun updateGroupList(token: String): List<User> {
    val tmp = fetchGroups(token)
    users = users + tmp
    return fetchGroups(token)
}

/**
 * 更新好友和群组列表
 *
 * @param token 用户的token
 */
suspend fun updateList(token: String): List<User> {
    val friends = fetchFriends(token)
    val groups = fetchGroups(token)
    return friends + groups
}