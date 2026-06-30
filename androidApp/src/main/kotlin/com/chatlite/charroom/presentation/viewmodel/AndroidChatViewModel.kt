package com.chatlite.charroom.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatlite.charroom.data.datasource.remote.AndroidRemoteDataSource
import core.ServerConfig
import data.repository.ChatRepository
import data.repository.GlobalChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import model.Message
import model.MessageIdGenerator
import model.MessageType
import model.User
import model.withAgentAssistant
import presentation.viewmodel.ChatViewModel
import java.util.*

/**
 * Android端特有的ChatViewModel
 * 扩展基础ChatViewModel，添加Android特有的WebSocket连接和消息处理逻辑
 */
class AndroidChatViewModel(
    private val remoteDataSource: AndroidRemoteDataSource,
    chatRepository: ChatRepository = GlobalChatRepository,
    chatState: core.state.ChatState = core.state.GlobalChatState
) : ChatViewModel(chatRepository, chatState) {

    // WebSocket连接状态
    private val _wsConnected = MutableStateFlow(false)
    val wsConnected: StateFlow<Boolean> = _wsConnected.asStateFlow()

    private val _wsConnecting = MutableStateFlow(false)
    val wsConnecting: StateFlow<Boolean> = _wsConnecting.asStateFlow()

    // 当前登录用户ID
    private var currentUserId: Int = 0

    /**
     * 连接WebSocket
     */
    suspend fun connectWebSocket(
        token: String,
        userId: Int,
        onAuthFailed: ((reason: String) -> Unit)? = null
    ): Boolean {
        _wsConnecting.value = true

        val success = remoteDataSource.connectWebSocket(
            token = token,
            ownUserId = userId,
            onMessage = ::handleIncomingMessage,
            onStatusUpdate = ::handleStatusUpdate,
            onAuthFailed = { reason ->
                coroutineScope.launch {
                    _wsConnected.value = false
                    onAuthFailed?.invoke(reason)
                }
            }
        )

        _wsConnected.value = success
        _wsConnecting.value = false

        if (success) {
            currentUserId = userId
            // 加载离线消息
            loadOfflineMessages()
        }

        return success
    }

    /**
     * 发送消息
     */
    fun sendMessage(targetId: Int, content: String, messageType: MessageType = MessageType.TEXT) {
        if (!_wsConnected.value) return

        // 构造消息对象
        val messageId = MessageIdGenerator.generateMessageId(
            senderId = currentUserId,
            content = content,
            timestamp = System.currentTimeMillis()
        )

        val message = Message(
            senderId = currentUserId,
            message = content,
            sender = true,
            receiverId = targetId,
            timestamp = System.currentTimeMillis(),
            isSent = false,
            messageId = messageId,
            messageType = messageType
        )

        // 先添加到本地消息列表
        coroutineScope.launch {
            addMessage(message)
        }

        // 发送消息
        val success = when {
            targetId < 0 -> remoteDataSource.sendGroupMessage(-targetId, content, currentUserId)
            core.ServerConfig.isAgentAssistant(targetId) -> remoteDataSource.sendAgentMessage(targetId, content, currentUserId)
            else -> remoteDataSource.sendMessage(targetId, content, currentUserId)
        }

        // 更新消息发送状态
        coroutineScope.launch {
            updateMessageSentStatus(messageId, success)
        }
    }

    /**
     * 断开WebSocket连接
     */
    fun disconnectWebSocket() {
        remoteDataSource.disconnectWebSocket()
        _wsConnected.value = false
    }

    /**
     * 重新连接
     */
    fun reconnect() {
        remoteDataSource.reconnect()
    }

    /**
     * 网络断开通知
     */
    fun onNetworkDisconnected() {
        remoteDataSource.onNetworkDisconnected()
        _wsConnected.value = false
    }

    /**
     * 确保连接正常
     */
    fun ensureConnected() {
        remoteDataSource.ensureConnected()
    }

    /**
     * 应用退出清理
     */
    suspend fun onAppQuit() {
        remoteDataSource.onAppQuit()
        _wsConnected.value = false
    }

    /**
     * 处理接收到的消息
     */
    private fun handleIncomingMessage(chatMessage: Message) {
        coroutineScope.launch {
            // 确保消息发送者标记正确
            val message = if (chatMessage.senderId == currentUserId) {
                chatMessage.copy(sender = true)
            } else {
                chatMessage.copy(sender = false)
            }

            // 判断是否为Agent流式消息：来自Agent助手且有有效的messageId
            if (message.senderId == ServerConfig.AGENT_ASSISTANT_ID && message.messageId.isNotBlank()) {
                // 使用流式消息专用处理方法，实现内容追加而不是新增消息
                upsertAgentStreamMessage(message.messageId, message.message)
            } else {
                // 普通消息使用常规添加方式
                addMessage(message)
            }
        }
    }

    /**
     * 处理用户在线状态更新
     */
    private fun handleStatusUpdate(clientId: String, online: Boolean) {
        val userId = clientId.toIntOrNull() ?: return
        coroutineScope.launch {
            updateUserOnlineStatus(userId, online)
        }
    }

    /**
     * 加载离线消息
     */
    fun loadOfflineMessages() {
        coroutineScope.launch {
            val messages = chatRepository.getOfflineMessages()
            messages.forEach { addMessage(it) }
        }
    }

    /**
     * 登出
     */
    override fun clear() {
        super.clear()
        disconnectWebSocket()
        remoteDataSource.clearConnectionInfo()
        currentUserId = 0
    }
}
