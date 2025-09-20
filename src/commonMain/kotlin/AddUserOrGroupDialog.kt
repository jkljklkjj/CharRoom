import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Composable
fun addUserOrGroupDialog(onDismiss: () -> Unit) {
    var account by remember { mutableStateOf("") }
    var isUser by remember { mutableStateOf(true) }
    var responseMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add User or Group") },
        text = {
            Column {
                TextField(
                    value = account,
                    onValueChange = { account = it },
                    label = { Text("Account") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isUser,
                        onClick = { isUser = true }
                    )
                    Text("User")
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(
                        selected = !isUser,
                        onClick = { isUser = false }
                    )
                    Text("Group")
                }
                responseMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = if (it == "添加成功") Color.Green else Color.Red
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                println("正在添加。。。$account")
                val payload = if (isUser) {
                    mapOf("friendId" to account)
                } else {
                    mapOf("groupId" to account)
                }
                val requestBody = jacksonObjectMapper().writeValueAsString(payload)
                val uri = if (isUser) {
                    "http://${ServerConfig.SERVER_IP}:${ServerConfig.SPRING_SERVER_PORT}/friend/add"
                } else {
                    "http://${ServerConfig.SERVER_IP}:${ServerConfig.SPRING_SERVER_PORT}/user/addgroup"
                }
                val client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build()

                val request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .header("Authorization", "Bearer ${ServerConfig.Token}")
                    .timeout(Duration.ofSeconds(10))
                    .build()

                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                responseMessage = when (response.body()) {
                    "true" -> "添加成功"
                    "false" -> "添加失败"
                    else -> response.body()
                }
                if (responseMessage == "添加成功") {
                    val newRequest = if (isUser) {
                        HttpRequest.newBuilder()
                            .uri(URI.create("http://${ServerConfig.SERVER_IP}:${ServerConfig.SPRING_SERVER_PORT}/user/get?id=$account"))
                            .header("Authorization", "Bearer ${ServerConfig.Token}")
                            .header("Content-Type", "application/json")
                            .build()
                    } else {
                        HttpRequest.newBuilder()
                            .uri(URI.create("http://${ServerConfig.SERVER_IP}:${ServerConfig.SPRING_SERVER_PORT}/group/getDetail?id=$account"))
                            .header("Authorization", "Bearer ${ServerConfig.Token}")
                            .header("Content-Type", "application/json")
                            .build()
                    }

                    val detail = client.send(newRequest, HttpResponse.BodyHandlers.ofString())
                    println(detail.body())
                    val json = Json { ignoreUnknownKeys = true }
                    val user = json.decodeFromString<User>(detail.body())
                    val isuser = if (isUser) 1 else -1
                    val updatedUser = user.copy(id = isuser * user.id)
                    users += updatedUser
                }
            }) {
                Text("Add")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

