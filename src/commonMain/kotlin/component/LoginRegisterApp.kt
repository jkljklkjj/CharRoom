package component

import component.chat.ChatApp
import presentation.viewmodel.AuthViewModel
import presentation.viewmodel.GlobalAuthViewModel

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 登录或注册界面
 * UI层只负责渲染和事件转发，业务逻辑由AuthViewModel处理
 */
@Composable
fun LoginRegisterApp(
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onBackPressed: ((() -> Boolean) -> Unit)? = null,
    authViewModel: AuthViewModel = GlobalAuthViewModel
) {
    var isLogin by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") } // 注册模式下用户名
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var triedAutoLogin by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") } // 注册模式下邮箱
    var verifyCode by remember { mutableStateOf("") } // 注册模式下验证码
    var isSendingCode by remember { mutableStateOf(false) } // 验证码发送中状态
    var countdownTime by remember { mutableStateOf(0) } // 倒计时秒数

    // 观察认证状态
    val authState by authViewModel.authState.collectAsState()

    val scope = rememberCoroutineScope()

    // 处理退出登录
    fun handleLogout() {
        authViewModel.logout()
        password = ""
        message = "已退出登录"
    }

    // 只在首次组合时初始化ViewModel，尝试自动登录
    LaunchedEffect(Unit) {
        if (!triedAutoLogin) {
            // 自动登录逻辑完全由ViewModel处理
            authViewModel.init()
            triedAutoLogin = true

            // 填充上次使用的账号
            val savedAccount = authViewModel.getCurrentAccount()
            if (!savedAccount.isNullOrBlank()) {
                account = savedAccount
                message = "已填充上次使用的账号"
            }
        }
    }

    // 根据认证状态显示不同界面
    when (authState) {
        is core.state.AuthState.Authenticated -> {
            val authenticatedState = authState as core.state.AuthState.Authenticated
            // 使用BoxWithConstraints跨平台获取屏幕尺寸
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val windowSize = DpSize(
                    width = maxWidth,
                    height = maxHeight
                )

                // 调试日志：输出屏幕尺寸
                LaunchedEffect(Unit) {
                    println("[LoginRegisterApp DEBUG] Screen size: ${windowSize.width} x ${windowSize.height}, isSmallScreen: ${windowSize.width < 600.dp}")
                }

                ChatApp(
                    windowSize = windowSize,
                    token = authenticatedState.accessToken,
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = onToggleDarkMode,
                    onLogout = ::handleLogout,
                    onBackPressed = onBackPressed
                )
            }
        }
        else -> {
            // 根据认证状态更新提示消息
            LaunchedEffect(authState) {
                message = when (authState) {
                    is core.state.AuthState.Loading -> "正在登录..."
                    is core.state.AuthState.Error -> (authState as core.state.AuthState.Error).message
                    else -> message
                }
            }

            val isSubmitting = authState is core.state.AuthState.Loading

            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp).statusBarsPadding(),
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

                // 注册模式下显示邮箱和验证码输入框
                if (!isLogin) {
                    TextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("邮箱") },
                        enabled = !isSubmitting
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = verifyCode,
                            onValueChange = { verifyCode = it },
                            label = { Text("验证码") },
                            enabled = !isSubmitting,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (email.isBlank()) {
                                    message = "请先输入邮箱地址"
                                    return@Button
                                }
                                // 发送验证码
                                isSendingCode = true
                                authViewModel.sendRegisterVerifyCode(email) { result ->
                                    result.onSuccess {
                                        message = "验证码已发送，请注意查收"
                                        // 开始60秒倒计时
                                        countdownTime = 60
                                        scope.launch {
                                            while (countdownTime > 0) {
                                                delay(1000)
                                                countdownTime--
                                            }
                                            isSendingCode = false
                                        }
                                    }.onFailure {
                                        message = "验证码发送失败: ${it.message}"
                                        isSendingCode = false
                                    }
                                }
                            },
                            enabled = !isSubmitting && !isSendingCode && countdownTime == 0,
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text(
                                if (countdownTime > 0) "${countdownTime}s后重新发送"
                                else if (isSendingCode) "发送中..."
                                else "发送验证码"
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

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

                            // 转发登录事件到ViewModel
                            authViewModel.login(acc, pwd, rememberMe)
                        } else {
                            val name = username.trim()
                            val pwd = password.trim()
                            val emailText = email.trim()
                            val code = verifyCode.trim()

                            if (name.isEmpty() || pwd.isEmpty()) {
                                message = "用户名和密码不能为空"
                                return@Button
                            }
                            if (emailText.isEmpty()) {
                                message = "邮箱不能为空"
                                return@Button
                            }
                            if (code.isEmpty()) {
                                message = "验证码不能为空"
                                return@Button
                            }

                            // 转发注册事件到ViewModel，使用verifyRegister接口与网页端逻辑一致
                            authViewModel.verifyRegister(name, pwd, emailText, code) { result ->
                                result.onSuccess { accountId ->
                                    account = accountId.toString()
                                    message = "注册成功，您的账号是 $accountId"
                                    isLogin = true
                                    password = ""
                                    email = ""
                                    verifyCode = ""
                                }.onFailure {
                                    message = "注册失败: ${it.message ?: "请稍后重试"}"
                                }
                            }
                        }
                    }
                ) {
                    Text(text = if (isSubmitting) "处理中..." else if (isLogin) "登录" else "注册")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        if (!isSubmitting) {
                            isLogin = !isLogin
                            // 切换模式时清空相关字段
                            if (isLogin) {
                                // 切换到登录模式，清空注册相关字段
                                email = ""
                                verifyCode = ""
                                countdownTime = 0
                                isSendingCode = false
                            } else {
                                // 切换到注册模式，清空账号字段（注册用username）
                                account = ""
                            }
                            message = ""
                        }
                    },
                    enabled = !isSubmitting
                ) {
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
}
