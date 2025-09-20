package component

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.io.File

import ServerConfig.id
import org.slf4j.Logger
import org.slf4j.LoggerFactory

// 新增：引入统一封装的 ApiService
private val logger: Logger = LoggerFactory.getLogger("component.LoginRegisterApp")
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

    val credentialsFile = File("credentials.txt")
    if (credentialsFile.exists()) {
        val savedCredentials = runCatching { credentialsFile.readLines() }.getOrElse { emptyList() }
        if (savedCredentials.size == 2) {
            id = savedCredentials[0]
            password = savedCredentials[1]
            message = "Retrieving login history..."
            token = ApiService.login(id, password)
            if (token.isNotEmpty()) {
                message = "Login successful!"
                isLoggedIn = true
            } else {
                message = "Please login again"
            }
        }
    }

    if (isLoggedIn) {
        ChatApp(DpSize(800.dp, 600.dp), token)
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
                    token = ApiService.login(id, password)
                    if (token.isNotEmpty()) {
                        message = "Login successful"
                        isLoggedIn = true
                        if (rememberMe) {
                            // 修正：写入账号而不是 username（注册模式才有 username）
                            credentialsFile.writeText("$id\n$password")
                        }
                    } else {
                        message = "Login failed"
                    }
                } else {
                    res = ApiService.register(username, password)
                    // 注册成功
                    if (res != -1) {
                        message = "您的账号为 $res ，请记清楚"
                        isLogin = true
                        id = res.toString()
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

// 移除：旧的 RegisterUser / register / validateToken / login 实现，改为统一 ApiService

