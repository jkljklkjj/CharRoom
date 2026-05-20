package com.chatlite.charroom.data.network

import model.Message as AppMessage
import model.GroupMessage as AppGroupMessage
import com.chatlite.proto.MessageProtos
import core.MsgType
import core.NetworkConstants
import core.ServerConfig
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler
import io.netty.handler.codec.http.websocketx.WebSocketVersion
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent
import io.netty.handler.timeout.IdleStateHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.chatlite.charroom.BuildConfig
import timber.log.Timber
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

class AndroidWebSocketClient {
    private var group: EventLoopGroup? = null
    private var channel: Channel? = null
    private var connectFuture: CompletableFuture<Boolean>? = null
    private var heartbeatJob: kotlinx.coroutines.Job? = null
    private var reconnectJob: kotlinx.coroutines.Job? = null
    private var scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentToken: String? = null
    private var currentUserId: Int = 0
    private var onMessageCallback: ((AppMessage) -> Unit)? = null
    private var onStatusUpdateCallback: ((clientId: String, online: Boolean) -> Unit)? = null
    private var onAuthFailedCallback: ((reason: String) -> Unit)? = null
    private var isManualDisconnect = false
    private val connectionGeneration = AtomicInteger(0)
    private var lastConnectedAtMs: Long = 0L
    private var awaitingLoginAck = false
    private var loginAckFuture: CompletableFuture<Boolean>? = null
    private var reconnectDelay = 1000L // 初始重连延迟1秒
    private val maxReconnectDelay = 30000L // 最大重连延迟30秒

    /**
     * 连接状态监听器
     */
    interface ConnectionListener {
        fun onConnected()
        fun onDisconnected()
        fun onAuthFailed(reason: String)
    }

    private var connectionListener: ConnectionListener? = null

