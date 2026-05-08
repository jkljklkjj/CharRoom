package com.chatlite.charroom

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatAppState(
    private val repository: NetworkRepository,
    private val coroutineScope: CoroutineScope,
    private val onAuthFailed: (() -> Unit)? = null
) {
    var screen by mutableStateOf<Screen>(Screen.Login)
    var token by mutableStateOf("")
    var users by mutableStateOf<List<LocalUser>>(emptyList())
    var loadingUsers by mutableStateOf(false)
    var authLoading by mutableStateOf(false)
    var wsConnecting by mutableStateOf(false)
    var wsConnected by mutableStateOf(false)
    var errorMessage by mutableStateOf("")
    var currentUserId by mutableStateOf(0)
    var currentUserAvatar: String? by mutableStateOf(null) // 当前登录用户的头像
    var refreshToken by mutableStateOf("")
    private var accountId by mutableStateOf(0)

    suspend fun login(
        account: String,
        password: String,
        saveToken: ((token: String, refreshToken: String, accountId: Int) -> Unit)? = null
    ): Boolean {
        errorMessage = ""
        authLoading = true
        val loginResult = withContext(Dispatchers.IO) {
            repository.login(account, password)
        }

        if (loginResult != null && loginResult.accessToken.isNotBlank()) {
            token = loginResult.accessToken
            refreshToken = loginResult.refreshToken
            accountId = account.toIntOrNull() ?: 0
            currentUserId = accountId

            // 拉取当前用户详情，保存头像
            val currentUser = withContext(Dispatchers.IO) {
                repository.getUserDetail(accountId.toString(), token)
            }
            currentUserAvatar = currentUser?.avatarUrl

            loadUserList(token)
            connectWebSocket()
            saveToken?.invoke(token, refreshToken, accountId)
            authLoading = false
            return true
        } else {
            authLoading = false
            errorMessage = "登录失败，请检查账号或密码"
            return false
        }
    }

    suspend fun tryRestoreSession(token: String, accountId: Int, refreshToken: String = ""): Boolean {
        errorMessage = ""
        authLoading = true
        var activeToken = token
        var activeRefreshToken = refreshToken

        var validBundle = withContext(Dispatchers.IO) {
            repository.validateToken(token)
        }

        if (validBundle != null) {
            // 验证成功，使用新的token
            activeToken = validBundle.accessToken
            activeRefreshToken = validBundle.refreshToken
        } else if (refreshToken.isNotBlank()) {
            val refreshed = withContext(Dispatchers.IO) {
                repository.refreshAccessToken(refreshToken)
            }
            if (refreshed != null && refreshed.accessToken.isNotBlank()) {
                activeToken = refreshed.accessToken
                activeRefreshToken = refreshed.refreshToken
                validBundle = refreshed
            }
        }

        if (validBundle == null) {
            authLoading = false
            return false
        }

        this.token = activeToken
        this.refreshToken = activeRefreshToken
        this.accountId = accountId
        this.currentUserId = accountId

        // 拉取当前用户详情，保存头像
        val currentUser = withContext(Dispatchers.IO) {
            repository.getUserDetail(accountId.toString(), activeToken)
        }
        currentUserAvatar = currentUser?.avatarUrl

        loadUserList(activeToken)
        connectWebSocket()
        authLoading = false
        return wsConnected
    }

    suspend fun register(username: String, password: String) {
        errorMessage = ""
        authLoading = true
        val accountId = withContext(Dispatchers.IO) {
            repository.register(username, password)
        }
        authLoading = false
        if (accountId != -1) {
            errorMessage = "注册成功，请使用账号登录"
            screen = Screen.Login
        } else {
            errorMessage = "注册失败，请稍后重试"
        }
    }

    suspend fun sendMessage(targetId: Int, message: String) {
        if (message.isBlank()) return
        if (!wsConnected) {
            errorMessage = "WebSocket 未连接，消息发送失败"
            return
        }
        withContext(Dispatchers.IO) {
            val success = when {
                targetId < 0 -> repository.sendGroupMessage(-targetId, message, accountId)
                core.ServerConfig.isAgentAssistant(targetId) -> repository.sendAgentMessage(targetId, message, accountId)
                else -> repository.sendChatMessage(targetId, message, accountId)
            }
            if (!success) {
                errorMessage = "消息发送失败，请重试"
            }
        }
    }

    private suspend fun loadUserList(token: String) {
        loadingUsers = true
        val loadedUsers = withContext(Dispatchers.IO) {
            repository.fetchFriendAndGroupList(token)
        }
        loadingUsers = false
        authLoading = false

        // 添加AI助手到好友列表最前面（如果不存在）
        val usersWithAgent = if (!loadedUsers.any { it.id == core.ServerConfig.AGENT_ASSISTANT_ID }) {
            listOf(
                LocalUser(
                    id = core.ServerConfig.AGENT_ASSISTANT_ID,
                    username = core.ServerConfig.AGENT_ASSISTANT_NAME,
                    online = true,
                    avatarUrl = null,
                    signature = "智能聊天助手",
                    isGroup = false
                )
            ) + loadedUsers
        } else {
            loadedUsers
        }

        if (usersWithAgent.isNotEmpty()) {
            users = usersWithAgent
            screen = Screen.Users
        } else {
            users = emptyList()
            errorMessage = "登录成功，但好友/群聊列表为空或拉取失败"
            screen = Screen.Users
        }
    }

    private suspend fun connectWebSocket() {
        wsConnecting = true
        wsConnected = withContext(Dispatchers.IO) {
            repository.connectWebSocket(
                token = token,
                ownUserId = accountId.toInt(),
                onMessage = { message ->
                    handleIncomingMessage(message)
                },
                onStatusUpdate = { clientId, online ->
                    handleStatusUpdate(clientId, online)
                },
                onAuthFailed = { reason ->
                    // 认证失败，清除本地凭证并跳回登录页
                    coroutineScope.launch(Dispatchers.Main) {
                        repository.clearConnectionInfo()
                        logout()
                        errorMessage = reason
                        onAuthFailed?.invoke()
                    }
                }
            )
        }
        wsConnecting = false
        if (!wsConnected) {
            errorMessage = "WebSocket 连接失败，聊天功能可能受限"
        } else {
            refreshFriendStatus() // trigger status checks after successful connection
        }
    }

    private fun handleIncomingMessage(message: ChatMessage) {
        currentChatReceiver?.invoke(message)
    }

    private fun handleStatusUpdate(clientId: String, online: Boolean) {
        val userId = clientId.toIntOrNull() ?: return
        val updated = users.map { user ->
            if (user.id == userId) user.copy(online = online) else user
        }
        if (updated != users) {
            users = updated
        }
    }

    private suspend fun refreshFriendStatus() {
        val friendIds = users.filter { it.id > 0 && !core.ServerConfig.isAgentAssistant(it.id) }.map { it.id }
        withContext(Dispatchers.IO) {
            friendIds.forEach { repository.sendCheck(it) }
        }
    }

    private var currentChatReceiver: ((ChatMessage) -> Unit)? = null

    fun registerChatReceiver(receiver: (ChatMessage) -> Unit) {
        currentChatReceiver = receiver
    }

    fun unregisterChatReceiver() {
        currentChatReceiver = null
    }

    fun logout() {
        if (wsConnected) {
            repository.sendLogout(accountId.toString())
        }
        repository.disconnectWebSocket()
        wsConnected = false
        token = ""
        refreshToken = ""
        users = emptyList()
        screen = Screen.Login
        currentUserId = 0
        accountId = 0
    }

    fun resetError() {
        errorMessage = ""
    }
}
