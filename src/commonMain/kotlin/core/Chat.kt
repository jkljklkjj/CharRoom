package core

import model.Message
import model.GroupMessage
import model.messages
import model.groupMessages
import androidx.compose.runtime.mutableStateOf
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.websocketx.*
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.*
import component.appendAgentChunk


// 定义发送类型枚举，统一管理后端协议中的字符串
enum class MsgType(val wire: String) {
    LOGIN("login"),
    LOGOUT("logout"),
    CHAT("chat"),
    AGENT_CHAT("agentChat"),
    AGENT_CHAT_STREAM("agentChatStream"),
    GROUP_CHAT("groupChat"),
    CHECK("check"),
    HEARTBEAT("heartbeat");
}

// ApiUnwrap is defined in core.ApiUnwrap (ApiUnwrap.kt)

private fun unwrapApi(content: String): ApiUnwrap {
    return try {
        val trimmed = content.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            // 非JSON，按无包裹成功直传
            ApiUnwrap(hasEnvelope = false, success = true, dataJson = content, message = null)
        } else {
            val mapper = jacksonObjectMapper()
            val root = mapper.readTree(trimmed)
            if (root.has("code")) {
                val code = root.get("code").asInt()
                val msg = root.get("message")?.asText()
                val dataNode = root.get("data")
                val dataStr = dataNode?.let { mapper.writeValueAsString(it) }
                ApiUnwrap(hasEnvelope = true, success = code == 0, dataJson = dataStr, message = msg)
            } else {
                // 旧格式，直接当数据
                ApiUnwrap(hasEnvelope = false, success = true, dataJson = content, message = null)
            }
        }
    } catch (_: Exception) {
        ApiUnwrap(hasEnvelope = false, success = true, dataJson = content, message = null)
    }
}

private fun prompt(msg: String?) {
    if (!msg.isNullOrBlank()) println("提示: $msg")
}

/**
 * 处理接收到的WebSocket信息
 */
