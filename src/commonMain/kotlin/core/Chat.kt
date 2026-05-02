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
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import component.appendAgentChunk
import kotlin.time.Duration.Companion.milliseconds

/**
 * 全局消息接收回调接口
 */
interface MessageReceiveListener {
    /**
     * 收到私聊消息
     */
    fun onPrivateMessageReceived(senderId: Int, message: String, timestamp: Long)

    /**
     * 收到群聊消息
     */
    fun onGroupMessageReceived(groupId: Int, senderId: Int, senderName: String, message: String, timestamp: Long)
}

// 定义发送类型枚举，统一管理后端协议中的字符串
enum class MsgType(val wire: String) {
    LOGIN("login"),
    LOGOUT("logout"),
    CHAT("chat"),
    AGENT_CHAT("agentChat"),
    AGENT_CHAT_STREAM("agentChatStream"),
    GROUP_CHAT("groupChat"),
    CHECK("check"),
    HEARTBEAT("heartbeat"),
    ACK("ack");
}

// ApiUnwrap is defined in core.ApiUnwrap (ApiUnwrap.kt)

/**
 * 处理接收到的WebSocket信息
 */
class CustomWebSocketHandler : SimpleChannelInboundHandler<Any>() {
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

    // store raw binary responses
    private val responses = mutableListOf<ByteArray>()
    private var expectedResponses = 1
    private var pendingMsgType: MsgType? = null
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
                    AppLog.d{"Binary unwrap result: hasEnvelope=${unwrap.hasEnvelope}, success=${unwrap.success}, message=${unwrap.message?.take(128)}"}
                    if (unwrap.hasEnvelope && !unwrap.success) {
                        prompt(unwrap.message)
                    } else {
                        val rawData = unwrap.dataJson ?: String(bodyBytes, Charsets.UTF_8)
                        val dataStr = rawData.trim().trimStart('\uFEFF', '\uFFFE')
                        if (dataStr.startsWith("{") && dataStr.endsWith("}")) {
                            val json = jacksonObjectMapper().readTree(dataStr)
                            val typeText = json.get("type")?.asText()
                            if (typeText == "heartbeat") {
                                AppLog.d{"Received heartbeat frame"}
                                if (pendingMsgType != MsgType.HEARTBEAT) {
                                    AppLog.d{"Ignoring heartbeat while waiting for ${pendingMsgType?.wire ?: "response"}"}
                                    return
                                }
                                // heartbeat response is expected for heartbeat requests
                            }
                            if (json.has("type") && json.has("payload")) {
                                AppLog.i({"Binary websocket envelope contains proto wrapper: ${json.get("type").asText()}"})
                                handleIncomingFromProtoWrapper(json)
                            } else if (json.has("messageId")) {
                                AppLog.i({"Binary websocket JSON contains messageId, routing to handleIncomingFromJson"})
                                handleIncomingFromJson(json)
                            } else if (json.has("clientId") && json.has("online")) {
                                AppLog.i({"Binary websocket JSON contains CHECK response for clientId=${json.get("clientId").asText()}, callback will process it"})
                            } else if (typeText == "heartbeat") {
                                // ignore heartbeat messages that are not direct responses to send()
                            } else {
                                AppLog.w({"Binary websocket JSON did not contain expected fields: ${json.toString().take(256)}"})
                                handleIncomingMessage(dataStr)
                            }
                        } else {
                            AppLog.d{"Binary message content is not JSON after unwrap: ${dataStr.take(64)}"}
                        }
                    }
                } catch (e: Exception) {
                    AppLog.e({"Binary WebSocket parse error: ${e.message}"}, e)
                }

                // collect response for callers waiting in send()
                val shouldCollectResponse = try {
                    val dataStr = String(bodyBytes, Charsets.UTF_8).trim().trimStart('\uFEFF', '\uFFFE')
                    if (dataStr.startsWith("{") && dataStr.endsWith("}")) {
                        val json = jacksonObjectMapper().readTree(dataStr)
                        val typeText = json.get("type")?.asText()
                        if (typeText == "heartbeat" && pendingMsgType != MsgType.HEARTBEAT) {
                            false
                        } else {
                            true
                        }
                    } else {
                        true
                    }
                } catch (_: Exception) {
                    true
                }

                if (shouldCollectResponse) {
                    synchronized(this) {
                        responses.add(bodyBytes)
                        if (responses.size >= expectedResponses && !responseFuture.isDone) {
                            responseFuture.complete(responses.toList())
                        }
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

            if (!messageId.isNullOrBlank()) {
                sendAck(messageId)
            }

            // 消息去重
            var isDuplicate = false
            if (messageId != null && messageId.isNotBlank()) {
                isDuplicate = runBlocking {
                    Chat.messageCacheMutex.withLock {
                        if (Chat.receivedMessageIds.contains(messageId)) {
                            AppLog.d{"收到重复消息，忽略: $messageId"}
                            true
                        } else {
                            // 添加到去重缓存
                            Chat.receivedMessageIds.add(messageId)
                            // 缓存超过大小，移除最旧的
                            if (Chat.receivedMessageIds.size > Chat.maxMessageCacheSize) {
                                val iterator = Chat.receivedMessageIds.iterator()
                                if (iterator.hasNext()) {
                                    iterator.next()
                                    iterator.remove()
                                }
                            }
                            false
                        }
                    }
                }
            }
            if (isDuplicate) return

            val message = Message(
                senderId = senderId,
                message = text,
                sender = sender,
                timestamp = timestamp,
                isSent = mutableStateOf(true),
                messageId = messageId ?: ""
            )
            messages += message
            try { ActionLogger.log(Action(type = ActionType.RECEIVE_MESSAGE, targetId = senderId.toString(), metadata = mapOf("text" to text.take(64)))) } catch (_: Exception) {}

            // 通知消息监听器
            synchronized(Chat.messageListeners) {
                Chat.messageListeners.forEach {
                    it.onPrivateMessageReceived(senderId, text, timestamp)
                }
            }
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
                    val messageIdField = payload.get("messageId")?.asText()
                    val messageId = messageIdField?.takeIf { it.isNotBlank() } ?: payload.get("timestamp")?.asText().orEmpty()

                    if (!messageIdField.isNullOrBlank()) {
                        sendAck(messageIdField)
                    }

                    // 消息去重
                    var isDuplicate = false
                    if (messageId.isNotBlank()) {
                        isDuplicate = runBlocking {
                            Chat.messageCacheMutex.withLock {
                                if (Chat.receivedMessageIds.contains(messageId)) {
                                    AppLog.d{"收到重复消息，忽略: $messageId"}
                                    true
                                } else {
                                    Chat.receivedMessageIds.add(messageId)
                                    if (Chat.receivedMessageIds.size > Chat.maxMessageCacheSize) {
                                        val iterator = Chat.receivedMessageIds.iterator()
                                        if (iterator.hasNext()) {
                                            iterator.next()
                                            iterator.remove()
                                        }
                                    }
                                    false
                                }
                            }
                        }
                    }
                    if (isDuplicate) return

                    val message = Message(
                        senderId = senderId,
                        message = text,
                        sender = false,
                        timestamp = ts,
                        isSent = mutableStateOf(true),
                        messageId = messageId
                    )
                    messages += message
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

                    // 通知消息监听器
                    synchronized(Chat.messageListeners) {
                        Chat.messageListeners.forEach {
                            it.onPrivateMessageReceived(senderId, text, ts)
                        }
                    }
                }

                MsgType.GROUP_CHAT.wire -> {
                    val groupId = payload.get("targetClientId")?.asText()?.toIntOrNull() ?: return
                    val senderId = payload.get("userId")?.asText()?.toIntOrNull() ?: return
                    val text = payload.get("content")?.asText() ?: return
                    val messageIdField = payload.get("messageId")?.asText()
                    val messageId = messageIdField?.takeIf { it.isNotBlank() } ?: payload.get("timestamp")?.asText().orEmpty()

                    if (!messageIdField.isNullOrBlank()) {
                        sendAck(messageIdField)
                    }

                    // 消息去重
                    var isDuplicate = false
                    if (messageId.isNotBlank()) {
                        isDuplicate = runBlocking {
                            Chat.messageCacheMutex.withLock {
                                val uniqueId = "group_${groupId}_${senderId}_$messageId"
                                if (Chat.receivedMessageIds.contains(uniqueId)) {
                                    AppLog.d{"收到重复群聊消息，忽略: $uniqueId"}
                                    true
                                } else {
                                    Chat.receivedMessageIds.add(uniqueId)
                                    if (Chat.receivedMessageIds.size > Chat.maxMessageCacheSize) {
                                        val iterator = Chat.receivedMessageIds.iterator()
                                        if (iterator.hasNext()) {
                                            iterator.next()
                                            iterator.remove()
                                        }
                                    }
                                    false
                                }
                            }
                        }
                    }
                    if (isDuplicate) return

                    val groupMessage = GroupMessage(
                        groupId = groupId,
                        senderName = senderId.toString(),
                        text = text,
                        senderId = senderId,
                        timestamp = System.currentTimeMillis(),
                        isSent = mutableStateOf(true),
                        messageId = messageId
                    )
                    groupMessages += groupMessage
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

                    // 通知消息监听器
                    synchronized(Chat.messageListeners) {
                        Chat.messageListeners.forEach {
                            it.onGroupMessageReceived(groupId, senderId, senderId.toString(), text, groupMessage.timestamp)
                        }
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

    private fun sendAck(messageId: String) {
        if (messageId.isBlank()) return
        try {
            val ackPayload = buildAckPayload(messageId)
            Chat.send(ackPayload, MsgType.ACK, ServerConfig.Token, 1) { success, _ ->
                if (!success) {
                    AppLog.w({"ACK发送失败: $messageId"})
                }
            }
        } catch (e: Exception) {
            AppLog.e({"ACK发送异常: ${e.message}"}, e)
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
                "heartbeat" -> {
                    AppLog.d({"Received heartbeat message"})
                    return
                }
                else -> {
                    if (messageType.contains("登录失败") || messageType.contains("失败") || messageType.contains("error", ignoreCase = true)) {
                        AppLog.w({"收到登录/服务端错误: $messageType"})
                        prompt(messageType)
                        return
                    }
                }
            }

            when (messageType) {
                "chat" -> {
                    println("New private message from $senderName: $messageContent")
                    val timestamp = System.currentTimeMillis()
                    val message = Message(
                        senderId = userId,
                        message = messageContent,
                        sender = false,
                        timestamp = timestamp,
                        isSent = mutableStateOf(true)
                    )
                    messages += message
                    try { ActionLogger.log(Action(type = ActionType.RECEIVE_MESSAGE, targetId = userId.toString(), metadata = mapOf("text" to messageContent.take(64)))) } catch (_: Exception) {}

                    // 通知消息监听器
                    synchronized(Chat.messageListeners) {
                        Chat.messageListeners.forEach {
                            it.onPrivateMessageReceived(userId, messageContent, timestamp)
                        }
                    }
                }
                "groupChat" -> {
                    val groupId = json.get("groupId")?.asInt() ?: throw IllegalArgumentException("Missing groupId")
                    println("New group message in group $groupId from $senderName: $messageContent")
                    val timestamp = System.currentTimeMillis()
                    val groupMessage = GroupMessage(
                        groupId = groupId,
                        senderName = senderName,
                        text = messageContent,
                        senderId = userId,
                        timestamp = timestamp,
                        isSent = mutableStateOf(true)
                    )
                    groupMessages += groupMessage
                    try { ActionLogger.log(Action(type = ActionType.RECEIVE_MESSAGE, targetId = groupId.toString(), metadata = mapOf("text" to messageContent.take(64), "group" to "true"))) } catch (_: Exception) {}

                    // 通知消息监听器
                    synchronized(Chat.messageListeners) {
                        Chat.messageListeners.forEach {
                            it.onGroupMessageReceived(groupId, userId, senderName, messageContent, timestamp)
                        }
                    }
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

    fun setExpectedResponses(count: Int, msgType: MsgType? = null) {
        synchronized(this) {
            expectedResponses = count
            pendingMsgType = msgType
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
        // 强制使用WSS 443端口，避免301重定向
        private var port = 443
        private lateinit var responseHandler: CustomWebSocketHandler
        private val sendLock = Any()

    internal val messageListeners = mutableListOf<MessageReceiveListener>()

    fun addMessageReceiveListener(listener: MessageReceiveListener) {
        synchronized(messageListeners) {
            if (!messageListeners.contains(listener)) {
                messageListeners.add(listener)
            }
        }
    }

    fun removeMessageReceiveListener(listener: MessageReceiveListener) {
        synchronized(messageListeners) {
            messageListeners.remove(listener)
        }
    }

    val isServerConnected = mutableStateOf(true)
    private var heartbeatJob: Job? = null
    private val heartbeatIntervalMillis = 30000L // 30秒心跳
    private val heartbeatTimeoutMillis = 5000L // 心跳超时5秒

    // 自动重连配置
    private var reconnectJob: Job? = null
    private val maxReconnectDelay = 30000L // 最大重连间隔30秒
    private val initialReconnectDelay = 1000L // 初始重连间隔1秒
    private var currentReconnectDelay = initialReconnectDelay
    private val isReconnecting = AtomicBoolean(false)
    private val stopReconnect = AtomicBoolean(false)

    // 消息队列，网络断开时缓存待发送消息
    private val messageQueue = ConcurrentLinkedQueue<PendingMessage>()
    private val queueMutex = Mutex()
    private val maxQueueSize = 1000 // 最大队列长度，防止内存溢出

    // 消息去重缓存，存储最近收到的消息ID
    // Make these visible to other classes in the module (e.g. CustomWebSocketHandler)
    internal val receivedMessageIds = mutableSetOf<String>()
    internal val maxMessageCacheSize = 1000 // 最多缓存1000条消息ID
    internal val messageCacheMutex = Mutex()

    // 待发送消息封装
    private data class PendingMessage(
        val payload: ByteArray,
        val type: MsgType,
        val targetClientId: String,
        val expectedResponses: Int,
        val callback: (Boolean, List<Any>) -> Unit,
        val timestamp: Long = System.currentTimeMillis(),
        val retryCount: Int = 0,
        val maxRetries: Int = 3
    )

    // helper: strip explicit port from host string if present (simple IPv4/hostname handling)
    private fun hostWithoutPort(rawHost: String?): String {
        if (rawHost.isNullOrBlank()) return ""
        // handle cases like "hostname:8080" -> "hostname"; IPv6 with [] is uncommon here
        return rawHost.substringBefore(':')
    }

    fun start(newHost: String = host, newPort: Int = if (NetworkConstants.WS_PROTOCOL == "wss") 443 else 80) {
        // normalize host to remove any provided ":port" suffix
        host = newHost
        // 根据协议自动选择端口：WSS用443，WS用80
        port = newPort
        stopReconnect.set(false)

        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(Thread {
            shutdown()
        })

        try {
            startInternal()

            // 阻塞等待连接关闭
            channel.closeFuture().sync()
        } catch (e: Exception) {
            AppLog.e({ "连接失败: ${e.message}" }, e)
            // 连接失败，启动重连
            if (!stopReconnect.get() && isReconnecting.compareAndSet(false, true)) {
                currentReconnectDelay = initialReconnectDelay
                startReconnectLoop()
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    if (!isConnected()) {
                        delay(1000.milliseconds)
                        continue
                    }

                    val heartbeatResult = CompletableDeferred<Boolean>()

                    // 构建心跳消息
                    val wrapperBytes = buildHeartbeatPayload()

                    sendInternal(wrapperBytes, MsgType.HEARTBEAT, ServerConfig.Token, 1) { success, _ ->
                        heartbeatResult.complete(success)
                    }

                    val ok = withTimeoutOrNull(heartbeatTimeoutMillis.milliseconds) {
                        heartbeatResult.await()
                    } ?: false

                    isServerConnected.value = ok

                    if (!ok) {
                        AppLog.w({ "心跳超时，连接可能已断开" })
                        // 心跳失败，关闭连接触发重连
                        channel.close()
                        break
                    }
                } catch (e: Exception) {
                    AppLog.e({ "心跳发送失败: ${e.message}" })
                    isServerConnected.value = false
                    // 关闭连接触发重连
                    if (::channel.isInitialized && channel.isActive) {
                        runCatching { channel.close() }
                    }
                    break
                }
                delay(heartbeatIntervalMillis.milliseconds)
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

        // 心跳消息不缓存，直接丢弃
        if (type == MsgType.HEARTBEAT && !isConnected()) {
            callback(false, emptyList())
            return
        }

        // 如果未连接，将消息加入队列
        if (!isConnected()) {
            AppLog.d{"连接未建立，消息加入队列: $type -> $targetClientId"}
            CoroutineScope(Dispatchers.IO).launch {
                enqueueMessage(
                    PendingMessage(
                        payload = payload,
                        type = type,
                        targetClientId = targetClientId,
                        expectedResponses = expectedResponses,
                        callback = callback
                    )
                )
            }
            return
        }

        // 连接已建立，直接发送
        sendInternal(payload, type, targetClientId, expectedResponses, callback)
    }

    /**
     * 内部发送方法，直接发送消息不经过队列
     */
    private fun sendInternal(
        payload: ByteArray,
        type: MsgType,
        targetClientId: String,
        expectedResponses: Int,
        callback: (Boolean, List<Any>) -> Unit
    ) {
        // 如果握手尚未完成，延后发送以避免 WebSocket 编码器在 state=0 时接收到帧
        try {
            if (!this::responseHandler.isInitialized || !responseHandler.handshakeFuture.isDone) {
                AppLog.d{"Handshake 未完成，稍后发送消息"}
                try {
                    if (this::responseHandler.isInitialized) {
                        responseHandler.handshakeFuture.whenComplete { _, ex ->
                            if (ex == null) {
                                try {
                                    sendInternal(payload, type, targetClientId, expectedResponses, callback)
                                } catch (e: Exception) {
                                    AppLog.d{"握手完成后发送失败: ${e.message}"}
                                    callback(false, emptyList())
                                }
                            } else {
                                AppLog.d{"握手失败，无法发送消息: ${ex.message}"}
                                callback(false, emptyList())
                            }
                        }
                        return
                    } else {
                        AppLog.d{"responseHandler 未初始化，无法发送"}
                        callback(false, emptyList())
                        return
                    }
                } catch (e: Exception) {
                    AppLog.d{"发送时出错: ${e.message}"}
                    callback(false, emptyList())
                    return
                }
            }
        } catch (_: Exception) {}

        if (::channel.isInitialized && channel.isActive) {
            // 发送二进制WebSocket帧
            responseHandler.setExpectedResponses(expectedResponses, type)

            // ensure write happens on channel event loop
            channel.eventLoop().execute {
                synchronized(sendLock) {
                    try {
                        AppLog.d{"Writing frame to channel, frame class: ${BinaryWebSocketFrame::class.java.name}"}
                    } catch (_: Exception) {}
                     // allocate buffer from the channel's allocator to match pipeline expectations
                     val buf = channel.alloc().buffer(payload.size)
                     buf.writeBytes(payload)
                     val outFrame = BinaryWebSocketFrame(buf)
                     channel.writeAndFlush(outFrame).addListener { future ->
                         if (future.isSuccess) {
                             AppLog.d{"WebSocket消息发送成功"}
                            // 非阻塞等待响应（带超时），避免阻塞 Netty 事件循环线程
                             try {
                                val timeoutSeconds = if (type == MsgType.HEARTBEAT) 5L else 30L
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
                                                AppLog.d{"等待响应超时: ${ex.message}"}
                                            } else {
                                                AppLog.d{"等待响应时出现错误: ${ex.message}"}
                                            }
                                            callback(false, emptyList())
                                        } else {
                                            val isValid = try {
                                                responses.all { bytes ->
                                                    val unwrap = parseProtoResponse(bytes)
                                                    if (unwrap.hasEnvelope && !unwrap.success) {
                                                        false
                                                    } else {
                                                        val rawData = unwrap.dataJson ?: String(bytes, Charsets.UTF_8)
                                                        val dataStr = rawData.trim().trimStart('\uFEFF', '\uFFFE')
                                                        if (dataStr.startsWith("{") && dataStr.endsWith("}")) {
                                                            val json = jacksonObjectMapper().readTree(dataStr)
                                                            val typeText = json.get("type")?.asText()
                                                            if (!typeText.isNullOrBlank() && (typeText.contains("失败") || typeText.contains("error", ignoreCase = true))) {
                                                                false
                                                            } else {
                                                                true
                                                            }
                                                        } else {
                                                            true
                                                        }
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                true
                                            }
                                            callback(isValid, if (isValid) responses.map { it as Any } else emptyList())
                                        }
                                    } finally {
                                        // reset expectation after handling
                                        responseHandler.setExpectedResponses(1)
                                    }
                                }
                            } catch (e: Exception) {
                                AppLog.d{"等待响应时出现错误: ${e.message}"}
                                callback(false, emptyList())
                                responseHandler.setExpectedResponses(1)
                            }
                         } else {
                             AppLog.d{"WebSocket消息发送失败: ${future.cause()}"}
                             future.cause()?.let { t ->
                                 try {
                                     if (t is io.netty.handler.codec.EncoderException) {
                                         val names = channel.pipeline().names()
                                         AppLog.d{"EncoderException - pipeline names: $names"}
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
             AppLog.d{"Channel未初始化或已关闭，无法发送消息"}
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

    /**
     * 检查是否已连接
     */
    fun isConnected(): Boolean {
        return ::channel.isInitialized && channel.isActive && isServerConnected.value
    }

    /**
     * 手动重连
     */
    fun reconnect() {
        if (isReconnecting.compareAndSet(false, true)) {
            stopReconnect.set(false)
            currentReconnectDelay = initialReconnectDelay
            startReconnectLoop()
        }
    }

    /**
     * 启动重连循环
     */
    private fun startReconnectLoop() {
        reconnectJob?.cancel()
        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && !stopReconnect.get() && !isConnected()) {
                try {
                    AppLog.i({ "尝试重连，延迟 ${currentReconnectDelay}ms..." })
                    delay(currentReconnectDelay.milliseconds)

                    // 尝试重新连接
                    val result = runCatching {
                        startInternal()
                    }

                    if (result.isSuccess) {
                        AppLog.i({ "重连成功" })
                        isServerConnected.value = true
                        isReconnecting.set(false)
                        // 重连成功，发送队列中缓存的消息
                        flushMessageQueue()
                        break
                    } else {
                        AppLog.e({ "重连失败: ${result.exceptionOrNull()?.message}" })
                        // 指数退避
                        currentReconnectDelay = minOf(currentReconnectDelay * 2, maxReconnectDelay)
                    }
                } catch (e: Exception) {
                    AppLog.e({ "重连过程出错: ${e.message}" })
                    delay(currentReconnectDelay.milliseconds)
                    currentReconnectDelay = minOf(currentReconnectDelay * 2, maxReconnectDelay)
                }
            }
        }
    }

    /**
     * 添加消息到发送队列
     */
    private suspend fun enqueueMessage(message: PendingMessage) {
        queueMutex.withLock {
            if (messageQueue.size >= maxQueueSize) {
                // 队列已满，移除最旧的消息
                messageQueue.poll()
                AppLog.w({ "消息队列已满，丢弃最旧消息" })
            }
            messageQueue.add(message)
        }
    }

    /**
     * 刷新消息队列，发送所有缓存的消息
     */
    private suspend fun flushMessageQueue() {
        queueMutex.withLock {
            if (messageQueue.isEmpty()) return

            AppLog.i({ "开始发送队列中的消息，共 ${messageQueue.size} 条" })
            val iterator = messageQueue.iterator()
            while (iterator.hasNext()) {
                val pending = iterator.next()
                iterator.remove()

                if (pending.retryCount >= pending.maxRetries) {
                    AppLog.w({ "消息重试次数超过上限，丢弃: ${pending.type} -> ${pending.targetClientId}" })
                    pending.callback(false, emptyList())
                    continue
                }

                // 重新发送
                sendInternal(
                    payload = pending.payload,
                    type = pending.type,
                    targetClientId = pending.targetClientId,
                    expectedResponses = pending.expectedResponses,
                    callback = { success, responses ->
                        if (success) {
                            pending.callback(true, responses)
                        } else {
                            // 发送失败，重新入队
                            CoroutineScope(Dispatchers.IO).launch {
                                enqueueMessage(pending.copy(retryCount = pending.retryCount + 1))
                            }
                        }
                    }
                )
            }
        }
    }

    /**
     * 内部连接方法，供重连使用
     */
    private fun startInternal() {
        // 先关闭现有连接
        if (::channel.isInitialized && channel.isActive) {
            runCatching { channel.close().sync() }
        }

        val effectiveHost = hostWithoutPort(host)
        val group = NioEventLoopGroup()

        try {
            val b = Bootstrap()
            b.group(group)
                .channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<Channel>() {
                    override fun initChannel(ch: Channel) {
                        val pipeline = ch.pipeline()

                        // WSS协议添加SSL处理器
                        if (NetworkConstants.WS_PROTOCOL == "wss") {
                            val sslContext = io.netty.handler.ssl.SslContextBuilder.forClient()
                                .trustManager(io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE)
                                .build()
                            pipeline.addLast(sslContext.newHandler(ch.alloc(), effectiveHost, port))
                        }

                        pipeline.addLast(HttpClientCodec())
                        pipeline.addLast(HttpObjectAggregator(8192))

                        val uidQuery = try {
                            val idVal = ServerConfig.id
                            if (idVal.isNotBlank()) "?clientId=${idVal}" else ""
                        } catch (_: Exception) {
                            ""
                        }
                        // 使用配置的WebSocket地址，自动适配WSS/WS协议
                        val wsUri = URI(NetworkConstants.wsUrl())
                        val effectiveHost = wsUri.host

                        val headers = DefaultHttpHeaders()
                        val tokenForHandshake = try { ServerConfig.Token } catch (_: Exception) { "" }
                        if (tokenForHandshake.isNotBlank()) {
                            headers.set("Authorization", "Bearer $tokenForHandshake")
                        }
                        try { headers.set(HttpHeaderNames.HOST.toString(), effectiveHost) } catch (_: Exception) {}
                        try { headers.set("Origin", NetworkConstants.wsOrigin()) } catch (_: Exception) {}

                        val handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                            wsUri,
                            WebSocketVersion.V13,
                            null,
                            true,
                            headers,
                            64 * 1024
                        )

                        pipeline.addLast(WebSocketClientProtocolHandler(handshaker, true))
                        responseHandler = CustomWebSocketHandler()
                        pipeline.addLast(responseHandler)
                    }
                })

            // 连接
            val f = b.connect(effectiveHost, port).sync()
            channel = f.channel()

            // 等待握手完成
            responseHandler.handshakeFuture.get(10, TimeUnit.SECONDS)
            AppLog.i({ "WebSocket连接成功" })

            // 发送登录消息
            val loginWrapperBytes = buildLoginPayload(ServerConfig.Token)
            sendInternal(loginWrapperBytes, MsgType.LOGIN, ServerConfig.Token, 1) { success, _ ->
                if (success) {
                    AppLog.i({ "登录成功" })
                } else {
                    AppLog.e({ "登录失败" })
                    // 关闭连接触发重连逻辑，不要抛出异常导致APP崩溃
                    channel.close()
                }
            }

            // 启动心跳
            startHeartbeat()

            // 监听连接关闭
            channel.closeFuture().addListener {
                AppLog.i({ "连接已关闭" })
                isServerConnected.value = false
                heartbeatJob?.cancel()

                // 如果不是主动关闭，启动重连
                if (!stopReconnect.get() && isReconnecting.compareAndSet(false, true)) {
                    currentReconnectDelay = initialReconnectDelay
                    startReconnectLoop()
                }
            }

        } catch (e: Exception) {
            group.shutdownGracefully()
            throw e
        }
    }

    fun logoutAndDisconnect() {
        stopReconnect.set(true)
        reconnectJob?.cancel()
        heartbeatJob?.cancel()

        // 清空消息队列
        runBlocking {
            queueMutex.withLock {
                messageQueue.forEach { it.callback(false, emptyList()) }
                messageQueue.clear()
            }
        }

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