    fun connect(
        token: String,
        ownUserId: Int,
        onMessage: (AppMessage) -> Unit,
        onStatusUpdate: (clientId: String, online: Boolean) -> Unit,
        onAuthFailed: ((reason: String) -> Unit)? = null,
        listener: ConnectionListener? = null
    ): Boolean {
        if (channel?.isActive == true) return true

        val generation = connectionGeneration.incrementAndGet()

        // 保存连接参数，用于重连
        currentToken = token
        currentUserId = ownUserId
        onMessageCallback = onMessage
        onStatusUpdateCallback = onStatusUpdate
        onAuthFailedCallback = onAuthFailed
        connectionListener = listener
        isManualDisconnect = false
        awaitingLoginAck = false
        loginAckFuture = CompletableFuture()

        group = NioEventLoopGroup()
        connectFuture = CompletableFuture()

        val uri = URI(NetworkConstants.wsUrl())
        val headers = DefaultHttpHeaders().apply {
            add("Host", ServerConfig.SERVER_IP)
            if (token.isNotBlank()) add("Authorization", "Bearer $token")
            add("Origin", NetworkConstants.wsOrigin())
        }

        val handshaker = WebSocketClientHandshakerFactory.newHandshaker(
            uri,
            WebSocketVersion.V13,
            null,
            true,
            headers,
            65536
        )

        val bootstrap = Bootstrap()
            .group(group)
            .channel(NioSocketChannel::class.java)
            .handler(object : ChannelInitializer<Channel>() {
                override fun initChannel(ch: Channel) {
                    val pipeline = ch.pipeline()

                    // WSS协议添加SSL处理器
                    if (uri.scheme == "wss") {
                        val sslBuilder = io.netty.handler.ssl.SslContextBuilder.forClient()
                        // 仅在 debug 模式下使用不安全的 TrustManager，生产环境使用系统默认信任链
                        if (BuildConfig.DEBUG) {
                            sslBuilder.trustManager(io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE)
                        }
                        val sslContext = sslBuilder.build()
                        pipeline.addLast(sslContext.newHandler(ch.alloc(), uri.host, if (uri.port != -1) uri.port else 443))
                    }

                    pipeline.addLast(HttpClientCodec())
                    pipeline.addLast(HttpObjectAggregator(8192))
                    pipeline.addLast(WebSocketClientProtocolHandler(handshaker, true))
                    // 添加空闲状态检测：读超时45秒，写超时45秒，全部超时90秒
                    pipeline.addLast(IdleStateHandler(45, 45, 90))
                    pipeline.addLast(object : SimpleChannelInboundHandler<Any>() {
                        override fun channelRead0(ctx: ChannelHandlerContext, msg: Any) {
                            when (msg) {
                                is TextWebSocketFrame -> {
                                    // Text frames are not currently used by Android chat UI
                                }
                                is BinaryWebSocketFrame -> {
                                    val bytes = ByteArray(msg.content().readableBytes())
                                    msg.content().readBytes(bytes)
                                    try {
                                        val wrapper = MessageProtos.MessageWrapper.parseFrom(bytes)
                                        if (awaitingLoginAck) {
                                            Timber.i("WebSocket登录等待中收到包: type=${wrapper.payloadCase}, bytes=${bytes.size}")
                                        }
                                        if (awaitingLoginAck) {
                                            when (wrapper.payloadCase) {
                                                MessageProtos.MessageWrapper.PayloadCase.ACK -> {
                                                    val ackMessage = wrapper.getAck().message
                                                    Timber.i("WebSocket登录ACK内容: message=$ackMessage")
                                                    if (ackMessage.contains("登录成功")) {
                                                        awaitingLoginAck = false
                                                        loginAckFuture?.complete(true)
                                                        Timber.i("WebSocket登录ACK确认成功")
                                                    } else {
                                                        Timber.w("WebSocket登录ACK未命中成功标记: message=$ackMessage")
                                                    }
                                                }
                                                MessageProtos.MessageWrapper.PayloadCase.RESPONSE -> {
                                                    val response = wrapper.getResponse()
                                                    Timber.i("WebSocket登录等待中收到RESPONSE: success=${response.success}, message=${response.message}")
                                                    if (!response.success) {
                                                        val reason = response.message.takeIf { it.isNotBlank() } ?: "WebSocket登录失败"
                                                        awaitingLoginAck = false
                                                        loginAckFuture?.complete(false)
                                                        onAuthFailed?.invoke(reason)
                                                        ctx.close()
                                                        return
                                                    }
                                                }
                                                else -> {}
                                            }
                                        }
                                        if (wrapper.payloadCase == MessageProtos.MessageWrapper.PayloadCase.CHAT ||
                                            wrapper.payloadCase == MessageProtos.MessageWrapper.PayloadCase.AGENTCHAT ||
                                            wrapper.payloadCase == MessageProtos.MessageWrapper.PayloadCase.GROUPCHAT ||
                                            wrapper.payloadCase == MessageProtos.MessageWrapper.PayloadCase.AGENTSTREAM) {
                                            parseIncomingChat(bytes, ownUserId)?.let { chatMsg ->
                                                // 回复ACK确认（流式消息不需要重复确认）
                                                if (chatMsg.messageId.isNotBlank() && !chatMsg.messageId.startsWith("agent-stream-")) {
                                                    sendBinary(buildAckPayload(chatMsg.messageId))
                                                }
                                                onMessage(chatMsg)
                                            }
                                        }
                                    } catch (_: Exception) {
                                        try {
                                            val response = MessageProtos.ResponseMessage.parseFrom(bytes)
                                            if (response.clientId.isNotBlank()) {
                                                onStatusUpdate(response.clientId, response.online)
                                            }
                                            // 检查响应是否为登录失败
                                            if (!response.success) {
                                                val msg = response.message ?: ""
                                                if (msg.contains("登录失败") || msg.contains("token无效") || msg.contains("token过期") || msg.contains("token不能为空")) {
                                                    // 认证失败，通知上层
                                                    awaitingLoginAck = false
                                                    loginAckFuture?.complete(false)
                                                    onAuthFailed?.invoke(msg)
                                                    // 关闭连接
                                                    ctx.close()
                                                }
                                            }
                                        } catch (_: Exception) {
                                            // 尝试解析普通响应
                                            try {
                                                val root = org.json.JSONObject(String(bytes, Charsets.UTF_8))
                                                if (!root.optBoolean("success", true)) {
                                                    val msg = root.optString("message", "")
                                                    if (msg.contains("登录失败") || msg.contains("token无效") || msg.contains("token过期") || msg.contains("token不能为空")) {
                                                        onAuthFailed?.invoke(msg)
                                                        ctx.close()
                                                    }
                                                }
                                            } catch (_: Exception) {
                                                // ignore invalid messages
                                            }
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }

                        override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
                            if (evt is WebSocketClientProtocolHandler.ClientHandshakeStateEvent) {
                                when (evt) {
                                    WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE -> {
                                        connectFuture?.complete(true)
                                    }
                                    WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_TIMEOUT -> {
                                        connectFuture?.complete(false)
                                    }
                                    else -> {}
                                }
                            } else if (evt is IdleStateEvent) {
                                // 空闲超时，主动关闭连接触发重连
                                if (evt.state() == IdleState.READER_IDLE || evt.state() == IdleState.ALL_IDLE) {
                                    ctx.close()
                                }
                            }
                            super.userEventTriggered(ctx, evt)
                        }

                        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                            connectFuture?.completeExceptionally(cause)
                            ctx.close()
                        }
                    })
                }
            })

        return try {
            // 从URI中获取正确的host和port
            val connectHost = uri.host
            val connectPort = if (uri.port != -1) uri.port else if (uri.scheme == "wss") 443 else 80

            val future: ChannelFuture = bootstrap.connect(connectHost, connectPort).sync()
            channel = future.channel()
            val connected = connectFuture?.get(10, TimeUnit.SECONDS) ?: false
            if (!connected) {
                disconnect()
                return false
            }

            Timber.i("WebSocket握手成功，等待登录ACK确认")

            // 添加通道关闭监听器
            channel?.closeFuture()?.addListener {
                if (generation != connectionGeneration.get()) {
                    return@addListener
                }
                val connectedRecently = lastConnectedAtMs > 0L && (System.currentTimeMillis() - lastConnectedAtMs) < 2000L
                if (connectedRecently && !isManualDisconnect) {
                    Timber.i("WebSocket刚连接后快速断开，忽略本次自动重连")
                    return@addListener
                }
                if (!isManualDisconnect) {
                    connectionListener?.onDisconnected()
                }
            }

            awaitingLoginAck = true
            sendBinary(buildLoginPayload(token))

            val loginConfirmed = try {
                loginAckFuture?.get(10, TimeUnit.SECONDS) ?: false
            } catch (_: TimeoutException) {
                Timber.w("WebSocket登录ACK等待超时，10秒内未收到成功ACK")
                false
            } catch (_: Exception) {
                Timber.w("WebSocket登录ACK等待异常结束")
                false
            }

            if (!loginConfirmed) {
                Timber.w("WebSocket登录确认失败或超时，准备断开并重连")
                disconnect()
                return false
            }

            startHeartbeat()
            reconnectDelay = 1000L // 重置重连延迟
            lastConnectedAtMs = System.currentTimeMillis()
            Timber.i("WebSocket登录确认成功，连接建立完成")
            connectionListener?.onConnected()
            true
        } catch (e: Exception) {
            disconnect()
            false
        }
    }

    fun sendChatText(targetId: Int, content: String, senderId: Int): Boolean {
        return sendBinary(buildChatPayload(targetId.toString(), content, senderId, System.currentTimeMillis()))
    }

    fun sendAgentText(targetId: Int, content: String, senderId: Int): Boolean {
        return sendBinary(buildAgentChatPayload(targetId.toString(), content, senderId, System.currentTimeMillis()))
    }

    fun sendGroupText(targetId: Int, content: String, senderId: Int): Boolean {
        val groupId = if (targetId < 0) -targetId else targetId
        return sendBinary(buildGroupChatPayload(groupId.toString(), content, senderId))
    }

    fun sendCheck(targetId: Int): Boolean {
        return sendBinary(buildCheckPayload(targetId.toString()))
    }

    fun sendLogout(userId: String): Boolean {
        return sendBinary(buildLogoutPayload(userId))
    }

    fun sendHeartbeat(): Boolean {
        return sendBinary(buildHeartbeatPayload())
    }

    private fun parseIncomingChat(bytes: ByteArray, ownUserId: Int): AppMessage? {
        return try {
            val wrapper = MessageProtos.MessageWrapper.parseFrom(bytes)
            when (wrapper.payloadCase) {
                MessageProtos.MessageWrapper.PayloadCase.CHAT -> {
                    val v = wrapper.chat
                    val senderId = v.userId.toIntOrNull() ?: 0
                    val timestamp = (v.timestamp.takeIf { it.isNotBlank() }?.toLongOrNull() ?: System.currentTimeMillis())
                    AppMessage(
                        senderId = senderId,
                        message = v.content,
                        sender = senderId == ownUserId,
                        receiverId = ownUserId,
                        timestamp = timestamp,
                        isSent = true,
                        messageId = "ws-${wrapper.type}-${senderId}-${timestamp}-${v.content.hashCode()}"
                    )
                }
                MessageProtos.MessageWrapper.PayloadCase.AGENTCHAT -> {
                    val v = wrapper.agentChat
                    if (v.content.isBlank()) {
                        return null
                    }
                    val senderId = v.userId.toIntOrNull() ?: 0
                    val timestamp = (v.timestamp.takeIf { it.isNotBlank() }?.toLongOrNull() ?: System.currentTimeMillis())
                    AppMessage(
                        senderId = senderId,
                        message = v.content,
                        sender = senderId == ownUserId,
                        receiverId = ownUserId,
                        timestamp = timestamp,
                        isSent = true,
                        messageId = "ws-${wrapper.type}-${senderId}-${timestamp}-${v.content.hashCode()}"
                    )
                }
                MessageProtos.MessageWrapper.PayloadCase.GROUPCHAT -> {
                    val v = wrapper.groupChat
                    val senderId = v.userId.toIntOrNull() ?: 0
                    val groupId = v.targetClientId.toIntOrNull()?.let { -it } ?: 0
                    // 群聊消息用receiverId的负值表示群组ID
                    AppMessage(
                        senderId = senderId,
                        message = v.content,
                        sender = senderId == ownUserId,
                        receiverId = groupId, // 群组ID为负值
                        timestamp = System.currentTimeMillis(),
                        isSent = true,
                        messageId = "ws-group-${senderId}-${v.content.hashCode()}"
                    )
                }
                MessageProtos.MessageWrapper.PayloadCase.AGENTSTREAM -> {
                    val v = wrapper.agentStream
                    // 不要生成随机ID，留空让上层统一处理流式会话ID
                    val messageId = v.messageId.ifBlank { "" }
                    val rawContent = if (v.error && v.message.isNotBlank()) v.message else v.chunk
                    if (rawContent.isBlank() && !v.done) {
                        null
                    } else {
                        AppMessage(
                            senderId = ServerConfig.AGENT_ASSISTANT_ID,
                            message = rawContent,
                            sender = false,
                            receiverId = ownUserId,
                            timestamp = System.currentTimeMillis(),
                            isSent = true,
                            messageId = messageId
                        )
                    }
                }
                MessageProtos.MessageWrapper.PayloadCase.CHECK,
                MessageProtos.MessageWrapper.PayloadCase.HEARTBEAT -> {
                    // 收到服务端心跳，回复心跳包
                    sendHeartbeat()
                    null
                }
                MessageProtos.MessageWrapper.PayloadCase.LOGOUT -> {
                    // control frames, not chat messages
                    null
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun sendBinary(payload: ByteArray): Boolean {
        val ch = channel
        if (ch == null || !ch.isActive) return false
        return try {
            ch.eventLoop().execute {
                val buf: ByteBuf = ch.alloc().buffer(payload.size)
                buf.writeBytes(payload)
                ch.writeAndFlush(BinaryWebSocketFrame(buf))
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun sendText(content: String): Boolean {
        val ch = channel
        if (ch == null || !ch.isActive) return false
        return try {
            ch.eventLoop().execute {
                ch.writeAndFlush(TextWebSocketFrame(content))
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 启动心跳任务
     */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(start = CoroutineStart.DEFAULT) {
            while (isActive) {
                delay(30000) // 每30秒发送一次心跳
                if (channel?.isActive == true) {
                    sendBinary(buildHeartbeatPayload())
                } else {
                    break
                }
            }
        }
    }

    fun disconnect() {
        isManualDisconnect = true
        lastConnectedAtMs = 0L
        awaitingLoginAck = false
        loginAckFuture?.cancel(true)
        loginAckFuture = null
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        heartbeatJob = null
        reconnectJob = null

        try {
            channel?.close()?.addListener(ChannelFutureListener.CLOSE)
        } catch (_: Exception) {
        }
        try {
            group?.shutdownGracefully()
        } catch (_: Exception) {
        } finally {
            channel = null
            group = null
            connectFuture = null
            // 取消并重置协程范围，避免使用 GlobalScope
            try {
                scope.cancel()
            } catch (_: Exception) {}
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            if (!isManualDisconnect) {
                connectionListener?.onDisconnected()
            }
        }
    }

    private fun buildLoginPayload(token: String?): ByteArray {
        val login = MessageProtos.LoginMessage.newBuilder()
            .setToken(token ?: "")
            .build()
        return MessageProtos.MessageWrapper.newBuilder()
            .setType(MsgType.LOGIN.wire)
            .setLogin(login)
            .build()
            .toByteArray()
    }

    private fun buildHeartbeatPayload(): ByteArray {
        val hb = MessageProtos.HeartbeatMessage.newBuilder()
            .setTimestamp(System.currentTimeMillis())
            .build()
        return MessageProtos.MessageWrapper.newBuilder()
            .setType(MsgType.HEARTBEAT.wire)
            .setHeartbeat(hb)
            .build()
            .toByteArray()
    }

    private fun buildLogoutPayload(userId: String): ByteArray {
        val logout = MessageProtos.LogoutMessage.newBuilder()
            .setUserId(userId)
            .build()
        return MessageProtos.MessageWrapper.newBuilder()
            .setType(MsgType.LOGOUT.wire)
            .setLogout(logout)
            .build()
            .toByteArray()
    }

    private fun buildChatPayload(targetClientId: String, content: String, userId: Int, timestamp: Long): ByteArray {
        val chat = MessageProtos.ChatMessage.newBuilder()
            .setTargetClientId(targetClientId)
            .setContent(content)
            .setUserId(userId.toString())
            .setTimestamp(timestamp.toString())
            .build()
        return MessageProtos.MessageWrapper.newBuilder()
            .setType(MsgType.CHAT.wire)
            .setChat(chat)
            .build()
            .toByteArray()
    }

    private fun buildAgentChatPayload(targetClientId: String, content: String, userId: Int, timestamp: Long): ByteArray {
        val chat = MessageProtos.ChatMessage.newBuilder()
            .setTargetClientId(targetClientId)
            .setContent(content)
            .setUserId(userId.toString())
            .setTimestamp(timestamp.toString())
            .build()
        return MessageProtos.MessageWrapper.newBuilder()
            .setType(MsgType.AGENT_CHAT.wire)
            .setChat(chat)
            .build()
            .toByteArray()
    }

    private fun buildGroupChatPayload(targetClientId: String, content: String, userId: Int): ByteArray {
        val gm = MessageProtos.GroupChatMessage.newBuilder()
            .setTargetClientId(targetClientId)
            .setContent(content)
            .setUserId(userId.toString())
            .build()
        return MessageProtos.MessageWrapper.newBuilder()
            .setType(MsgType.GROUP_CHAT.wire)
            .setGroupChat(gm)
            .build()
            .toByteArray()
    }

    private fun buildCheckPayload(targetClientId: String): ByteArray {
        val check = MessageProtos.CheckMessage.newBuilder()
            .setTargetClientId(targetClientId)
            .build()
        return MessageProtos.MessageWrapper.newBuilder()
            .setType(MsgType.CHECK.wire)
            .setCheck(check)
            .build()
            .toByteArray()
    }

    /**
     * 构建ACK确认消息，通知后端消息已收到
     */
    private fun buildAckPayload(messageId: String): ByteArray {
        val ack = MessageProtos.AckMessage.newBuilder()
            .setMessageId(messageId)
            .build()
        return MessageProtos.MessageWrapper.newBuilder()
            .setType(MsgType.ACK.wire)
            .setAck(ack)
            .build()
            .toByteArray()
    }
}
