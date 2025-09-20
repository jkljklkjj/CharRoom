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
import core.ServerConfig
import core.ApiService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("component.LoginRegisterApp")

@Composable
fun LoginRegisterApp() {
    var isLogin by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") } // 注册模式下用户名
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var res by remember { mutableStateOf(-1) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    // 本地账号输入状态（方式一）
    var account by remember { mutableStateOf(ServerConfig.id) }
    val credentialsFile = remember { File("credentials.txt") }
    var triedAutoLogin by remember { mutableStateOf(false) }

    // 只在首次组合时尝试自动读取
    LaunchedEffect(Unit) {
        if (!triedAutoLogin && credentialsFile.exists()) {
            val saved = runCatching { credentialsFile.readLines() }.getOrElse { emptyList() }
            if (saved.size == 2) {
                account = saved[0]
                password = saved[1]
                message = "Retrieving login history..."
                val tk = ApiService.login(account, password)
                if (tk.isNotEmpty()) {
                    token = tk
                    ServerConfig.id = account // 写回全局
                    message = "Login successful!"
                    isLoggedIn = true
                } else {
                    message = "Please login again"
                }
            }
            triedAutoLogin = true
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
                value = if (isLogin) account else username,
                onValueChange = {
                    if (isLogin) account = it else username = it
                },
                label = { Text(if (isLogin) "账号" else "用户名") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码") },
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (isLogin) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rememberMe, onCheckedChange = { rememberMe = it })
                    Text(text = "Remember me")
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                if (isLogin) {
                    val tk = ApiService.login(account, password)
                    token = tk
                    if (tk.isNotEmpty()) {
                        ServerConfig.id = account
                        message = "Login successful"
                        isLoggedIn = true
                        if (rememberMe) {
                            credentialsFile.writeText("$account\n$password")
                        }
                    } else {
                        message = "Login failed"
                    }
                } else {
                    res = ApiService.register(username, password)
                    if (res != -1) {
                        // 注册成功，把返回的账号赋给本地 account
                        account = res.toString()
                        message = "您的账号为 $res ，请记清楚"
                        isLogin = true
                        // 不立即写 ServerConfig.id，等用户用该账号真正登录后写入
                    } else {
                        message = "Registration failed"
                    }
                }
            }) { Text(text = if (isLogin) "Login" else "Register") }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { isLogin = !isLogin }) {
                Text(text = if (isLogin) "Switch to Register" else "Switch to Login")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = message)
        }
    }
}
