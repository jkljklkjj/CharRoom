package core
import com.chatlite.proto.MessageProtos

import kotlinx.coroutines.*
import core.state.GlobalAppState
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

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
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 实际传输层
    private val transport = QuicNettyClient()
    private val connected = AtomicBoolean(false)

    // Session 管理: conversationId -> StreamSession
    private val sessions = mutableMapOf<String, StreamSession>()
    private val CONTROL_SESSION_KEY = "__control__"

    // 待发送消息缓冲（断线时缓存）
    private val messageQueue = ConcurrentLinkedQueue<PendingMessage>()

    // 主动停止标志：true 时禁止 onDisconnected 自动重连（logout/stop 场景）
    private val manualStop = AtomicBoolean(false)

    // 消息监听器
    internal val messageListeners = mutableListOf<MessageReceiveListener>()
    internal val authStateListeners = mutableListOf<AuthStateListener>()
    override var agentStreamHandler: AgentStreamHandler? = null

    private val pendingAcks = PendingMessageAcks(schedule = { timeout, action ->
        val task = scope.launch { delay(timeout); action() }
        val cancel: () -> Unit = { task.cancel() }
        cancel
    }, onCallbackFailure = { log.warn("发送确认回调失败", it) })

    private val dispatcher = ProtobufMessageDispatcher(
        listeners = { synchronized(messageListeners) { messageListeners.toList() } },
        agentHandler = { agentStreamHandler },
        onResponse = { response ->
            if (response.success) response.clientId.toIntOrNull()?.takeIf { it > 0 }?.let {
                GlobalAppState.setCurrentUserId(it)
            }
        },
        onAcknowledgement = { ack, bytes -> pendingAcks.acknowledge(ack, bytes) },
        onFailure = { log.warn("消息分发失败", it) }
    )

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
        manualStop.set(false) // 首次连接/手动 reconnect 时允许自动重连

        transport.listener = object : QuicNettyClient.Listener {
            override fun onConnected() {
                connected.set(true)
                scope.launch {
                    try {
                        doLogin()
                        flushPendingMessages()
                        startHeartbeat()
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        log.error("登录流程异常", e)
                    }
                }
            }

            override fun onDisconnected(cause: Throwable?) {
                connected.set(false)
                pendingAcks.failAll()
                log.warn("QUIC 连接断开: ${cause?.message}")
                sessions.clear()
                // 主动停止（logout/stop）时不再自动重连，避免后台偷偷重连
                if (manualStop.get()) return
                // 自动重连：指数退避，最多等 30 秒
                scope.launch {
                    var delayMs = 1000L
                    while (!connected.get() && !manualStop.get()) {
                        log.info("{}ms 后尝试重连...", delayMs)
                        delay(delayMs)
                        if (manualStop.get()) break
                        try {
                            start()
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            log.warn("重连失败: {}", e.message)
                        }
                        // start() 内部会吞掉 connect 异常，不能据此判定成功；
                        // 必须检查 connected 标志（onConnected 同步 set true）才能 break，
                        // 否则 connect 失败时也会误判"重连成功"提前退出循环。
                        if (connected.get()) {
                            log.info("重连成功")
                            break
                        }
                        delayMs = (delayMs * 2).coerceAtMost(30_000L)
                    }
                }
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
        val stream0Id = transport.openStream()
        sessions[CONTROL_SESSION_KEY] = StreamSession(
            streamId = stream0Id,
            conversationId = CONTROL_SESSION_KEY,
            conversationType = StreamSession.ConversationType.CONTROL,
            targetId = ""
        )

        val loginPayload = buildLoginPayload(GlobalAppState.currentToken ?: "")
        val frame = QuicStreamProtocol.encodeFrame(loginPayload)
        transport.send(stream0Id, frame)
        log.info("QUIC 登录请求已发送 (streamId=$stream0Id)")
    }

    // 心跳自适应 RTT 跟踪
    private val rttWindow = mutableListOf<Long>()
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
            delay(interval.milliseconds)
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
                log.warn("心跳发送失败，尝试重建控制流: ${e.message}")
                sessions.remove(CONTROL_SESSION_KEY)
            }
        }
    }

    /**
     * 处理收到的 Stream 数据帧，向上层回调分发。
     */
    private fun handleStreamData(streamId: Long, data: ByteArray) {
        recordRtt()
        dispatcher.dispatch(data) { messageId, conversationId ->
            sendAckToServer(streamId, messageId, conversationId)
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
                .setType(MessageProtos.MessageWrapperType.ACK)
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
        val conversationType = when (type) {
            MsgType.CHAT -> MessageProtos.ConversationType.PRIVATE
            MsgType.GROUP_CHAT -> MessageProtos.ConversationType.GROUP
            MsgType.AGENT_CHAT -> MessageProtos.ConversationType.AGENT
            else -> return getOrCreateControlStream()
        }
        val existing = sessions[targetId]
        if (existing != null && transport.isStreamActive(existing.streamId)) return existing.streamId
        val streamId = transport.openStream()
        sessions[targetId] = StreamSession(
            streamId = streamId,
            conversationId = targetId,
            conversationType = when (conversationType) {
                MessageProtos.ConversationType.GROUP -> StreamSession.ConversationType.GROUP
                MessageProtos.ConversationType.AGENT -> StreamSession.ConversationType.AGENT
                else -> StreamSession.ConversationType.PRIVATE
            },
            targetId = targetId
        )
        val check = MessageProtos.CheckMessage.newBuilder().setConversationType(conversationType)
            .setTargetClientId(targetId.toLong()).build()
        val init = MessageProtos.MessageWrapper.newBuilder().setType(MsgType.CHECK).setCheck(check).build()
        transport.send(streamId, QuicStreamProtocol.encodeFrame(init.toByteArray()))
        return streamId
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
        var messageId: String? = null
        try {
            val wrapper = MessageProtos.MessageWrapper.parseFrom(payload)
            require(wrapper.type == type) { "Message type and encoded payload disagree" }
            messageId = wrapper.deliveryMessageId()
            if (messageId != null) pendingAcks.await(messageId, callback)
            sendMessage(payload, type, targetClientId)
            if (messageId == null) callback(true, emptyList())
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!messageId.isNullOrBlank()) pendingAcks.fail(messageId) else callback(false, emptyList())
        }
    }

    override fun sendText(content: String, callback: (Boolean) -> Unit) {
        // 文本消息由上层构建 protobuf 后通过 send() 发送
        callback(false)
    }

    override fun isConnected(): Boolean = connected.get()

    override fun reconnect() {
        stop()
        start()
    }

    override fun logoutAndDisconnect() {
        val controlStreamId = sessions[CONTROL_SESSION_KEY]?.streamId
        if (controlStreamId != null) {
            val logoutPayload = buildLogoutPayload((GlobalAppState.currentUserId ?: 0).toString())
            val frame = QuicStreamProtocol.encodeFrame(logoutPayload)
            transport.send(controlStreamId, frame)
        }
        stop()
        messageQueue.clear() // 用户主动退出，清空待发送消息
    }

    override fun stop() {
        manualStop.set(true) // 禁止自动重连
        connected.set(false)
        pendingAcks.failAll()
        dispatcher.clear()
        transport.shutdown()
        sessions.clear()
        // 注意：不清理 messageQueue——重连后 flushPendingMessages 会重发这些缓存消息。
        // 用户主动退出（logoutAndDisconnect）时才应清空。
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
