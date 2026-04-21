package com.chatlite.charroom

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatAppState(private val repository: NetworkRepository) {
    var screen by mutableStateOf<Screen>(Screen.Login)
    var token by mutableStateOf("")
    var users by mutableStateOf<List<LocalUser>>(emptyList())
    var loadingUsers by mutableStateOf(false)
    var authLoading by mutableStateOf(false)
    var wsConnecting by mutableStateOf(false)
    var wsConnected by mutableStateOf(false)
    var errorMessage by mutableStateOf("")
    var currentUserId by mutableStateOf(0)
    private var accountId by mutableStateOf(0)

    suspend fun login(account: String, password: String) {
        errorMessage = ""
        authLoading = true
        val tokenResult = withContext(Dispatchers.IO) {
            repository.login(account, password)
        }

        if (tokenResult.isNotBlank()) {
            token = tokenResult
            accountId = account.toIntOrNull() ?: 0
            currentUserId = accountId
            loadUserList(token)
            connectWebSocket()
        } else {
            authLoading = false
            errorMessage = "登录失败，请检查账号或密码"
        }
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

        if (loadedUsers.isNotEmpty()) {
            users = loadedUsers
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
            repository.connectWebSocket(token, accountId, { message ->
                handleIncomingMessage(message)
            }, { clientId, online ->
                handleStatusUpdate(clientId, online)
            })
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
        users = emptyList()
        screen = Screen.Login
        currentUserId = 0
        accountId = 0
    }

    fun resetError() {
        errorMessage = ""
    }
}
