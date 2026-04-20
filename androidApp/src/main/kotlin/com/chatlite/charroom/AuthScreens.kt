package com.chatlite.charroom

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    isBusy: Boolean,
    errorMessage: String,
    onLoginRequest: (account: String, password: String) -> Unit,
    onRegisterClick: () -> Unit
) {
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf("") }

    ChatUi.AuthCard(title = "登录") {
        ChatUi.AuthInputField(value = account, label = "账号", onValueChange = { account = it })
        Spacer(modifier = Modifier.height(12.dp))
        ChatUi.AuthInputField(
            value = password,
            label = "密码",
            onValueChange = { password = it },
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(modifier = Modifier.height(20.dp))
        ChatUi.PrimaryActionButton(
            text = if (isBusy) "登录中..." else "登录",
            enabled = !isBusy,
            onClick = {
                if (account.isBlank() || password.isBlank()) {
                    inputError = "账号和密码不能为空"
                    return@PrimaryActionButton
                }
                inputError = ""
                onLoginRequest(account.trim(), password.trim())
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChatUi.SecondaryActionText(text = "还没有账号？注册新用户", onClick = onRegisterClick)
        Spacer(modifier = Modifier.height(8.dp))
        ChatUi.FormMessage(inputError)
        ChatUi.FormMessage(errorMessage)
    }
}

@Composable
fun RegisterScreen(
    isBusy: Boolean,
    errorMessage: String,
    onRegisterRequest: (username: String, password: String) -> Unit,
    onBack: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf("") }

    ChatUi.AuthCard(title = "注册新账号") {
        ChatUi.AuthInputField(value = username, label = "用户名", onValueChange = { username = it })
        Spacer(modifier = Modifier.height(12.dp))
        ChatUi.AuthInputField(
            value = password,
            label = "密码",
            onValueChange = { password = it },
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(modifier = Modifier.height(12.dp))
        ChatUi.AuthInputField(
            value = confirmPassword,
            label = "确认密码",
            onValueChange = { confirmPassword = it },
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(modifier = Modifier.height(20.dp))
        ChatUi.PrimaryActionButton(
            text = if (isBusy) "注册中..." else "注册",
            enabled = !isBusy,
            onClick = {
                if (username.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
                    inputError = "请填写完整注册信息"
                    return@PrimaryActionButton
                }
                if (password != confirmPassword) {
                    inputError = "两次输入的密码不一致"
                    return@PrimaryActionButton
                }
                inputError = ""
                onRegisterRequest(username.trim(), password.trim())
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChatUi.SecondaryActionText(text = "返回登录", onClick = onBack)
        Spacer(modifier = Modifier.height(8.dp))
        ChatUi.FormMessage(inputError)
        ChatUi.FormMessage(errorMessage)
    }
}
