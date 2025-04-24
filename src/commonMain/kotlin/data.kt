import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableStateOf
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
data class User(val id: Int, var username: String)
@Serializable
data class Message(val id: Int, val text: String, val sender: Boolean)
@Serializable
data class GroupMessage(val groupId: Int, val senderName: String, val text: String, val sender: Int)
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
    Message(1, "Hello from Alice", false),
    Message(2, "Hello from Bob", false),
    Message(3, "Hello from Charlie", false),
    Message(1, "How are you?", false),
    Message(2, "I'm fine, thanks!", false),
    Message(1, "I'm fine, thanks!", true)
)

var groupMessages = mutableStateListOf(
    GroupMessage(1, "Alice", "Hello from Alice", 1),
    GroupMessage(1, "Bob", "Hello from Bob", 2),
    GroupMessage(1, "Charlie", "Hello from Charlie", 3),
    GroupMessage(1, "Alice", "How are you?", 1),
    GroupMessage(1, "Bob", "I'm fine, thanks!", 2)
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
    return fetchFriends(token)
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