import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration

import ServerConfig.id
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.xml.bind.annotation.XmlRootElement

private val logger: Logger = LoggerFactory.getLogger("LoginRegisterApp")
/**
 * 登录注册应用窗口
 */
@Composable
fun LoginRegisterApp() {
    // 用于判断是登录还是注册
    var isLogin by remember { mutableStateOf(true) }
    // 用于存储用户输入的信息
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    // 用于存储是否记住密码
    var rememberMe by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var res by remember { mutableStateOf(-1) }
    // 用于判断是否登录成功
    var isLoggedIn by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }

    // 如果文件有信息，就读取文件中的信息
    val credentialsFile = File("credentials.txt")
    if (credentialsFile.exists()) {
        val savedCredentials = credentialsFile.readLines()
        // 进行自动登录
        if (savedCredentials.size == 2) {
            id = savedCredentials[0]
            password = savedCredentials[1]
            message = "Retrieving login history..."
            // Attempt to log in with saved credentials
            token = login(id, password)
            println(token)
            if (token.isNotEmpty()) {
                message = "Login successful!"
                isLoggedIn = true
            } else {
                message = "Please login again"
            }
        }
    }

    if (isLoggedIn) {
        LaunchedEffect(Unit) {
            updateList(token)
        }
        chatApp(DpSize(800.dp, 600.dp), token)
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = if (isLogin) "登录" else "注册", style = MaterialTheme.typography.h4)
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = if (isLogin) id else username,
                onValueChange = {
                    if (isLogin) {
                        id = it
                    } else {
                        username = it
                    }
                },
                label = { Text(if (isLogin) "账号" else "用户名") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            // 密码框
            TextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码") },
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (isLogin) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = rememberMe,
                        onCheckedChange = { rememberMe = it }
                    )
                    Text(text = "Remember me")
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                if (isLogin) {
                    token = login(id, password)
                    if (token.isNotEmpty()) {
                        message = "Login successful"
                        isLoggedIn = true
                        if (rememberMe) {
                            credentialsFile.writeText("$username\n$password")
                        }
                    } else {
                        message = "Login failed"
                    }
                } else {
                    res = register(RegisterUser(username, password))
                    // 注册成功
                    if (res != -1) {
                        message = "您的账号为 $res ，请记清楚"
                        isLogin = true
                    } else {
                        message = "Registration failed"
                    }
                }
            }) {
                // 如果是登录，按钮显示登录，否则显示注册
                Text(text = if (isLogin) "Login" else "Register")
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { isLogin = !isLogin }) {
                Text(text = if (isLogin) "Switch to Register" else "Switch to Login")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = message)
        }
    }
}

@XmlRootElement
@Serializable
data class RegisterUser(
    val username: String = "",
    val password: String = ""
)

/**
 * 注册用户
 *
 * @param user 要注册的用户信息
 */
fun register(user: RegisterUser): Int {
    println("正在注册：name:${user.username} password:${user.password}")
    return try {
        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()

        // Create JSON request body
        val requestBody = Json.encodeToString(user)

        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://${ServerConfig.SERVER_IP}:${ServerConfig.SPRING_SERVER_PORT}/user/register"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(Duration.ofSeconds(20))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        println(response.body())

        response.body().toInt()
    } catch (e: Exception) {
        e.printStackTrace()
        -1 // Return a default value or handle the error as needed
    }
}

/**
 * 验证 token 是否有效
 *
 * @param token 要验证的 token
 */
fun validateToken(token: String): Boolean {
    println("正在验证token：$token")
    return try {
        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://${ServerConfig.SERVER_IP}:${ServerConfig.SPRING_SERVER_PORT}/user/validateToken"))
            .header("Authorization", "Bearer $token")
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 200) {
            println("token有效")
            true
        } else {
            println("token无效")
            false
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

/**
 * 登录
 *
 * @param id 账号
 * @param password 密码
 */
fun login(id: String, password: String): String {
    println("正在登录：id:$id password:$password")
    return try {
        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()

        val boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
        val requestBody = buildFormDataBody(boundary, mapOf("id" to id, "password" to password))

        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://${ServerConfig.SERVER_IP}:${ServerConfig.SPRING_SERVER_PORT}/user/login"))
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(requestBody)
            .timeout(Duration.ofSeconds(10))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        response.body()
    } catch (e: HttpTimeoutException) {
        logger.error("请求超时，请检查网络连接或服务器状态")
        ""
    } catch (e: Exception) {
        logger.error(e.message.toString())
        "" // Return a default value or handle the error as needed
    }
}

fun buildFormDataBody(boundary: String, data: Map<String, String>): HttpRequest.BodyPublisher {
    val byteArrays = data.flatMap { (key, value) ->
        listOf(
            "--$boundary\r\n".toByteArray(),
            "Content-Disposition: form-data; name=\"$key\"\r\n\r\n".toByteArray(),
            "$value\r\n".toByteArray()
        )
    } + "--$boundary--\r\n".toByteArray()

    return HttpRequest.BodyPublishers.ofByteArrays(byteArrays)
}