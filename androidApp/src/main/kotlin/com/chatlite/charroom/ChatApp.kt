package com.chatlite.charroom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.systemBarsPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import core.LocalChatHistoryStore
import core.ServerConfig
import model.messages
import model.groupMessages

@Composable
fun ChatApp(
    appState: ChatAppState,
    onBackPressed: (((() -> Boolean)?) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
                    is Screen.Chat,
                    is Screen.Profile,
                    is Screen.UserDetail -> {
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

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.primary)) {
        Surface(
            color = MaterialTheme.colors.background,
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            when (val screen = appState.screen) {
                is Screen.Login -> LoginScreen(
                    isBusy = appState.authLoading,
                    errorMessage = appState.errorMessage,
                    onLoginRequest = { account, password ->
                        scope.launch {
                            appState.login(account, password) { token, refreshToken, accountId ->
                                AndroidTokenStorage.save(context, token, accountId, refreshToken)
                            }
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
                    onUserClick = { user -> appState.screen = Screen.Chat(user) },
                    onUserDetailClick = { user -> appState.screen = Screen.UserDetail(user) },
                    onProfileClick = { appState.screen = Screen.Profile }
                )
                is Screen.Chat -> ChatScreen(
                    user = screen.user,
                    appState = appState,
                    currentUserAvatar = appState.currentUserAvatar,
                    onBack = { appState.screen = Screen.Users },
                    onSend = { message ->
                        scope.launch {
                            appState.sendMessage(screen.user.id, message)
                        }
                    }
                )
                is Screen.Profile -> ProfileScreen(
                    token = appState.token,
                    currentUserId = appState.currentUserId,
                    onBack = { appState.screen = Screen.Users },
                    onLogout = {
                        AndroidTokenStorage.clear(context)
                        appState.logout()
                    }
                )
                is Screen.UserDetail -> UserDetailScreen(
                    user = screen.user,
                    token = appState.token,
                    onBack = { appState.screen = Screen.Users }
                )
            }
        }
    }

    // Android端：监听全局消息列表并自动保存到本地（防止历史丢失）
    @OptIn(FlowPreview::class)
    LaunchedEffect(Unit) {
        snapshotFlow { messages.toList() to groupMessages.toList() }
            .distinctUntilChanged()
            .debounce(350)
            .collect { (privateMsgs, groupMsgs) ->
                if (ServerConfig.id.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        try {
                            LocalChatHistoryStore.save(ServerConfig.id, privateMsgs, groupMsgs)
                        } catch (_: Exception) {
                        }
                    }
                }
            }
    }
}
