package com.chatlite.charroom.data.network

import com.chatlite.charroom.data.repository.NetworkRepository
import com.chatlite.proto.MessageProtos
import core.AuthStateListener
import core.MessageReceiveListener
import core.MsgType
import core.ChatTransport
import core.state.GlobalAppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import model.Message
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android 平台的全局 WebSocket 适配器。
 * 适配 commonMain 的 Chat 接口到 Android 现有 NetworkRepository 实现。
 */
object AndroidGlobalWebSocketClient : ChatTransport {
    private val repository by lazy { NetworkRepository.getInstance() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val messageListeners = CopyOnWriteArraySet<MessageReceiveListener>()
    private val authStateListeners = CopyOnWriteArraySet<AuthStateListener>()

    @Volatile
    private var connected: Boolean = false

    // 使用原子布尔类型保证并发安全，防止竞态条件
    private val isConnecting = AtomicBoolean(false)

    // 流式输出消息缓存：key=messageId, value=已拼接的完整内容
    private val streamingAgentMessages = ConcurrentHashMap<String, StringBuilder>()
    // 当前活跃的流式会话ID（处理服务端未返回messageId的情况）
    @Volatile
    private var currentStreamSessionId: String? = null

    /**
     * 追加AI助手消息块并返回完整内容
     * 处理服务端未返回messageId的情况，确保同一个流式会话使用相同ID
     */
    private fun appendAgentChunk(messageId: String, chunk: String, isDone: Boolean, isError: Boolean): String {
        // 处理空messageId的情况：使用当前会话ID或创建新的
        val actualMessageId = if (messageId.isBlank()) {
            if (currentStreamSessionId == null) {
                // 新的流式会话开始，生成新ID
                currentStreamSessionId = "agent-stream-${System.currentTimeMillis()}"
            }
            currentStreamSessionId!!
        } else {
            messageId
        }

        // 从缓存中获取或创建StringBuilder
        val contentBuilder = streamingAgentMessages.getOrPut(actualMessageId) {
            StringBuilder()
        }

        // 追加新的内容块
        contentBuilder.append(chunk)
        val fullContent = contentBuilder.toString()

        // 会话结束时清理
        if (isDone || isError) {
            if (actualMessageId == currentStreamSessionId) {
                currentStreamSessionId = null
            }
            streamingAgentMessages.remove(actualMessageId)
        }

        return fullContent
    }

    override fun start(host: String?, port: Int?) {
        val token = GlobalAppState.currentToken
        val userId = GlobalAppState.currentUserId ?: 0
        if (token.isNullOrBlank() || userId == 0) {
            notifyAuthInvalidated("token或用户信息无效")
            return
        }

        // 防止重复连接：已连接直接返回
        if (connected) {
            return
        }

        // 使用CAS原子操作确保只有一个协程能进入连接流程
        if (!isConnecting.compareAndSet(false, true)) {
            // 已有其他协程正在连接，直接返回
            return
        }

        scope.launch {
            try {
                val success = repository.connectWebSocket(
                    token = token,
                    ownUserId = userId,
                    onMessage = ::dispatchIncomingMessage,
                    onStatusUpdate = { _, _ -> },
                    onAuthFailed = { reason ->
                        connected = false
                        notifyAuthInvalidated(reason)
                    }
                )
                connected = success
            } finally {
                // 无论连接成功失败，都重置连接中状态
                isConnecting.set(false)
            }
        }
    }

    override fun stop() {
        repository.disconnectWebSocket()
        connected = false
        isConnecting.set(false)
    }

    override fun send(
        payload: ByteArray,
        type: MsgType,
        targetClientId: String,
        expectedResponses: Int,
        callback: (Boolean, List<ByteArray>) -> Unit
    ) {
        val success = try {
            val wrapper = MessageProtos.MessageWrapper.parseFrom(payload)
            when (type) {
                MsgType.CHAT -> {
                    val chat = wrapper.chat
                    repository.sendMessage(
                        targetId = chat.targetClientId.toIntOrNull() ?: targetClientId.toIntOrNull() ?: 0,
                        content = chat.content,
                        senderId = chat.userId.toIntOrNull() ?: (GlobalAppState.currentUserId ?: 0)
                    )
                }
                MsgType.AGENT_CHAT -> {
                    val chat = wrapper.chat
                    repository.sendAgentMessage(
                        targetId = chat.targetClientId.toIntOrNull() ?: targetClientId.toIntOrNull() ?: 0,
                        content = chat.content,
                        senderId = chat.userId.toIntOrNull() ?: (GlobalAppState.currentUserId ?: 0)
                    )
                }
                MsgType.GROUP_CHAT -> {
                    val group = wrapper.groupChat
                    repository.sendGroupMessage(
                        targetId = group.targetClientId.toIntOrNull() ?: targetClientId.toIntOrNull() ?: 0,
                        content = group.content,
                        senderId = group.userId.toIntOrNull() ?: (GlobalAppState.currentUserId ?: 0)
                    )
                }
                MsgType.CHECK -> {
                    val check = wrapper.check
                    repository.sendCheck(
                        targetId = check.targetClientId.toIntOrNull() ?: targetClientId.toIntOrNull() ?: 0
                    )
                }
                MsgType.LOGOUT -> {
                    val logout = wrapper.logout
                    repository.sendLogout(logout.userId)
                }
                MsgType.HEARTBEAT -> {
                    // 发送心跳包
                    repository.sendHeartbeat()
                }
                MsgType.ACK,
                MsgType.LOGIN,
                MsgType.AGENT_CHAT_STREAM,
                MsgType.RESPONSE -> false
            }
        } catch (_: Exception) {
            false
        }

        callback(success, emptyList())
    }

    override fun sendText(content: String, callback: (Boolean) -> Unit) {
        callback(false)
    }

    override fun isConnected(): Boolean = connected

    override fun reconnect() {
        // 重连时重置连接状态
        connected = false
        isConnecting.set(false)
        repository.reconnect()
    }

    override fun logoutAndDisconnect() {
        val userId = GlobalAppState.currentUserId
        if (userId != null && userId > 0) {
            repository.sendLogout(userId.toString())
        }
        stop()
    }

    override fun addMessageReceiveListener(listener: MessageReceiveListener) {
        messageListeners.add(listener)
    }

    override fun removeMessageReceiveListener(listener: MessageReceiveListener) {
        messageListeners.remove(listener)
    }

    override fun addAuthStateListener(listener: AuthStateListener) {
        authStateListeners.add(listener)
    }

    override fun removeAuthStateListener(listener: AuthStateListener) {
        authStateListeners.remove(listener)
    }

    override val isServerConnected: Boolean
        get() = connected

    private fun dispatchIncomingMessage(message: Message) {
        // 检查是否是流式消息（messageId以agent-stream-开头或发送者是AI助手）
        if (message.messageId.startsWith("agent-stream-") || message.senderId == core.ServerConfig.AGENT_ASSISTANT_ID) {
            // 提取流式消息的元数据
            val isDone = message.message == "[DONE]"
            val isError = message.message.startsWith("[ERROR]")
            val originalMessageId = message.messageId

            val content = when {
                isDone -> ""
                isError -> message.message.removePrefix("[ERROR]")
                else -> message.message
            }

            if (content.isBlank() && !isDone && !isError) {
                return
            }

            // 追加内容并获取实际使用的messageId（处理空ID的情况）
            val fullContent = appendAgentChunk(originalMessageId, content, isDone, isError)
            // 确定实际使用的messageId
            val actualMessageId = if (originalMessageId.isBlank()) {
                currentStreamSessionId ?: originalMessageId
            } else {
                originalMessageId
            }

            // 分发流式回调
            messageListeners.forEach {
                it.onAgentStreamChunk(
                    messageId = actualMessageId,
                    fullContent = fullContent,
                    done = isDone,
                    error = isError
                )
            }

            return
        }

        // 普通消息处理
        if (message.receiverId < 0) {
            val groupId = -message.receiverId
            messageListeners.forEach {
                it.onGroupMessageReceived(
                    groupId = groupId,
                    senderId = message.senderId,
                    senderName = "用户${message.senderId}",
                    message = message.message,
                    timestamp = message.timestamp
                )
            }
            return
        }

        messageListeners.forEach {
            it.onPrivateMessageReceived(
                senderId = message.senderId,
                message = message.message,
                timestamp = message.timestamp
            )
        }
    }

    private fun notifyAuthInvalidated(reason: String) {
        authStateListeners.forEach { it.onAuthInvalidated(reason) }
    }
}
