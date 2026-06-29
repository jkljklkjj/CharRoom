package core

import kotlinx.coroutines.*
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
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 实际传输层
    private val transport = QuicNettyClient()
    private val connected = AtomicBoolean(false)

    // Session 管理: conversationId -> StreamSession
    private val sessions = mutableMapOf<String, StreamSession>()
    private val CONTROL_SESSION_KEY = "__control__"

    // 待发送消息缓冲（断线时缓存）
    private val messageQueue = ConcurrentLinkedQueue<PendingMessage>()

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
                scope.launch {
                    doLogin()
                    flushPendingMessages()
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

        val loginPayload = buildLoginPayload(ServerConfig.Token)
        val frame = QuicStreamProtocol.encodeFrame(loginPayload)
        transport.send(stream0Id, frame)
        log.info("QUIC 登录请求已发送 (streamId=$stream0Id)")
    }

    /**
     * 处理收到的 Stream 数据帧，向上层回调分发。
     */
    private fun handleStreamData(streamId: Long, data: ByteArray) {
        // 数据已由 QuicStreamInitializer 按帧边界返回，直接透传
        synchronized(messageListeners) {
            messageListeners.forEach { listener ->
                try {
                    // 尝试解析为 protobuf MessageWrapper 并分发
                    val wrapper = com.chatlite.proto.MessageProtos.MessageWrapper.parseFrom(data)
                    when (wrapper.type) {
                        MsgType.ACK.wire -> {
                            if (wrapper.hasAck()) {
                                val ack = wrapper.ack
                                val ackSeqId = ack.seqId
                                val ackConvId = ack.conversationId
                                // 更新会话 seqId 游标（增量同步用）
                                if (ackConvId.isNotBlank() && ackSeqId > 0) {
                                    GlobalChatState.updateConversationSeqId(ackConvId, ackSeqId)
                                }
                            }
                            return@forEach
                        }
                        MsgType.CHAT.wire -> {
                            if (wrapper.hasChat()) {
                                val chat = wrapper.chat
                                val senderId = chat.userId.toIntOrNull() ?: return@forEach
                                val text = chat.content
                                val ts = chat.timestamp.toLongOrNull() ?: System.currentTimeMillis()
                                listener.onPrivateMessageReceived(senderId, text, ts)
                            }
                        }
                        MsgType.GROUP_CHAT.wire -> {
                            if (wrapper.hasGroupChat()) {
                                val gc = wrapper.groupChat
                                val groupId = gc.targetClientId.toIntOrNull() ?: return@forEach
                                val senderId = gc.userId.toIntOrNull() ?: return@forEach
                                val senderName = senderId.toString()
                                val text = gc.content
                                val ts = System.currentTimeMillis()
                                listener.onGroupMessageReceived(groupId, senderId, senderName, text, ts)
                            }
                        }
                        MsgType.AGENT_CHAT_STREAM.wire -> {
                            if (wrapper.hasAgentStream()) {
                                val stream = wrapper.agentStream
                                listener.onAgentStreamChunk(
                                    messageId = stream.messageId,
                                    fullContent = stream.chunk,
                                    done = stream.done,
                                    error = stream.error
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    log.debug("无法解析 Stream 数据为 MessageWrapper: ${e.message}")
                }
            }
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
     * 根据消息类型和目标选择或创建会话流。
     *
     * 流分配规则：
     * - 控制类消息（LOGIN/LOGOUT/HEARTBEAT）-> Stream 0
     * - 会话类消息（CHAT/GROUP_CHAT/AGENT_CHAT）-> 按 conversationId 复用或新建流
     */
    private fun getOrCreateStream(type: MsgType, targetId: String): Long {
        return when (type) {
            MsgType.LOGIN, MsgType.LOGOUT, MsgType.HEARTBEAT -> {
                sessions[CONTROL_SESSION_KEY]?.streamId
                    ?: throw IllegalStateException("控制流未就绪")
            }
            MsgType.CHAT, MsgType.GROUP_CHAT, MsgType.AGENT_CHAT -> {
                val convId = targetId
                val existing = sessions[convId]
                if (existing != null && existing.isActive) {
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
                            else -> StreamSession.ConversationType.PRIVATE
                        },
                        targetId = targetId
                    )
                    // 发送 Stream 初始化首帧
                    val initFrame = QuicStreamProtocol.createStreamInitFrame(
                        conversationType = when (type) {
                            MsgType.CHAT -> "private"
                            MsgType.GROUP_CHAT -> "group"
                            MsgType.AGENT_CHAT -> "agent"
                            else -> "private"
                        },
                        targetId = targetId
                    )
                    transport.send(newStreamId, initFrame)
                    newStreamId
                }
            }
            MsgType.CHECK, MsgType.ACK, MsgType.RESPONSE, MsgType.AGENT_CHAT_STREAM -> {
                sessions[CONTROL_SESSION_KEY]?.streamId
                    ?: throw IllegalStateException("控制流未就绪")
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
            val logoutPayload = buildLogoutPayload(ServerConfig.Token)
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
