package component

import component.chat.ChatApp
import presentation.viewmodel.AuthViewModel
import presentation.viewmodel.GlobalAuthViewModel

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.chatlite.i18n.LocalStrings

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
    var isSuccessMessage by remember { mutableStateOf(false) }

    val s = LocalStrings.current

    // 观察认证状态
    val authState by authViewModel.authState.collectAsState()

    val scope = rememberCoroutineScope()

    // 处理退出登录
    fun handleLogout() {
        authViewModel.logout()
        password = ""
        message = s["login.logged.out"]
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
                message = s["login.account.restored"]
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
                isSuccessMessage = false
                message = when (authState) {
                    is core.state.AuthState.Loading -> s["login.logging.in"]
                    is core.state.AuthState.Error -> (authState as core.state.AuthState.Error).message
                    else -> message
                }
            }

            val isSubmitting = authState is core.state.AuthState.Loading

            Box(
                modifier = Modifier.fillMaxSize().background(immersiveBackgroundBrush(isDarkMode)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 380.dp).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Brand header - just title
                    Text(
                        text = "ChatLite",
                        style = MaterialTheme.typography.h4,
                        color = MaterialTheme.colors.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isLogin) s["login.title"] else s["register.title"],
                        style = MaterialTheme.typography.subtitle1,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // Form card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colors.surface.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(16.dp),
                        elevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            // 账号/用户名
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.04f),
                                shape = RoundedCornerShape(10.dp),
                                elevation = 0.dp
                            ) {
                                TextField(
                                    value = if (isLogin) account else username,
                                    onValueChange = {
                                        if (isLogin) account = it else username = it
                                    },
                                    placeholder = { Text(
                                        if (isLogin) s["login.account"] else s["login.username"],
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                                    ) },
                                    enabled = !isSubmitting,
                                    colors = TextFieldDefaults.textFieldColors(
                                        backgroundColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        cursorColor = MaterialTheme.colors.primary
                                    ),
                                    singleLine = true,
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            // 密码
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.04f),
                                shape = RoundedCornerShape(10.dp),
                                elevation = 0.dp
                            ) {
                                TextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    placeholder = { Text(s["login.password"], color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)) },
                                    visualTransformation = PasswordVisualTransformation(),
                                    enabled = !isSubmitting,
                                    colors = TextFieldDefaults.textFieldColors(
                                        backgroundColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        cursorColor = MaterialTheme.colors.primary
                                    ),
                                    singleLine = true,
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                )
                            }

                            // 注册模式下显示邮箱和验证码
                            if (!isLogin) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.04f),
                                    shape = RoundedCornerShape(10.dp),
                                    elevation = 0.dp
                                ) {
                                    TextField(
                                        value = email,
                                        onValueChange = { email = it },
                                        placeholder = { Text(s["login.email"], color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)) },
                                        enabled = !isSubmitting,
                                        colors = TextFieldDefaults.textFieldColors(
                                            backgroundColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                            cursorColor = MaterialTheme.colors.primary
                                        ),
                                        singleLine = true,
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Email,
                                                contentDescription = null,
                                                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                // 验证码 + 发送按钮
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.04f),
                                        shape = RoundedCornerShape(10.dp),
                                        elevation = 0.dp
                                    ) {
                                        TextField(
                                            value = verifyCode,
                                            onValueChange = { verifyCode = it },
                                            placeholder = { Text(s["login.verify.code"], color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)) },
                                            enabled = !isSubmitting,
                                            colors = TextFieldDefaults.textFieldColors(
                                                backgroundColor = Color.Transparent,
                                                focusedIndicatorColor = Color.Transparent,
                                                unfocusedIndicatorColor = Color.Transparent,
                                                cursorColor = MaterialTheme.colors.primary
                                            ),
                                            singleLine = true,
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.VerifiedUser,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val codeInteraction = remember { MutableInteractionSource() }
                                    val codeScale = rememberElasticScale(codeInteraction)
                                    Button(
                                        onClick = {
                                            if (email.isBlank()) {
                                                message = s["login.please.enter.email"]
                                                return@Button
                                            }
                                            isSendingCode = true
                                            authViewModel.sendRegisterVerifyCode(email) { result ->
                                                result.onSuccess {
                                                    message = s["login.code.sent"]
                                                    countdownTime = 60
                                                    scope.launch {
                                                        while (countdownTime > 0) {
                                                            delay(1000)
                                                            countdownTime--
                                                        }
                                                        isSendingCode = false
                                                    }
                                                }.onFailure {
                                                    message = s["login.code.send.failed"].format(it.message)
                                                    isSendingCode = false
                                                }
                                            }
                                        },
                                        enabled = !isSubmitting && !isSendingCode && countdownTime == 0,
                                        interactionSource = codeInteraction,
                                        modifier = Modifier
                                            .height(48.dp)
                                            .graphicsLayer { scaleX = codeScale; scaleY = codeScale },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.9f),
                                            contentColor = MaterialTheme.colors.onPrimary
                                        )
                                    ) {
                                        Text(
                                            if (countdownTime > 0) s["login.send.code.resend"].format(countdownTime)
                                            else if (isSendingCode) s["login.send.code.sending"]
                                            else s["login.send.code"],
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            // 记住我
                            if (isLogin) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = rememberMe,
                                        onCheckedChange = { rememberMe = it },
                                        enabled = !isSubmitting,
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MaterialTheme.colors.primary
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = s["login.remember.me"],
                                        style = MaterialTheme.typography.body2,
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // 提交按钮
                            val submitInteraction = remember { MutableInteractionSource() }
                            val submitScale = rememberElasticScale(submitInteraction, pressedScale = 0.96f)
                            Button(
                                onClick = {
                                    if (isSubmitting) return@Button
                                    if (isLogin) {
                                        val acc = account.trim()
                                        val pwd = password.trim()
                                        if (acc.isEmpty() || pwd.isEmpty()) {
                                            message = s["login.account.password.empty"]
                                            return@Button
                                        }
                                        authViewModel.login(acc, pwd, rememberMe)
                                    } else {
                                        val name = username.trim()
                                        val pwd = password.trim()
                                        val emailText = email.trim()
                                        val code = verifyCode.trim()
                                        if (name.isEmpty() || pwd.isEmpty()) {
                                            message = s["login.username.password.empty"]
                                            return@Button
                                        }
                                        if (emailText.isEmpty()) {
                                            message = s["login.email.empty"]
                                            return@Button
                                        }
                                        if (code.isEmpty()) {
                                            message = s["login.code.empty"]
                                            return@Button
                                        }
                                        authViewModel.verifyRegister(name, pwd, emailText, code) { result ->
                                            result.onSuccess { accountId ->
                                                account = accountId.toString()
                                                message = s["login.register.success"].format(accountId)
                                                isSuccessMessage = true
                                                isLogin = true
                                                password = ""
                                                email = ""
                                                verifyCode = ""
                                            }.onFailure {
                                                message = s["login.register.failed"].format(it.message ?: s["login.retry.later"])
                                            }
                                        }
                                    }
                                },
                                enabled = !isSubmitting,
                                interactionSource = submitInteraction,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .graphicsLayer { scaleX = submitScale; scaleY = submitScale },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = MaterialTheme.colors.primary,
                                    contentColor = MaterialTheme.colors.onPrimary,
                                    disabledBackgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.45f)
                                )
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colors.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    if (isSubmitting) s["login.processing"]
                                    else if (isLogin) s["login.title"]
                                    else s["register.title"]
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 切换登录/注册
                    val toggleInteraction = remember { MutableInteractionSource() }
                    val toggleScale = rememberElasticScale(toggleInteraction)
                    TextButton(
                        onClick = {
                            if (!isSubmitting) {
                                isLogin = !isLogin
                                if (isLogin) {
                                    email = ""
                                    verifyCode = ""
                                    countdownTime = 0
                                    isSendingCode = false
                                } else {
                                    account = ""
                                }
                                message = ""
                            }
                        },
                        enabled = !isSubmitting,
                        interactionSource = toggleInteraction,
                        modifier = Modifier.graphicsLayer { scaleX = toggleScale; scaleY = toggleScale }
                    ) {
                        Text(
                            text = if (isLogin) s["login.switch.to.register"] else s["register.switch.to.login"],
                            color = MaterialTheme.colors.primary.copy(alpha = 0.8f)
                        )
                    }

                    // 提示消息
                    if (message.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = if (isSuccessMessage) Color(0xFF4CAF50).copy(alpha = 0.1f)
                                    else MaterialTheme.colors.error.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            elevation = 0.dp
                        ) {
                            Text(
                                text = message,
                                color = if (isSuccessMessage) Color(0xFF2E7D32) else MaterialTheme.colors.error,
                                style = MaterialTheme.typography.body2,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
