package com.chatlite.charroom

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

@Composable
fun ChatApp(
    appState: ChatAppState = remember { ChatAppState(NetworkRepository.getInstance()) },
    onBackPressed: (((() -> Boolean)?) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()

    DisposableEffect(appState) {
        onDispose {
            appState.logout()
        }
    }

    // 处理返回键逻辑
    DisposableEffect(onBackPressed, appState.screen) {
        onBackPressed?.let { setCallback ->
            setCallback {
                when (appState.screen) {
                    is Screen.Register -> {
                        appState.resetError()
                        appState.screen = Screen.Login
                        true
                    }
                    is Screen.Chat -> {
                        appState.screen = Screen.Users
                        true
                    }
                    else -> false // 其他页面让系统处理返回（退出应用）
                }
            }
        }
        onDispose {
            onBackPressed?.let { setCallback ->
                setCallback(null)
            }
        }
    }

    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        when (val screen = appState.screen) {
            is Screen.Login -> LoginScreen(
                isBusy = appState.authLoading,
                errorMessage = appState.errorMessage,
                onLoginRequest = { account, password ->
                    scope.launch {
                        appState.login(account, password)
                    }
                },
                onRegisterClick = {
                    appState.resetError()
                    appState.screen = Screen.Register
                }
            )
            is Screen.Register -> RegisterScreen(
                isBusy = appState.authLoading,
                errorMessage = appState.errorMessage,
                onRegisterRequest = { username, password ->
                    scope.launch {
                        appState.register(username, password)
                    }
                },
                onBack = {
                    appState.resetError()
                    appState.screen = Screen.Login
                }
            )
            is Screen.Users -> UserListScreen(
                users = appState.users,
                loading = appState.loadingUsers,
                error = appState.errorMessage,
                onUserClick = { user -> appState.screen = Screen.Chat(user) }
            )
            is Screen.Chat -> ChatScreen(
                user = screen.user,
                appState = appState,
                onBack = { appState.screen = Screen.Users },
                onSend = { message ->
                    scope.launch {
                        appState.sendMessage(screen.user.id, message)
                    }
                }
            )
        }
    }
}
