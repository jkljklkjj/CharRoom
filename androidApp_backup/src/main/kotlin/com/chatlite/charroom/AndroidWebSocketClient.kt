package com.chatlite.charroom

import com.chatlite.proto.MessageProtos
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
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class AndroidWebSocketClient {
    private var group: EventLoopGroup? = null
    private var channel: Channel? = null
    private var connectFuture: CompletableFuture<Boolean>? = null

    fun connect(
        token: String,
        ownUserId: Int,
        onMessage: (ChatMessage) -> Unit,
        onStatusUpdate: (clientId: String, online: Boolean) -> Unit
    ): Boolean {
        if (channel?.isActive == true) return true

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
                        val sslContext = io.netty.handler.ssl.SslContextBuilder.forClient()
                            .trustManager(io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE)
                            .build()
                        pipeline.addLast(sslContext.newHandler(ch.alloc(), uri.host, if (uri.port != -1) uri.port else 443))
                    }

                    pipeline.addLast(HttpClientCodec())
                    pipeline.addLast(HttpObjectAggregator(8192))
                    pipeline.addLast(WebSocketClientProtocolHandler(handshaker, true))
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
                                        if (wrapper.payloadCase == MessageProtos.MessageWrapper.PayloadCase.CHAT ||
                                            wrapper.payloadCase == MessageProtos.MessageWrapper.PayloadCase.AGENTCHAT ||
                                            wrapper.payloadCase == MessageProtos.MessageWrapper.PayloadCase.GROUPCHAT ||
                                            wrapper.payloadCase == MessageProtos.MessageWrapper.PayloadCase.AGENTSTREAM) {
                                            parseIncomingChat(bytes, ownUserId)?.let(onMessage)
                                        }
                                    } catch (_: Exception) {
                                        try {
                                            val response = MessageProtos.ResponseMessage.parseFrom(bytes)
                                            if (response.clientId.isNotBlank()) {
                                                onStatusUpdate(response.clientId, response.online)
                                            }
                                        } catch (_: Exception) {
                                            // ignore non-response messages
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
            sendBinary(buildLoginPayload(token))
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

    private fun parseIncomingChat(bytes: ByteArray, ownUserId: Int): ChatMessage? {
        return try {
            val wrapper = MessageProtos.MessageWrapper.parseFrom(bytes)
            when (wrapper.payloadCase) {
                MessageProtos.MessageWrapper.PayloadCase.CHAT -> {
                    val v = wrapper.chat
                    val senderId = v.userId.toIntOrNull() ?: 0
                    val timestamp = (v.timestamp.takeIf { it.isNotBlank() }?.toLongOrNull() ?: System.currentTimeMillis())
                    ChatMessage(
                        senderId = senderId,
                        receiverId = ownUserId,
                        content = v.content,
                        isMe = senderId == ownUserId,
                        timestamp = timestamp,
                        messageId = "ws-${wrapper.type}-${senderId}-${timestamp}-${v.content.hashCode()}"
                    )
                }
                MessageProtos.MessageWrapper.PayloadCase.AGENTCHAT -> {
                    val v = wrapper.agentChat
                    val senderId = v.userId.toIntOrNull() ?: 0
                    val timestamp = (v.timestamp.takeIf { it.isNotBlank() }?.toLongOrNull() ?: System.currentTimeMillis())
                    ChatMessage(
                        senderId = senderId,
                        receiverId = ownUserId,
                        content = v.content,
                        isMe = senderId == ownUserId,
                        timestamp = timestamp,
                        messageId = "ws-${wrapper.type}-${senderId}-${timestamp}-${v.content.hashCode()}"
                    )
                }
                MessageProtos.MessageWrapper.PayloadCase.GROUPCHAT -> {
                    val v = wrapper.groupChat
                    val senderId = v.userId.toIntOrNull() ?: 0
                    val groupId = v.targetClientId.toIntOrNull()?.let { -it } ?: 0
                    ChatMessage(
                        senderId = senderId,
                        receiverId = groupId,
                        content = v.content,
                        isMe = senderId == ownUserId,
                        timestamp = System.currentTimeMillis(),
                        messageId = "ws-group-${senderId}-${v.content.hashCode()}"
                    )
                }
                MessageProtos.MessageWrapper.PayloadCase.AGENTSTREAM -> {
                    val v = wrapper.agentStream
                    val messageId = v.messageId.ifBlank { "agent-stream-${System.nanoTime()}" }
                    val rawContent = if (v.error && v.message.isNotBlank()) v.message else v.chunk
                    if (rawContent.isBlank()) {
                        null
                    } else {
                        ChatMessage(
                            senderId = ServerConfig.AGENT_ASSISTANT_ID,
                            receiverId = ownUserId,
                            content = rawContent,
                            isMe = false,
                            timestamp = System.currentTimeMillis(),
                            messageId = messageId
                        )
                    }
                }
                MessageProtos.MessageWrapper.PayloadCase.CHECK,
                MessageProtos.MessageWrapper.PayloadCase.HEARTBEAT,
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

    fun disconnect() {
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
        }
    }

    private fun buildLoginPayload(token: String?): ByteArray {
        val login = MessageProtos.LoginMessage.newBuilder()
            .setTargetClientId(token ?: "")
            .build()
        return MessageProtos.MessageWrapper.newBuilder()
            .setType(NetworkConstants.WsMessageType.LOGIN)
            .setLogin(login)
            .build()
            .toByteArray()
    }

    private fun buildHeartbeatPayload(): ByteArray {
        val hb = MessageProtos.HeartbeatMessage.newBuilder()
            .setTimestamp(System.currentTimeMillis())
            .build()
        return MessageProtos.MessageWrapper.newBuilder()
            .setType(NetworkConstants.WsMessageType.HEARTBEAT)
            .setHeartbeat(hb)
            .build()
            .toByteArray()
    }

    private fun buildLogoutPayload(userId: String): ByteArray {
        val logout = MessageProtos.LogoutMessage.newBuilder()
            .setUserId(userId)
            .build()
        return MessageProtos.MessageWrapper.newBuilder()
            .setType(NetworkConstants.WsMessageType.LOGOUT)
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
            .setType(NetworkConstants.WsMessageType.CHAT)
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
            .setType(NetworkConstants.WsMessageType.AGENT_CHAT)
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
            .setType(NetworkConstants.WsMessageType.GROUP_CHAT)
            .setGroupChat(gm)
            .build()
            .toByteArray()
    }

    private fun buildCheckPayload(targetClientId: String): ByteArray {
        val check = MessageProtos.CheckMessage.newBuilder()
            .setTargetClientId(targetClientId)
            .build()
        return MessageProtos.MessageWrapper.newBuilder()
            .setType(NetworkConstants.WsMessageType.CHECK)
            .setCheck(check)
            .build()
            .toByteArray()
    }
}
