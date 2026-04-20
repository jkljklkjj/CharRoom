package com.chatlite.charroom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChatTheme {
                ChatApp()
            }
        }
    }
}

@Composable
fun ChatApp() {
    val screenState = remember { mutableStateOf<Screen>(Screen.Login) }
    val tokenState = remember { mutableStateOf("") }
    val usersState = remember { mutableStateOf<List<LocalUser>>(emptyList()) }
    val loadingUsers = remember { mutableStateOf(false) }
    val authLoading = remember { mutableStateOf(false) }
    val errorMessage = remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        when (val screen = screenState.value) {
            is Screen.Login -> LoginScreen(
                isBusy = authLoading.value,
                errorMessage = errorMessage.value,
                onLoginRequest = { account, password ->
                    scope.launch {
                        errorMessage.value = ""
                        authLoading.value = true
                        val token = withContext(Dispatchers.IO) {
                            loginRequest(account, password)
                        }
                        if (token.isNotBlank()) {
                            tokenState.value = token
                            val loaded = withContext(Dispatchers.IO) {
                                fetchFriendAndGroupList(token)
                            }
                            authLoading.value = false
                            loadingUsers.value = false
                            if (loaded.isNotEmpty()) {
                                usersState.value = loaded
                                screenState.value = Screen.Users
                            } else {
                                usersState.value = emptyList()
                                errorMessage.value = "登录成功，但好友/群聊列表为空或拉取失败"
                                screenState.value = Screen.Users
                            }
                        } else {
                            authLoading.value = false
                            errorMessage.value = "登录失败，请检查账号或密码"
                        }
                    }
                },
                onRegisterClick = { screenState.value = Screen.Register }
            )
            is Screen.Register -> RegisterScreen(
                isBusy = authLoading.value,
                errorMessage = errorMessage.value,
                onRegisterRequest = { username, password ->
                    scope.launch {
                        errorMessage.value = ""
                        authLoading.value = true
                        val accountId = withContext(Dispatchers.IO) {
                            registerRequest(username, password)
                        }
                        authLoading.value = false
                        if (accountId != -1) {
                            errorMessage.value = "注册成功，请使用账号登录"
                            screenState.value = Screen.Login
                        } else {
                            errorMessage.value = "注册失败，请稍后重试"
                        }
                    }
                },
                onBack = {
                    errorMessage.value = ""
                    screenState.value = Screen.Login
                }
            )
            is Screen.Users -> UserListScreen(
                users = usersState.value,
                loading = loadingUsers.value,
                error = errorMessage.value,
                onUserClick = { user -> screenState.value = Screen.Chat(user) }
            )
            is Screen.Chat -> ChatScreen(user = screen.user, onBack = { screenState.value = Screen.Users })
        }
    }
}
