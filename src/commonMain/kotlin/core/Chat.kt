package core

import core.ServerConfig.Token
import model.Message
import model.GroupMessage
import model.messages
import model.groupMessages
import androidx.compose.runtime.mutableStateOf
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.*
import java.net.URI
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.*
import androidx.compose.runtime.mutableStateOf

// 定义发送类型枚举，统一管理后端协议中的字符串
enum class MsgType(val wire: String) {
    LOGIN("login"),
    LOGOUT("logout"),
    CHAT("chat"),
    GROUP_CHAT("groupChat"),
    CHECK("check"),
    HEARTBEAT("heartbeat");
}

// 统一的API解包结果与提示
private data class ApiUnwrap(
    val hasEnvelope: Boolean,
    val success: Boolean,
    val dataJson: String?,
    val message: String?
)

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
    } catch (e: Exception) {
        ApiUnwrap(hasEnvelope = false, success = true, dataJson = content, message = null)
    }
}

private fun prompt(msg: String?) {
    if (!msg.isNullOrBlank()) println("提示: $msg")
}

/**
 * 处理接收到的http信息
 */
class CustomHttpResponseHandler : SimpleChannelInboundHandler<FullHttpResponse>() {
    private val responses = mutableListOf<FullHttpResponse>()
    private var expectedResponses = 1
    var responseFuture = CompletableFuture<List<FullHttpResponse>>()

    override fun channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse) {
        val content = msg.content().toString(Charsets.UTF_8)
        println("Received message: $content")

        if (responses.size < expectedResponses) {
            responses.add(msg)
            if (responses.size == expectedResponses) {
                val unwrap = unwrapApi(content)
                if (unwrap.hasEnvelope && !unwrap.success) {
                    prompt(unwrap.message)
                }
                val dataStr = unwrap.dataJson ?: content
                if (dataStr.trim().startsWith("{") && dataStr.trim().endsWith("}")){
                    val json = jacksonObjectMapper().readTree(dataStr)
                    val messageId = json.get("messageId")?.asText()

                    if (messageId != null){
                        handleIncomingFromJson(json)
                    }
                }

                responseFuture.complete(responses)
                responses.clear()
            }
        } else {
            // Handle unsolicited messages (e.g., messages from other users)
            println("接收到了来自外界的信息: $content")
            val unwrap = unwrapApi(content)
            if (unwrap.hasEnvelope && !unwrap.success) {
                prompt(unwrap.message)
            } else {
                val dataStr = unwrap.dataJson ?: content
                handleIncomingMessage(dataStr)
            }
        }
    }

