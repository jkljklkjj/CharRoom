package core

import kotlinx.coroutines.*
import core.state.GlobalAppState
import core.state.GlobalChatState
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * QUIC 协议客户端实现。
 *
 * 实现 [ChatTransport] 接口，通过 QUIC 自定义 Stream 协议通信。
 * 内部委托给 [QuicNettyClient] 进行底层 QUIC 传输。
 *
 * 流分配策略：
 * - Stream 0: 控制流（登录/登出/心跳）
 * - Stream N (N>=1): 会话流（私聊/群聊/Agent），每个会话独立一条流
 */
class QuicClientImpl : ChatTransport {

    private val log = LoggerFactory.getLogger(QuicClientImpl::class.java)
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 实际传输层
    private val transport = QuicNettyClient()
    private val connected = AtomicBoolean(false)

    // Session 管理: conversationId -> StreamSession（线程安全）
    private val sessions = java.util.concurrent.ConcurrentHashMap<String, StreamSession>()
    private val CONTROL_SESSION_KEY = "__control__"

    // 待发送消息缓冲（断线时缓存）
    private val messageQueue = ConcurrentLinkedQueue<PendingMessage>()

    // 消息去重：服务端未收到 ACK 时会重发，客户端需去重
    private val recentMessageIds = object : LinkedHashMap<String, Boolean>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
            return size > 256
        }
    }

    // 消息监听器
    internal val messageListeners = mutableListOf<MessageReceiveListener>()
    internal val authStateListeners = mutableListOf<AuthStateListener>()

    // 连接配置
    private var host: String = ServerConfig.QUIC_HOST
    private var port: Int = ServerConfig.QUIC_PORT

    data class PendingMessage(
        val payload: ByteArray,
        val type: MsgType,
        val targetClientId: String
    )

    /**
     * 建立 QUIC 连接并启动登录流程。
     */
    override fun start(host: String?, port: Int?) {
        this.host = host ?: ServerConfig.QUIC_HOST
        this.port = port ?: ServerConfig.QUIC_PORT

        transport.listener = object : QuicNettyClient.Listener {
            override fun onConnected() {
                connected.set(true)
                println("[QuicClient] onConnected 回调触发，准备启动登录流程")
                scope.launch {
                    try {
                        doLogin()
                        println("[QuicClient] doLogin 完成")
                        flushPendingMessages()
                        startHeartbeat()
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        println("[QuicClient] 登录流程异常: ${e.message}")
                    }
                }
            }

            override fun onDisconnected(cause: Throwable?) {
                connected.set(false)
                log.warn("QUIC 连接断开: ${cause?.message}")
                sessions.clear()
            }

            override fun onStreamFrame(streamId: Long, data: ByteArray) {
                handleStreamData(streamId, data)
            }

            override fun onError(cause: Throwable) {
                log.error("QUIC 传输错误", cause)
            }
        }

        // 阻塞等待 QUIC 连接建立完成（CLI 期望 start() 是同步的）
        kotlinx.coroutines.runBlocking {
            try {
                transport.connect(this@QuicClientImpl.host, this@QuicClientImpl.port)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                log.error("QUIC 连接失败", e)
            }
        }
    }

    /**
     * 通过 Stream 0 发送登录消息。
     */
    private fun doLogin() {
        println("[QuicClient] doLogin 开始，token长度: ${GlobalAppState.currentToken?.length}")
        val stream0Id = transport.openStream()
        println("[QuicClient] 控制流已打开: streamId=$stream0Id")
        sessions[CONTROL_SESSION_KEY] = StreamSession(
            streamId = stream0Id,
            conversationId = CONTROL_SESSION_KEY,
            conversationType = StreamSession.ConversationType.CONTROL,
            targetId = ""
        )

        val loginPayload = buildLoginPayload(GlobalAppState.currentToken ?: "")
        val frame = QuicStreamProtocol.encodeFrame(loginPayload)
        transport.send(stream0Id, frame)
        println("[QuicClient] QUIC 登录请求已发送 (streamId=$stream0Id)")
        log.info("QUIC 登录请求已发送 (streamId=$stream0Id)")
    }

    // 心跳自适应 RTT 跟踪（线程安全）
    private val rttWindow = java.util.Collections.synchronizedList(mutableListOf<Long>())
    private var lastHeartbeatSend = 0L
    private var hbSendInProgress = false

    /** 自适应心跳间隔：基于 P90 RTT，范围 5-15s（服务端 alive-ttl=30s，留余量）。 */
    private fun adaptiveHbInterval(): Long {
        if (rttWindow.size < 3) return 10_000L
        val sorted = rttWindow.sorted()
        val p90 = sorted[(sorted.size * 0.9).toInt().coerceAtMost(sorted.size - 1)]
        return (p90 * 3).coerceIn(5_000, 15_000)
    }

    /** 收到服务端响应时记录 RTT。 */
    private fun recordRtt() {
        if (!hbSendInProgress) return
        val rtt = System.currentTimeMillis() - lastHeartbeatSend
        if (rtt !in 0..60_000) return
        rttWindow.add(rtt)
        if (rttWindow.size > 10) rttWindow.removeAt(0)
        hbSendInProgress = false
    }

    /**
     * 心跳：自适应间隔（基于 RTT 滑动窗口），服务端 idle timeout=30s。
     * 每次发送前通过 [getOrCreateControlStream] 确保控制流活跃。
     */
    private suspend fun startHeartbeat() {
        while (connected.get()) {
            val interval = adaptiveHbInterval()
            delay(interval)
            if (!connected.get()) break
            try {
                val controlStreamId = getOrCreateControlStream()
                val hbPayload = buildHeartbeatPayload()
                val frame = QuicStreamProtocol.encodeFrame(hbPayload)
                lastHeartbeatSend = System.currentTimeMillis()
                hbSendInProgress = true
                transport.send(controlStreamId, frame)
                log.debug("心跳已发送 (interval={}ms)", interval)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                log.warn("心跳发送失败: ${e.message}")
            }
        }
    }

    /**
     * 处理收到的 Stream 数据帧，向上层回调分发。
     */
    private fun handleStreamData(streamId: Long, data: ByteArray) {
        // 任何服务端响应都可作为 RTT 样本（心跳确认 + 消息推送）
        recordRtt()
        log.info("收到 Stream 数据: streamId={}, dataLen={}, listeners={}", streamId, data.size, messageListeners.size)
        // 数据已由 QuicStreamInitializer 按帧边界返回，直接透传
        synchronized(messageListeners) {
            messageListeners.forEach { listener ->
                try {
                    // 尝试解析为 protobuf MessageWrapper 并分发
                    val wrapper = com.chatlite.proto.MessageProtos.MessageWrapper.parseFrom(data)
                    log.debug("解析消息类型: type={}", wrapper.type)
                    when (wrapper.type) {
                        MsgType.ACK.wire -> {
                            if (wrapper.hasAck()) {
                                val ack = wrapper.ack
                                val ackSeqId = ack.seqId
                                val ackConvId = ack.conversationId
                                // 更新会话 seqId 游标（增量同步用）
                                if (ackConvId.isNotBlank() && ackSeqId > 0) {
                                    scope.launch {
                                        GlobalChatState.updateConversationSeqId(ackConvId, ackSeqId)
                                    }
                                }
                            }
                            return@forEach
                        }
                        MsgType.RESPONSE.wire -> {
                            // 登录响应：提取 clientId 作为当前用户 ID
                            if (wrapper.hasResponse()) {
                                val resp = wrapper.response
                                val clientId = resp.clientId
                                if (resp.success && clientId.isNotBlank()) {
                                    val userId = clientId.toIntOrNull()
                                    if (userId != null && userId > 0) {
                                        GlobalAppState.setCurrentUserId(userId)
                                        log.info("QUIC 登录成功: userId={}", userId)
                                    }
                                }
                            }
                            return@forEach
                        }
                        MsgType.CHAT.wire -> {
                            if (wrapper.hasChat()) {
                                val chat = wrapper.chat
                                if (isDuplicateMessage(chat.messageId)) {
                                    log.debug("重复消息跳过: messageId={}", chat.messageId)
                                    return@forEach
                                }
                                val senderId = chat.userId.toIntOrNull() ?: return@forEach
                                val text = chat.content
                                val ts = chat.timestamp.toLongOrNull() ?: System.currentTimeMillis()
                                val myId = GlobalAppState.currentUserId ?: 0
                                sendAckToServer(streamId, chat.messageId, "${minOf(myId, senderId)}:${maxOf(myId, senderId)}")
                                log.info("分发私聊消息: senderId={}, text={}", senderId, text)
                                listener.onPrivateMessageReceived(senderId, text, ts)
                            }
                        }
                        MsgType.GROUP_CHAT.wire -> {
                            if (wrapper.hasGroupChat()) {
                                val gc = wrapper.groupChat
                                if (isDuplicateMessage(gc.messageId)) {
                                    log.debug("重复群聊消息跳过: messageId={}", gc.messageId)
                                    return@forEach
                                }
                                val groupId = gc.targetClientId.toIntOrNull() ?: return@forEach
                                val senderId = gc.userId.toIntOrNull() ?: return@forEach
                                val senderName = senderId.toString()
                                val text = gc.content
                                val ts = System.currentTimeMillis()
                                sendAckToServer(streamId, gc.messageId, "group:$groupId")
                                log.info("分发群聊消息: groupId={}, senderId={}", groupId, senderId)
                                listener.onGroupMessageReceived(groupId, senderId, senderName, text, ts)
                            }
                        }
                        MsgType.AGENT_CHAT_STREAM.wire -> {
                            if (wrapper.hasAgentStream()) {
                                val stream = wrapper.agentStream
                                val requestId = stream.requestId
                                val isDone = stream.type == com.chatlite.proto.AgentStreamProtos.AgentStreamType.STREAM_DONE
                                val isError = stream.type == com.chatlite.proto.AgentStreamProtos.AgentStreamType.STREAM_ERROR
                                val text = if (stream.hasText()) stream.text else ""

                                // 去重：done 按 requestId，chunk 按 requestId+contentHash
                                val dedupKey = if (isDone) requestId
                                    else "${requestId}:${text.hashCode()}"
                                if (isDuplicateMessage(dedupKey)) {
                                    log.debug("重复 Agent 流跳过: requestId={}, done={}", requestId, isDone)
                                    return@forEach
                                }
                                log.info("分发Agent流式消息: requestId={}, type={}, done={}", requestId, stream.type, isDone)
                                if (isDone) {
                                    sendAckToServer(streamId, requestId, "agent")
                                }

                                // 提取工具调用/结果/用量信息
                                val toolName = if (stream.hasToolCall()) stream.toolCall.name else null
                                val toolResult = if (stream.hasToolResult()) stream.toolResult.result else null
                                val inputTokens = if (stream.hasUsage()) stream.usage.inputTokens else 0
                                val outputTokens = if (stream.hasUsage()) stream.usage.outputTokens else 0

                                listener.onAgentStreamChunk(
                                    messageId = requestId,
                                    fullContent = text,
                                    done = isDone,
                                    error = isError,
                                    streamType = stream.typeValue,
                                    toolName = toolName,
                                    toolResult = toolResult,
                                    inputTokens = inputTokens,
                                    outputTokens = outputTokens
                                )
                            }
                        }
                        MsgType.SYNC_HINT.wire -> {
                            // sync_hint: 服务端通知有新消息，解析 conversationId + seqId
                            if (wrapper.hasAck()) {
                                val ack = wrapper.ack
                                val conversationId = ack.clientId
                                val seqId = ack.message?.toLongOrNull() ?: 0L
                                if (conversationId.isNotBlank() && seqId > 0) {
                                    log.info("收到 sync_hint: conversationId={}, seqId={}", conversationId, seqId)
                                    listener.onSyncHint(conversationId, seqId)
                                }
                            }
                        }
                        else -> {
                            log.warn("未知消息类型: type={}", wrapper.type)
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    log.error("解析 Stream 数据失败: streamId={}, dataLen={}, error={}", streamId, data.size, e.message)
                }
            }
        }
    }

    /**
     * 消息去重：返回 true 表示是重复消息，应跳过。
     */
    private fun isDuplicateMessage(messageId: String): Boolean {
        synchronized(recentMessageIds) {
            if (recentMessageIds.containsKey(messageId)) return true
            recentMessageIds[messageId] = true
            return false
        }
    }

    /**
     * 向服务端发送 ACK，确认消息已收到。
     * 服务端 PendingMessageManager 依赖 ACK 停止重发。
     */
    private fun sendAckToServer(streamId: Long, messageId: String, conversationId: String) {
        try {
            val ackMsg = com.chatlite.proto.MessageProtos.AckMessage.newBuilder()
                .setMessageId(messageId)
                .setSuccess(true)
                .setConversationId(conversationId)
                .build()
            val ackWrapper = com.chatlite.proto.MessageProtos.MessageWrapper.newBuilder()
                .setType(MsgType.ACK.wire)
                .setAck(ackMsg)
                .build()
            val frame = QuicStreamProtocol.encodeFrame(ackWrapper.toByteArray())
            transport.send(streamId, frame)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            log.warn("发送 ACK 失败: messageId={}, error={}", messageId, e.message)
        }
    }

    /**
     * 连接恢复后刷新待发送队列。
     */
    private fun flushPendingMessages() {
        while (messageQueue.isNotEmpty()) {
            val msg = messageQueue.poll() ?: break
            sendMessage(msg.payload, msg.type, msg.targetClientId)
        }
    }

    /**
     * 获取或重建控制流。
     * QUIC stream 在服务端回复后可能被关闭，需要检查实际活跃状态。
     */
    private fun getOrCreateControlStream(): Long {
        val existing = sessions[CONTROL_SESSION_KEY]
        if (existing != null && transport.isStreamActive(existing.streamId)) {
            return existing.streamId
        }
        // 控制流已失效，重建
        log.warn("控制流 {} 已失效，重建中", existing?.streamId)
        val newStreamId = transport.openStream()
        sessions[CONTROL_SESSION_KEY] = StreamSession(
            streamId = newStreamId,
            conversationId = CONTROL_SESSION_KEY,
            conversationType = StreamSession.ConversationType.CONTROL,
            targetId = ""
        )
        return newStreamId
    }

    /**
     * 根据消息类型和目标选择或创建会话流。
     *
     * 流分配规则：
     * - 控制类消息（LOGIN/LOGOUT/HEARTBEAT/CHECK/ACK/RESPONSE）-> 控制流，自动重建
     * - 会话类消息（CHAT/GROUP_CHAT/AGENT_CHAT）-> 按 conversationId 复用或新建流
     */
    private fun getOrCreateStream(type: MsgType, targetId: String): Long {
        return when (type) {
            MsgType.LOGIN, MsgType.LOGOUT, MsgType.HEARTBEAT,
            MsgType.CHECK, MsgType.ACK, MsgType.RESPONSE, MsgType.AGENT_CHAT_STREAM -> {
                getOrCreateControlStream()
            }
            MsgType.CHAT, MsgType.GROUP_CHAT, MsgType.AGENT_CHAT -> {
                val convId = targetId
                val existing = sessions[convId]
                if (existing != null && transport.isStreamActive(existing.streamId)) {
                    existing.streamId
                } else {
                    val newStreamId = transport.openStream()
                    sessions[convId] = StreamSession(
                        streamId = newStreamId,
                        conversationId = convId,
                        conversationType = when (type) {
                            MsgType.CHAT -> StreamSession.ConversationType.PRIVATE
                            MsgType.GROUP_CHAT -> StreamSession.ConversationType.GROUP
                            MsgType.AGENT_CHAT -> StreamSession.ConversationType.AGENT
                        },
                        targetId = targetId
                    )
                    // 发送 Stream 初始化首帧（protobuf CheckMessage，服务端期望此格式）
                    val convType = when (type) {
                        MsgType.CHAT -> "private"
                        MsgType.GROUP_CHAT -> "group"
                        MsgType.AGENT_CHAT -> "agent"
                        else -> "private"
                    }
                    val checkMsg = com.chatlite.proto.MessageProtos.CheckMessage.newBuilder()
                        .setTargetClientId("$convType:$targetId")
                        .build()
                    val initWrapper = com.chatlite.proto.MessageProtos.MessageWrapper.newBuilder()
                        .setType(MsgType.CHECK.wire)
                        .setCheck(checkMsg)
                        .build()
                    val initFrame = QuicStreamProtocol.encodeFrame(initWrapper.toByteArray())
                    transport.send(newStreamId, initFrame)
                    newStreamId
                }
            }
        }
    }

    /**
     * 内部发送，如果未连接则缓存到队列。
     */
    private fun sendMessage(payload: ByteArray, type: MsgType, targetClientId: String) {
        if (!connected.get()) {
            messageQueue.offer(PendingMessage(payload, type, targetClientId))
            return
        }
        val frame = QuicStreamProtocol.encodeFrame(payload)
        val streamId = getOrCreateStream(type, targetClientId)
        transport.send(streamId, frame)
    }

    override fun send(
        payload: ByteArray,
        type: MsgType,
        targetClientId: String,
        expectedResponses: Int,
        callback: (Boolean, List<ByteArray>) -> Unit
    ) {
        sendMessage(payload, type, targetClientId)
        // Phase 1 简化实现：直接回调成功
        callback(true, emptyList())
    }

    override fun sendText(content: String, callback: (Boolean) -> Unit) {
        // 文本消息由上层构建 protobuf 后通过 send() 发送
        callback(true)
    }

    override fun isConnected(): Boolean = connected.get()

    override fun reconnect() {
        stop()
        start()
    }

    override fun logoutAndDisconnect() {
        val controlStreamId = sessions[CONTROL_SESSION_KEY]?.streamId
        if (controlStreamId != null) {
            val logoutPayload = buildLogoutPayload(GlobalAppState.currentToken ?: "")
            val frame = QuicStreamProtocol.encodeFrame(logoutPayload)
            transport.send(controlStreamId, frame)
        }
        stop()
    }

    override fun stop() {
        connected.set(false)
        transport.shutdown()
        sessions.clear()
        messageQueue.clear()
        scope.coroutineContext.cancel()
    }

    override fun addMessageReceiveListener(listener: MessageReceiveListener) {
        synchronized(messageListeners) {
            if (!messageListeners.contains(listener)) {
                messageListeners.add(listener)
            }
        }
    }

    override fun removeMessageReceiveListener(listener: MessageReceiveListener) {
        synchronized(messageListeners) {
            messageListeners.remove(listener)
        }
    }

    override fun addAuthStateListener(listener: AuthStateListener) {
        synchronized(authStateListeners) {
            if (!authStateListeners.contains(listener)) {
                authStateListeners.add(listener)
            }
        }
    }

    override fun removeAuthStateListener(listener: AuthStateListener) {
        synchronized(authStateListeners) {
            authStateListeners.remove(listener)
        }
    }

    override val isServerConnected: Boolean
        get() = connected.get()
}