class CustomWebSocketHandler : SimpleChannelInboundHandler<Any>() {
    // store raw binary responses
    private val responses = mutableListOf<ByteArray>()
    private var expectedResponses = 1
    var responseFuture: CompletableFuture<List<ByteArray>> = CompletableFuture()
    // handshake future to notify when WebSocket handshake completes
    val handshakeFuture: CompletableFuture<Unit> = CompletableFuture()

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        try { println("CustomWebSocketHandler added to pipeline: ${ctx.pipeline().toString()}") } catch (_: Exception) {}
        super.handlerAdded(ctx)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        try { println("CustomWebSocketHandler channelActive: ${ctx.channel().remoteAddress()}") } catch (_: Exception) {}
        super.channelActive(ctx)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        try { println("CustomWebSocketHandler channelInactive: ${ctx.channel().localAddress()} -> ${ctx.channel().remoteAddress()}") } catch (_: Exception) {}
        if (!handshakeFuture.isDone) {
            handshakeFuture.completeExceptionally(Exception("Channel closed before handshake completed"))
        }
        super.channelInactive(ctx)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        try { println("CustomWebSocketHandler exceptionCaught: ${cause::class.java.name} - ${cause.message}") } catch (_: Exception) {}
        if (!handshakeFuture.isDone) {
            handshakeFuture.completeExceptionally(cause)
        }
        cause.printStackTrace()
        try { ctx.close() } catch (_: Exception) {}
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: Any) {
        AppLog.i({"Received WebSocket message of type ${msg.javaClass.name}"})
        when (msg) {
            is TextWebSocketFrame -> {
                val contentStr = msg.text()
                println("Received WebSocket message: $contentStr")
                handleIncomingMessage(contentStr)
            }
            is BinaryWebSocketFrame -> {
                val bodyBytes = ByteArray(msg.content().readableBytes())
                msg.content().readBytes(bodyBytes)

                // try to parse as proto wrapper; normal incoming flows use parseProtoResponse
                try {
                    val unwrap = parseProtoResponse(bodyBytes)
                    if (unwrap.hasEnvelope && !unwrap.success) {
                        prompt(unwrap.message)
                    } else {
                        val dataStr = unwrap.dataJson ?: String(bodyBytes, Charsets.UTF_8)
                        if (dataStr.trim().startsWith("{") && dataStr.trim().endsWith("}")) {
                            val json = jacksonObjectMapper().readTree(dataStr)
                            if (json.has("type") && json.has("payload")) {
                                handleIncomingFromProtoWrapper(json)
                            } else {
                                val messageId = json.get("messageId")?.asText()
                                if (messageId != null) {
                                    handleIncomingFromJson(json)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // ignore parse error here; still keep the raw bytes for callers
                }

                // collect response for callers waiting in send()
                synchronized(this) {
                    responses.add(bodyBytes)
                    if (responses.size >= expectedResponses && !responseFuture.isDone) {
                        responseFuture.complete(responses.toList())
                    }
                }
            }
            is WebSocketFrame -> {
                // 处理其他类型的WebSocket帧
                println("Received WebSocket frame: ${msg.javaClass.simpleName}")
            }
        }
    }

    private fun handleIncomingFromJson(json: JsonNode) {
        try{
            val senderId = json.get("senderId")?.asInt() ?: throw IllegalArgumentException("Missing senderId")
            val messageId = json.get("messageId")?.asText()
            val timestamp = json.get("timestamp")?.asLong() ?: throw IllegalArgumentException("Missing timestamp")
            val sender = false
            val text = json.get("message")?.asText() ?: throw IllegalArgumentException("Missing content")
            messages += Message(
                senderId = senderId,
                message = text,
                sender = sender,
                timestamp = timestamp,
                isSent = mutableStateOf(true),
                messageId = messageId ?: ""
            )
            try { ActionLogger.log(Action(type = ActionType.RECEIVE_MESSAGE, targetId = senderId.toString(), metadata = mapOf("text" to text.take(64)))) } catch (_: Exception) {}
        } catch (E: Exception) {
            println("Error handling incoming message: ${E.message}")
            E.printStackTrace()
        }
    }

    private fun handleIncomingFromProtoWrapper(json: JsonNode) {
        try {
            val type = json.get("type")?.asText() ?: return
            val payload = json.get("payload") ?: return

            when (type) {
                MsgType.CHAT.wire, MsgType.AGENT_CHAT.wire -> {
                    val senderId = payload.get("userId")?.asText()?.toIntOrNull() ?: return
                    val text = payload.get("content")?.asText() ?: return
                    val ts = payload.get("timestamp")?.asText()?.toLongOrNull() ?: System.currentTimeMillis()

                    messages += Message(
                        senderId = senderId,
                        message = text,
                        sender = false,
                        timestamp = ts,
                        isSent = mutableStateOf(true)
                    )
                    try {
                        ActionLogger.log(
                            Action(
                                type = ActionType.RECEIVE_MESSAGE,
                                targetId = senderId.toString(),
                                metadata = mapOf("text" to text.take(64), "source" to "proto")
                            )
                        )
                    } catch (_: Exception) {
                    }
                }

                MsgType.GROUP_CHAT.wire -> {
                    val groupId = payload.get("targetClientId")?.asText()?.toIntOrNull() ?: return
                    val senderId = payload.get("userId")?.asText()?.toIntOrNull() ?: return
                    val text = payload.get("content")?.asText() ?: return

                    groupMessages += GroupMessage(
                        groupId = groupId,
                        senderName = senderId.toString(),
                        text = text,
                        senderId = senderId,
                        timestamp = System.currentTimeMillis(),
                        isSent = mutableStateOf(true)
                    )
                    try {
                        ActionLogger.log(
                            Action(
                                type = ActionType.RECEIVE_MESSAGE,
                                targetId = groupId.toString(),
                                metadata = mapOf("text" to text.take(64), "group" to "true", "source" to "proto")
                            )
                        )
                    } catch (_: Exception) {
                    }
                }

                MsgType.AGENT_CHAT_STREAM.wire -> {
                    val messageId = payload.get("messageId")?.asText() ?: return
                    val chunk = payload.get("chunk")?.asText() ?: ""
                    val error = payload.get("error")?.asBoolean() ?: false
                    val done = payload.get("done")?.asBoolean() ?: false
                    val messageText = if (error && payload.has("message")) {
                        payload.get("message")?.asText() ?: chunk
                    } else {
                        chunk
                    }
                    if (messageText.isBlank() && !error) return

                    appendAgentChunk(messageId, messageText)
                    if (done && error) {
                        try {
                            ActionLogger.log(
                                Action(
                                    type = ActionType.RECEIVE_MESSAGE,
                                    targetId = messageId,
                                    metadata = mapOf("error" to "true", "source" to "proto")
                                )
                            )
                        } catch (_: Exception) {
                        }
                    }
                }

                else -> {
                    // ignore other proto wrapper types
                }
            }
        } catch (e: Exception) {
            println("Error handling proto wrapper message: ${e.message}")
        }
    }

    private fun handleIncomingMessage(content: String) {
        try {
            val unwrap = unwrapApi(content)
            if (unwrap.hasEnvelope && !unwrap.success) {
                prompt(unwrap.message)
                return
            }
            val dataStr = unwrap.dataJson ?: content
            val json = jacksonObjectMapper().readTree(dataStr)
            val messageType = json.get("type")?.asText() ?: throw IllegalArgumentException("Missing message type")
            val messageContent = json.get("content")?.asText() ?: throw IllegalArgumentException("Missing content")
            val senderName = json.get("sender")?.asText() ?: "Unknown"
            val userId = json.get("userId")?.asInt() ?: -1

            when (messageType) {
                "chat" -> {
                    println("New private message from $senderName: $messageContent")
                    messages += Message(
                        senderId = userId,
                        message = messageContent,
                        sender = false,
                        timestamp = System.currentTimeMillis(),
                        isSent = mutableStateOf(true)
                    )
                    try { ActionLogger.log(Action(type = ActionType.RECEIVE_MESSAGE, targetId = userId.toString(), metadata = mapOf("text" to messageContent.take(64)))) } catch (_: Exception) {}
                }
                "groupChat" -> {
                    val groupId = json.get("groupId")?.asInt() ?: throw IllegalArgumentException("Missing groupId")
                    println("New group message in group $groupId from $senderName: $messageContent")
                    groupMessages += GroupMessage(
                        groupId = groupId,
                        senderName = senderName,
                        text = messageContent,
                        senderId = userId,
                        timestamp = System.currentTimeMillis(),
                        isSent = mutableStateOf(true)
                    )
                    try { ActionLogger.log(Action(type = ActionType.RECEIVE_MESSAGE, targetId = groupId.toString(), metadata = mapOf("text" to messageContent.take(64), "group" to "true"))) } catch (_: Exception) {}
                }
                else -> {
                    println("Unknown message type: $messageType")
                }
            }
        } catch (e: Exception) {
            println("Error handling incoming message: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        try {
            println("CustomWebSocketHandler userEventTriggered: $evt")
            if (evt is WebSocketClientProtocolHandler.ClientHandshakeStateEvent) {
                when (evt) {
                    WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE -> {
                        println("WebSocket handshake complete")
                        if (!handshakeFuture.isDone) handshakeFuture.complete(Unit)
                    }
                    WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_TIMEOUT -> {
                        println("WebSocket handshake timeout event")
                        if (!handshakeFuture.isDone) handshakeFuture.completeExceptionally(Exception("Handshake timeout event"))
                    }
                    else -> {}
                }
            } else {
                super.userEventTriggered(ctx, evt)
            }
        } catch (_: Exception) {
            // ignore
        }
    }

    fun setExpectedResponses(count: Int) {
        synchronized(this) {
            expectedResponses = count
            responses.clear()
            responseFuture = CompletableFuture()
        }
    }
}

/**
 * Netty构建的功能类
 */
object Chat {
    private lateinit var channel: Channel
    private var host = ServerConfig.SERVER_IP
    // default to 80 as requested by change: no explicit port in URIs, connect to port 80
    private var port = 80
    private lateinit var responseHandler: CustomWebSocketHandler
    private val sendLock = Any()

    val isServerConnected = mutableStateOf(true)
    private var heartbeatJob: Job? = null
    private val heartbeatIntervalMillis = 10000L // 10秒

    // helper: strip explicit port from host string if present (simple IPv4/hostname handling)
    private fun hostWithoutPort(rawHost: String?): String {
        if (rawHost.isNullOrBlank()) return ""
        // handle cases like "hostname:8080" -> "hostname"; IPv6 with [] is uncommon here
        return rawHost.substringBefore(':')
    }

    fun start(newHost: String = host, newPort: Int = 80) {
        // normalize host to remove any provided ":port" suffix and force port 80 per request
        host = newHost
        port = 80 // always use 80 for both HTTP and WS as requested
        val effectiveHost = hostWithoutPort(host)

        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(Thread {
            shutdown()
        })
        val group = NioEventLoopGroup()

        try {
            val b = Bootstrap()
            b.group(group)
                .channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<Channel>() {
                    override fun initChannel(ch: Channel) {
                        val pipeline = ch.pipeline()
                        pipeline.addLast(HttpClientCodec())
                        pipeline.addLast(HttpObjectAggregator(8192))
                        // Use WebSocketClientProtocolHandler to handle handshake and install frame encoders/decoders
                        val uidQuery = try {
                            val idVal = ServerConfig.id
                            if (!idVal.isNullOrBlank()) "?clientId=${idVal}" else ""
                        } catch (_: Exception) {
                            ""
                        }
                        // Typical server expects /ws or /ws?clientId=...; use that form
                        val wsUri = URI("ws://$effectiveHost/ws")

                        // create explicit handshaker and install protocol handler (this ensures correct encoders/decoders)
                        val headers = DefaultHttpHeaders()
                        // include Authorization header if token available (many servers require JWT in upgrade request)
                        val tokenForHandshake = try { ServerConfig.Token } catch (_: Exception) { "" }
                        if (!tokenForHandshake.isNullOrBlank()) {
                            headers.set("Authorization", "Bearer $tokenForHandshake")
                        }
                        // set Host header explicitly (some servers validate Host during upgrade)
                        try { headers.set(HttpHeaderNames.HOST.toString(), effectiveHost) } catch (_: Exception) {}
                        // set a sensible Origin to avoid some servers rejecting cross-origin upgrades
                        try { headers.set("Origin", "http://$effectiveHost") } catch (_: Exception) {}

                        val handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                            wsUri,
                            WebSocketVersion.V13,
                            null,
                            true,
                            headers,
                            64 * 1024
                        )
                        // add a logging handler to help debug handshake and frames
//                        pipeline.addLast(LoggingHandler(LogLevel.DEBUG))
                        // log any HTTP responses during handshake (useful to see 401/403/400)
//                        pipeline.addLast(HttpResponseLogger())
                        pipeline.addLast(WebSocketClientProtocolHandler(handshaker, true))
                        responseHandler = CustomWebSocketHandler()
                        pipeline.addLast(responseHandler)
                    }
                })

            // Quick HTTP probe to see if server returns an HTTP error at the WS endpoint
            try {
                val probeClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
                val probeUidQuery = try { ServerConfig.id } catch (_: Exception) { "" }
                // build probe URI without explicit port number (no client id/query)
                val probeUri = URI.create("http://$effectiveHost/ws")
                val probeBuilder = HttpRequest.newBuilder().uri(probeUri).timeout(Duration.ofSeconds(2)).GET()
                val tokenForProbe = try { ServerConfig.Token } catch (_: Exception) { "" }
                if (!tokenForProbe.isNullOrBlank()) probeBuilder.header("Authorization", "Bearer $tokenForProbe")
                val probeReq = probeBuilder.build()
                val probeResp = probeClient.send(probeReq, HttpResponse.BodyHandlers.ofString())
                 println("HTTP probe to $probeUri returned: ${probeResp.statusCode()}")
                 if (probeResp.body() != null && probeResp.body().isNotBlank()) println("Probe body: ${probeResp.body()}")
            } catch (e: Exception) {
                println("HTTP probe failed: ${e.message}")
            }

            // connect + handshake with a small retry loop to tolerate transient early-close during handshake
            val maxAttempts = 3
            var attempt = 0
            var connectedAndHandshaked = false
            while (attempt < maxAttempts && !connectedAndHandshaked) {
                attempt++
                println("Attempt #$attempt to connect to $effectiveHost:$port")
                val f: ChannelFuture? = try {
                    val cf = b.connect(effectiveHost, port)
                    cf.addListener {
                        if (it.isSuccess) {
                            println("Connected to $effectiveHost:$port, channel active=${cf.channel().isActive}")
                        } else {
                            println("Connection failed on attempt $attempt: ${it.cause()}")
                        }
                    }
                    cf.sync()
                    cf
                } catch (e: Exception) {
                    println("Connect failed on attempt $attempt: ${e.message}")
                    null
                }

                if (f == null) {
                    if (attempt < maxAttempts) Thread.sleep(1000)
                    continue
                }

                channel = f.channel()

                // wait for websocket handshake to complete (installed in pipeline)
                try {
                    val handshakeWaitSeconds = 10L
                    println("Waiting up to ${handshakeWaitSeconds}s for websocket handshake...")
                    responseHandler.handshakeFuture.get(handshakeWaitSeconds, TimeUnit.SECONDS)
                    println("WebSocket握手完成")
                     // print pipeline for debugging
                     try {
                         println("Pipeline after handshake: ${channel.pipeline().toString()}")
                     } catch (_: Exception) {}
                     connectedAndHandshaked = true
                 } catch (e: java.util.concurrent.TimeoutException) {
                    println("WebSocket 握手超时 on attempt $attempt (${e.message}), dumping pipeline and closing channel")
                    try { println("Pipeline at timeout: ${channel.pipeline().toString()}") } catch (_: Exception) {}
                    try { channel.close().sync() } catch (_: Exception) {}
                    if (attempt < maxAttempts) Thread.sleep(1000)
                } catch (e: Exception) {
                    println("WebSocket 握手失败或超时 on attempt $attempt: ${e.message}")
                    try { channel.close().sync() } catch (_: Exception) {}
                    if (attempt < maxAttempts) Thread.sleep(1000)
                }
            }

            if (!::channel.isInitialized || !connectedAndHandshaked) {
                throw Exception("Failed to establish and handshake WebSocket after $maxAttempts attempts")
            }

            // 发送登录消息
            val loginWrapperBytes = buildLoginPayload(ServerConfig.Token)
            send(loginWrapperBytes, MsgType.LOGIN, ServerConfig.Token, 1) { success, _ ->
                if (success) {
                    println("登录成功")
                } else {
                    println("登录失败")
                }
            }
            // 启动心跳定时任务
//            startHeartbeat()
            channel.closeFuture().addListener(ChannelFutureListener {
                println("聊天服务器连接已关闭！")
            })
            channel.closeFuture().sync()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            shutdown()
            group.shutdownGracefully()
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                try {
                    val heartbeatTimeout = 5000L // 5秒超时
                    val heartbeatResult = CompletableDeferred<Boolean>()

                    // 构建心跳消息
                    val wrapperBytes = buildHeartbeatPayload()

                    send(wrapperBytes, MsgType.HEARTBEAT, ServerConfig.Token, 1) { success, _ ->
                        heartbeatResult.complete(success)
                    }
                    val ok = withTimeoutOrNull(heartbeatTimeout) { heartbeatResult.await() } ?: false
                    println(ok)
                    isServerConnected.value = ok
                } catch (e: Exception) {
                    isServerConnected.value = false
                }
                delay(heartbeatIntervalMillis)
            }
        }
    }

    /**
     * 发送信息到服务器（WebSocket）
     *
     * @param payload 已序列化的 protobuf 字节数组
     * @param type 消息类型
     * @param targetClientId 目标用户的ID
     * @param expectedResponses 预期的响应数量
     * @param callback 发送完成后的回调函数
     */
    fun send(
        payload: ByteArray,
        type: MsgType,
        targetClientId: String,
        expectedResponses: Int,
        callback: (Boolean, List<Any>) -> Unit
    ) {
        AppLog.d{"正在发送WebSocket消息"}
        // 如果握手尚未完成，延后发送以避免 WebSocket 编码器在 state=0 时接收到帧
        try {
            if (!this::responseHandler.isInitialized || !responseHandler.handshakeFuture.isDone) {
                println("Handshake 未完成，稍后发送消息")
                try {
                    if (this::responseHandler.isInitialized) {
                        responseHandler.handshakeFuture.whenComplete { _, ex ->
                            if (ex == null) {
                                try {
                                    send(payload, type, targetClientId, expectedResponses, callback)
                                } catch (e: Exception) {
                                    println("握手完成后发送失败: ${e.message}")
                                    callback(false, emptyList())
                                }
                            } else {
                                println("握手失败，无法发送消息: ${ex.message}")
                                callback(false, emptyList())
                            }
                        }
                        return
                    } else {
                        println("responseHandler 未初始化，无法排队发送")
                        callback(false, emptyList())
                        return
                    }
                } catch (e: Exception) {
                    println("排队发送时出错: ${e.message}")
                    callback(false, emptyList())
                    return
                }
            }
        } catch (_: Exception) {}

        if (::channel.isInitialized && channel.isActive) {
            // 发送二进制WebSocket帧
            responseHandler.setExpectedResponses(expectedResponses)

            // ensure write happens on channel event loop
            channel.eventLoop().execute {
                synchronized(sendLock) {
                    try {
                        println("Writing frame to channel, frame class: ${BinaryWebSocketFrame::class.java.name}")
                    } catch (_: Exception) {}
                    try {
                        val pipelineNames = channel.pipeline().names()
                        println("Pipeline before write: $pipelineNames")
                    } catch (_: Exception) {}
                     // allocate buffer from the channel's allocator to match pipeline expectations
                     val buf = channel.alloc().buffer(payload.size)
                     buf.writeBytes(payload)
                     val outFrame = BinaryWebSocketFrame(buf)
                     channel.writeAndFlush(outFrame).addListener { future ->
                         if (future.isSuccess) {
                             println("WebSocket消息发送成功")
                            // 非阻塞等待响应（带超时），避免阻塞 Netty 事件循环线程
                             try {
                                val timeoutSeconds = 30L // 调试时延长等待时间
                                val timeoutTask = channel.eventLoop().schedule({
                                    try {
                                        if (!responseHandler.responseFuture.isDone) {
                                            responseHandler.responseFuture.completeExceptionally(
                                                TimeoutException("等待响应超时")
                                            )
                                        }
                                    } catch (_: Exception) {}
                                }, timeoutSeconds, TimeUnit.SECONDS)

                                responseHandler.responseFuture.whenCompleteAsync { responses, ex ->
                                    try {
                                        try { timeoutTask.cancel(false) } catch (_: Exception) {}
                                        if (ex != null) {
                                            if (ex is TimeoutException) {
                                                println("等待响应超时: ${ex.message}")
                                            } else {
                                                println("等待响应时出现错误: ${ex.message}")
                                            }
                                            callback(false, emptyList())
                                        } else {
                                            callback(true, responses.map { it as Any })
                                        }
                                    } finally {
                                        // reset expectation after handling
                                        responseHandler.setExpectedResponses(1)
                                    }
                                }
                            } catch (e: Exception) {
                                println("等待响应时出现错误: ${e.message}")
                                callback(false, emptyList())
                                responseHandler.setExpectedResponses(1)
                            }
                         } else {
                             println("WebSocket消息发送失败: ${future.cause()}")
                             future.cause()?.let { t ->
                                 try {
                                     if (t is io.netty.handler.codec.EncoderException) {
                                         val names = channel.pipeline().names()
                                         println("EncoderException - pipeline names: $names")
                                     }
                                 } catch (_: Exception) {}
                                 t.printStackTrace()
                             }
                             callback(false, emptyList())
                             responseHandler.setExpectedResponses(1)
                         }
                     }
                 }
             }
         } else {
             println("Channel未初始化或已关闭，无法发送消息")
             callback(false, emptyList())
         }
     }

    fun sendText(
        content: String,
        callback: (Boolean) -> Unit
    ) {
        AppLog.d{"正在发送WebSocket文本消息"}
        if (::channel.isInitialized && channel.isActive) {
            // perform write on event loop to avoid encoder race conditions
            channel.eventLoop().execute {
                synchronized(sendLock) {
                    channel.writeAndFlush(TextWebSocketFrame(content)).addListener { future ->
                        if (future.isSuccess) {
                            println("WebSocket文本消息发送成功")
                            callback(true)
                        } else {
                            println("WebSocket文本消息发送失败: ${future.cause()}")
                            future.cause()?.printStackTrace()
                            callback(false)
                        }
                    }
                }
            }
         } else {
             println("Channel未初始化或已关闭，无法发送消息")
             callback(false)
         }
     }

    fun logoutAndDisconnect() {
        heartbeatJob?.cancel()

        if (!::channel.isInitialized) {
            return
        }

        try {
            if (!channel.isActive) {
                channel.close()
                return
            }

            val logoutWrapperBytes = buildLogoutPayload(ServerConfig.Token)
            channel.eventLoop().execute {
                try {
                    val buf = channel.alloc().buffer(logoutWrapperBytes.size)
                    buf.writeBytes(logoutWrapperBytes)
                    channel.writeAndFlush(BinaryWebSocketFrame(buf)).addListener {
                        try {
                            channel.close()
                        } catch (_: Exception) {
                        }
                    }
                } catch (_: Exception) {
                    try {
                        channel.close()
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
            try {
                channel.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun shutdown() {
        try {
            // 发送注销消息
            val logoutWrapperBytes = buildLogoutPayload(ServerConfig.Token)
            send(logoutWrapperBytes, MsgType.LOGOUT, ServerConfig.Token, 1) { success, _ ->
                if (success) {
                    println("注销消息发送成功")
                } else {
                    println("注销消息发送失败")
                }
            }
        } catch (e: Exception) {
            println("注销时发生错误: ${e.message}")
        } finally {
            try {
                if (::channel.isInitialized) {
                    channel.close().sync()
                }
            } catch (e: Exception) {
                println("关闭通道时发生错误: ${e.message}")
            }
        }
    }

    /**
     * Netty handler to log FullHttpResponse received during handshake (status, headers, body)
     * Install this handler before the WebSocket protocol handler to capture server rejection reasons.
     */
    class HttpResponseLogger : ChannelInboundHandlerAdapter() {
        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            try {
                if (msg is FullHttpResponse) {
                    try {
                        println("HTTP response during handshake: ${msg.status()} ${msg.protocolVersion()}")
                        println("Headers: ${msg.headers().toString()}")
                        val content = msg.content()
                        if (content.isReadable) {
                            val bytes = ByteArray(content.readableBytes())
                            content.readBytes(bytes)
                            val s = try { String(bytes, Charsets.UTF_8) } catch (_: Exception) { "<binary>" }
                            println("Response body: $s")
                        }
                    } catch (e: Exception) {
                        println("Failed to log HTTP response: ${e.message}")
                    }
                } else {
                    println("HttpResponseLogger saw message of type: ${msg.javaClass.name}")
                }
            } finally {
                ctx.fireChannelRead(msg)
            }
        }
    }
}