//    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
//        println("收到哈哈哈信息: $msg")
//        super.channelRead(ctx, msg)
//    }

    private fun handleIncomingFromJson(json: JsonNode) {
        try{
            val senderId = json.get("id")?.asInt() ?: throw IllegalArgumentException("Missing senderId")
            val messageId = json.get("messageId")?.asText()
            val timestamp = json.get("timestamp")?.asLong() ?: throw IllegalArgumentException("Missing timestamp")
            val sender = false
            val text = json.get("text")?.asText() ?: throw IllegalArgumentException("Missing content")
            messages += Message(
                senderId = senderId,
                message = text,
                sender = sender,
                timestamp = timestamp,
                isSent = mutableStateOf(true),
                messageId = messageId ?: ""
            )
        } catch (E: Exception){
            println("Error handling incoming message: ${E.message}")
            E.printStackTrace()
        }
    }

    private fun handleIncomingMessage(msg: FullHttpResponse) {
        try{
            val raw = msg.content().toString(Charsets.UTF_8)
            val unwrap = unwrapApi(raw)
            if (unwrap.hasEnvelope && !unwrap.success) {
                prompt(unwrap.message)
                return
            }
            val dataStr = unwrap.dataJson ?: raw
            val json = jacksonObjectMapper().readTree(dataStr)
            handleIncomingFromJson(json)
        } catch (E: Exception){
            println("Error handling incoming message: ${E.message}")
            E.printStackTrace()
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

    fun setExpectedResponses(count: Int) {
        expectedResponses = count
        responses.clear()
        responseFuture = CompletableFuture() // Reset the future for a new request
    }
}

/**
 * Netty构建的功能类
 */
object Chat {
    private lateinit var channel: Channel
    private var host = ServerConfig.SERVER_IP
    private var port = ServerConfig.NETTY_SERVER_PORT
    private lateinit var responseHandler: CustomHttpResponseHandler

    val isServerConnected = mutableStateOf(true)
    private var heartbeatJob: Job? = null
    private val heartbeatIntervalMillis = 10000L // 10秒

    fun start(newHost: String = host, newPort: Int = port) {
        host = newHost
        port = newPort
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
                        ch.pipeline().addLast(HttpClientCodec())
                        ch.pipeline().addLast(HttpObjectAggregator(8192))
                        responseHandler = CustomHttpResponseHandler()
                        ch.pipeline().addLast(responseHandler)
                    }
                })

            val channelFuture: ChannelFuture = b.connect(host, port).sync()
            channel = channelFuture.channel()
            println("后端服务器连接成功！")

            // 启动心跳定时任务
            startHeartbeat()

            send("", MsgType.LOGIN, ServerConfig.Token,1) { success, responses ->
                if (success) {
                    println("登录成功")
                    responses.forEach { response ->
                        println("Response: $response")
                    }
                } else {
                    println("Error: $responses")
                }
            }
            channel.closeFuture().addListener(ChannelFutureListener {
                println("启动聊天服务器服务！")
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
                    send("", MsgType.HEARTBEAT, ServerConfig.Token, 1) { success, _ ->
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
     * 发送信息到服务器
     *
     * @param message 发送的信息
     * @param type 发送的信息类型
     * @param targetClientId 目标用户的ID
     * @param expectedResponses 预期的响应数量
     * @param callback 发送完成后的回调函数
     */
    fun send(
        message: String,
        type: MsgType,
        targetClientId: String,
        expectedResponses: Int,
        callback: (Boolean, List<FullHttpResponse>) -> Unit
    ) {
        println("正在发送信息")
        if (::channel.isInitialized) {
            val uri = URI("http://$host:$port/send")
            val json = jacksonObjectMapper().writeValueAsString(
                mapOf(
                    "content" to message,
                    "type" to type.wire,
                    "targetClientId" to targetClientId,
                    "timestamp" to System.currentTimeMillis(),
                )
            )

            val request = DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.POST,
                uri.rawPath,
                Unpooled.wrappedBuffer(json.toByteArray(Charsets.UTF_8))
            )
            request.headers().set(HttpHeaderNames.HOST, host)
            request.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
            request.headers().set(HttpHeaderNames.CONTENT_LENGTH, request.content().readableBytes())
            request.headers().set(HttpHeaderNames.AUTHORIZATION, "Bearer $Token")

            responseHandler.setExpectedResponses(expectedResponses)

            channel.writeAndFlush(request).addListener { future ->
                if (future.isSuccess) {
//                    println("设定的期望响应数：$expectedResponses")
                    responseHandler.responseFuture.thenAccept { responses ->
                        // 统一按业务code判断成功，并在失败时提示
                        var allOk = true
                        var firstErrMsg: String? = null
                        responses.forEach { r ->
                            val body = r.content().toString(Charsets.UTF_8)
                            val unwrap = unwrapApi(body)
                            if (unwrap.hasEnvelope) {
                                if (!unwrap.success) {
                                    allOk = false
                                    if (firstErrMsg == null) firstErrMsg = unwrap.message
                                }
                            }
                        }
                        if (!allOk) prompt(firstErrMsg)
                        callback(allOk, responses)
                    }.exceptionally { throwable: Throwable ->
                        callback(
                            false,
                            listOf(
                                DefaultFullHttpResponse(
                                    HttpVersion.HTTP_1_1,
                                    HttpResponseStatus.INTERNAL_SERVER_ERROR
                                )
                            )
                        )
                        null
                    }
                } else {
                    println("发送信息的时发生错误！！")
                    future.cause().printStackTrace()
                    callback(
                        false,
                        listOf(DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR))
                    )
                }
            }
        } else {
            println("Channel is not initialized")
            callback(
                false,
                listOf(DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR))
            )
        }
        responseHandler.setExpectedResponses(1)
    }

    /**
     * 关闭连接
     */
    fun shutdown() {
        println("正在关闭")
        heartbeatJob?.cancel()
        val shutdownCompleted = CompletableFuture<Boolean>()
        send("Shutting down", MsgType.LOGOUT, "0", 1) { success, _ ->
            if (success) {
                println("Shutdown message sent successfully")
            } else {
                println("Failed to send shutdown message")
            }
            shutdownCompleted.complete(true)
            if (::channel.isInitialized) {
                channel.close()
            }
        }
        // 等待发送完成
        shutdownCompleted.get()
    }
}

fun main() {
    Runtime.getRuntime().addShutdownHook(Thread {
        Chat.shutdown()
    })
    Chat.start("localhost", 8080)
}