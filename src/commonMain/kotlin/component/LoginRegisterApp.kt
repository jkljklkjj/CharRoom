package component

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.io.File
import core.ServerConfig
import core.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import model.groupMessages
import model.messages
import model.users

/**
 * 登录或注册界面
 */
@Composable
fun LoginRegisterApp(
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onBackPressed: ((() -> Boolean) -> Unit)? = null
) {
    var isLogin by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") } // 注册模式下用户名
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var res by remember { mutableStateOf(-1) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    // 本地账号输入状态（方式一）
    var account by remember { mutableStateOf(ServerConfig.id) }
    val credentialsFile = remember { File("credentials.txt") }
    var triedAutoLogin by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 只在首次组合时尝试自动读取
    LaunchedEffect(Unit) {
        if (!triedAutoLogin && credentialsFile.exists()) {
            val saved = runCatching { credentialsFile.readLines() }.getOrElse { emptyList() }
            if (saved.isNotEmpty()) {
                account = saved[0]
                message = "已填充上次使用的账号"
            }
            triedAutoLogin = true
        }
    }

    if (isLoggedIn) {
        ChatApp(
            windowSize = DpSize(800.dp, 600.dp),
            token = token,
            isDarkMode = isDarkMode,
            onToggleDarkMode = onToggleDarkMode,
            onLogout = {
                ServerConfig.Token = ""
                token = ""
                isLoggedIn = false
                password = ""
                message = "已退出登录"
                messages.clear()
                groupMessages.clear()
                users = emptyList()
            },
            onBackPressed = onBackPressed
        )
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
                label = { Text(if (isLogin) "账号" else "用户名") },
                enabled = !isSubmitting
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码") },
                visualTransformation = PasswordVisualTransformation(),
                enabled = !isSubmitting
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (isLogin) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rememberMe, onCheckedChange = { rememberMe = it }, enabled = !isSubmitting)
                    Text(text = "记住账号")
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                enabled = !isSubmitting,
                onClick = {
                    if (isSubmitting) return@Button

                    if (isLogin) {
                        val acc = account.trim()
                        val pwd = password.trim()
                        if (acc.isEmpty() || pwd.isEmpty()) {
                            message = "账号和密码不能为空"
                            return@Button
                        }

                        scope.launch {
                            isSubmitting = true
                            message = "正在登录..."

                            val tk = withContext(Dispatchers.IO) { ApiService.login(acc, pwd) }
                            token = tk
                            if (tk.isNotEmpty()) {
                                ServerConfig.Token = tk
                                ServerConfig.id = acc
                                message = "登录成功"
                                if (rememberMe) {
                                    credentialsFile.writeText(acc)
                                } else if (credentialsFile.exists()) {
                                    credentialsFile.delete()
                                }
                                isLoggedIn = true
                            } else {
                                message = "登录失败，请检查账号或密码"
                            }
                            isSubmitting = false
                        }
                    } else {
                        val name = username.trim()
                        val pwd = password.trim()
                        if (name.isEmpty() || pwd.isEmpty()) {
                            message = "用户名和密码不能为空"
                            return@Button
                        }

                        scope.launch {
                            isSubmitting = true
                            message = "正在注册..."

                            res = withContext(Dispatchers.IO) { ApiService.register(name, pwd) }
                            if (res != -1) {
                                account = res.toString()
                                message = "注册成功，您的账号是 $res"
                                isLogin = true
                                password = ""
                            } else {
                                message = "注册失败，请稍后重试"
                            }

                            isSubmitting = false
                        }
                    }
                }
            ) {
                Text(text = if (isSubmitting) "处理中..." else if (isLogin) "登录" else "注册")
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { if (!isSubmitting) isLogin = !isLogin }, enabled = !isSubmitting) {
                Text(text = if (isLogin) "切换到注册" else "切换到登录")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = if (message.contains("成功")) Color(0xFF2E7D32) else MaterialTheme.colors.error
            )
        }
    }
}
