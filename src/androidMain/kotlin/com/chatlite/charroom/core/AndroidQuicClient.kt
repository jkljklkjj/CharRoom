package com.chatlite.charroom.core

import core.*
import core.state.GlobalAppState
import core.state.GlobalChatState
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.incubator.codec.quic.*
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android QUIC 客户端。
 * <p>
 * 与桌面端 QuicClientImpl 逻辑一致，使用 Netty QUIC 自定义协议。
 * 需要 netty-incubator-codec-native-quic 的 Android 原生库支持。</p>
 */
class AndroidQuicClient : ChatTransport {

    private val log = LoggerFactory.getLogger(AndroidQuicClient::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var group: EventLoopGroup? = null
    private var quicChannel: QuicChannel? = null
    private var datagramChannel: Channel? = null
    private val connected = AtomicBoolean(false)
    private val sessions = mutableMapOf<String, StreamSession>()
    private val messageQueue = ConcurrentLinkedQueue<PendingMessage>()

    internal val messageListeners = mutableListOf<MessageReceiveListener>()
    internal val authStateListeners = mutableListOf<AuthStateListener>()

    // ── 生命周期 ──

    override fun start(host: String?, port: Int?) {
        val h = host ?: ServerConfig.QUIC_HOST
        val p = port ?: ServerConfig.QUIC_PORT

        if (!Quic.isAvailable()) {
            log.error("Netty QUIC 原生库不可用，Android 需编译 netty-incubator-codec-native-quic 的 .so")
            notifyAuthInvalidated("QUIC native library not available")
            return
        }

        runBlocking {
            try {
                connect(h, p)
                connected.set(true)
                doLogin()
                startHeartbeat()
            } catch (e: Exception) {
                log.error("QUIC 连接失败", e)
                notifyAuthInvalidated(e.message ?: "QUIC connection failed")
            }
        }
    }

    override fun stop() { shutdown() }
    override fun isConnected(): Boolean = connected.get()
    override fun reconnect() { stop(); start() }

    override fun logoutAndDisconnect() {
        val token = GlobalAppState.currentToken
        if (!token.isNullOrBlank()) {
            val payload = buildLogoutPayload(token)
            sendInternal(payload)
        }
        stop()
    }

    override fun send(payload: ByteArray, type: MsgType, targetClientId: String,
                      expectedResponses: Int, callback: (Boolean, List<ByteArray>) -> Unit) {
        scope.launch {
            try {
                val streamId = getOrCreateStream(type, targetClientId)
                val frame = QuicStreamProtocol.encodeFrame(payload)
                (quicChannel?.stream(streamId)?.writeAndFlush(frame))
                callback(true, emptyList())
            } catch (e: Exception) {
                callback(false, emptyList())
            }
        }
    }

    override fun sendText(content: String, callback: (Boolean) -> Unit) {
        callback(false)
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
    override val isServerConnected: Boolean get() = connected.get()

    // ── QUIC 连接 ──

    private fun connect(host: String, port: Int) {
        val sslCtx = QuicSslContextBuilder.forClient()
            .applicationProtocols(ServerConfig.QUIC_ALPN)
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .build()

        val codec = QuicClientCodecBuilder()
            .sslContext(sslCtx)
            .maxIdleTimeout(30000, TimeUnit.MILLISECONDS)
            .initialMaxStreamsUnidirectional(100)
            .initialMaxStreamsBidirectional(100)
            .congestionControlAlgorithm(QuicCongestionControlAlgorithm.BBR)
            .build()

        val elGroup = NioEventLoopGroup(1)
        group = elGroup

        datagramChannel = Bootstrap()
            .group(elGroup)
            .channel(NioDatagramChannel::class.java)
            .handler(object : ChannelInitializer<Channel>() {
                override fun initChannel(ch: Channel) { ch.pipeline().addLast(codec) }
            })
            .bind(0)
            .sync()
            .channel()

        val address = InetSocketAddress(host, port)
        val f = QuicChannel.newBootstrap(datagramChannel!!)
            .streamHandler(QuicStreamInitializer { streamId, data -> handleStreamFrame(streamId, data) })
            .remoteAddress(address)
            .connect()

        val done = f.awaitUninterruptibly(10, TimeUnit.SECONDS)
        if (!done) throw RuntimeException("QUIC 连接超时")
        quicChannel = f.getNow()
        if (quicChannel == null) throw f.cause() ?: RuntimeException("QUIC 连接失败")
        log.info("QUIC 连接成功: {}:{}", host, port)
    }

    private fun shutdown() {
        quicChannel?.close()?.awaitUninterruptibly()
        datagramChannel?.close()?.awaitUninterruptibly()
        group?.shutdownGracefully()
        connected.set(false)
        sessions.clear()
    }

    // ── 登录 & 心跳 ──

    private fun doLogin() {
        val stream0 = quicChannel?.newStreamBootstrap()
            ?.type(QuicStreamType.BIDIRECTIONAL)
            ?.handler(QuicStreamInitializer { _, _ -> })
            ?.create()
            ?.awaitUninterruptibly()?.getNow()
            ?: throw RuntimeException("控制流创建失败")

        sessions[CONTROL_SESSION_KEY] = StreamSession(
            streamId = stream0.streamId(),
            conversationId = CONTROL_SESSION_KEY,
            conversationType = StreamSession.ConversationType.CONTROL,
            targetId = ""
        )

        val loginPayload = buildLoginPayload(GlobalAppState.currentToken ?: "")
        stream0.writeAndFlush(QuicStreamProtocol.encodeFrame(loginPayload))
        log.info("登录消息已发送")
    }

    private fun startHeartbeat() = scope.launch {
        while (connected.get()) {
            delay(20_000)
            if (!connected.get()) break
            try {
                val controlStream = sessions[CONTROL_SESSION_KEY]
                if (controlStream != null && quicChannel != null) {
                    val s = quicChannel!!.stream(controlStream.streamId)
                    s?.writeAndFlush(QuicStreamProtocol.encodeFrame(buildHeartbeatPayload()))
                }
            } catch (_: Exception) { }
        }
    }

    // ── 流管理 ──

    private fun getOrCreateStream(type: MsgType, targetId: String): Long {
        return when (type) {
            MsgType.LOGIN, MsgType.LOGOUT, MsgType.HEARTBEAT -> {
                sessions[CONTROL_SESSION_KEY]?.streamId
                    ?: throw IllegalStateException("控制流未就绪")
            }
            MsgType.CHAT, MsgType.GROUP_CHAT, MsgType.AGENT_CHAT -> {
                val convId = targetId
                val existing = sessions[convId]
                if (existing != null && quicChannel?.stream(existing.streamId)?.isActive == true) {
                    existing.streamId
                } else {
                    val newStream = quicChannel?.newStreamBootstrap()
                        ?.type(QuicStreamType.BIDIRECTIONAL)
                        ?.handler(QuicStreamInitializer { _, _ -> })
                        ?.create()
                        ?.awaitUninterruptibly()?.getNow()
                        ?: throw RuntimeException("流创建失败")
                    sessions[convId] = StreamSession(
                        streamId = newStream.streamId(),
                        conversationId = convId,
                        conversationType = when (type) {
                            MsgType.CHAT -> StreamSession.ConversationType.PRIVATE
                            MsgType.GROUP_CHAT -> StreamSession.ConversationType.GROUP
                            MsgType.AGENT_CHAT -> StreamSession.ConversationType.AGENT
                        },
                        targetId = targetId
                    )
                    newStream.streamId()
                }
            }
            else -> throw IllegalArgumentException("不支持的消息类型: $type")
        }
    }

    private fun handleStreamFrame(streamId: Long, data: ByteArray) {
        try {
            val wrapper = com.chatlite.proto.MessageProtos.MessageWrapper.parseFrom(data)
            synchronized(messageListeners) {
                messageListeners.forEach { listener ->
                    when (wrapper.type) {
                        MsgType.RESPONSE.wire -> {
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
                        }
                        MsgType.CHAT.wire -> {
                            if (wrapper.hasChat()) {
                                val chat = wrapper.chat
                                listener.onPrivateMessageReceived(
                                    senderId = chat.userId.toIntOrNull() ?: 0,
                                    message = chat.content,
                                    timestamp = chat.timestamp.toLongOrNull() ?: System.currentTimeMillis()
                                )
                            }
                        }
                        MsgType.GROUP_CHAT.wire -> {
                            if (wrapper.hasGroupChat()) {
                                val gc = wrapper.groupChat
                                listener.onGroupMessageReceived(
                                    groupId = gc.targetClientId.toIntOrNull() ?: 0,
                                    senderId = gc.userId.toIntOrNull() ?: 0,
                                    senderName = "用户${gc.userId}",
                                    message = gc.content,
                                    timestamp = gc.timestamp.toLongOrNull() ?: System.currentTimeMillis()
                                )
                            }
                        }
                        MsgType.AGENT_CHAT.wire -> {
                            if (wrapper.hasChat()) {
                                val chat = wrapper.chat
                                listener.onPrivateMessageReceived(
                                    senderId = chat.userId.toIntOrNull() ?: 0,
                                    message = chat.content,
                                    timestamp = chat.timestamp.toLongOrNull() ?: System.currentTimeMillis()
                                )
                            }
                        }
                        MsgType.ACK.wire -> {
                            if (wrapper.hasAck()) {
                                val ack = wrapper.ack
                                if (ack.conversationId.isNotBlank() && ack.seqId > 0) {
                                    scope.launch { GlobalChatState.updateConversationSeqId(ack.conversationId, ack.seqId) }
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) { }
    }

    private fun sendInternal(payload: ByteArray) {
        val streamId = sessions[CONTROL_SESSION_KEY]?.streamId ?: return
        quicChannel?.stream(streamId)?.writeAndFlush(QuicStreamProtocol.encodeFrame(payload))
    }

    private fun notifyAuthInvalidated(reason: String) {
        authStateListeners.forEach { it.onAuthInvalidated(reason) }
    }

    private fun <T> runBlocking(block: suspend CoroutineScope.() -> T): T =
        kotlinx.coroutines.runBlocking(Dispatchers.IO, block)

    companion object {
        private const val CONTROL_SESSION_KEY = "__control__"
    }
}
