package com.chatlite.charroom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

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
    val appState = remember { ChatAppState(NetworkRepository()) }
    val scope = rememberCoroutineScope()

    DisposableEffect(appState) {
        onDispose {
            appState.logout()
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
