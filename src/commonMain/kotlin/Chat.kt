import ServerConfig.Token
import androidx.compose.runtime.mutableStateOf
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.*
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import java.net.URI
import java.util.concurrent.CompletableFuture

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
                val json = jacksonObjectMapper().readTree(content)
                val messageId = json.get("messageId")?.asText()

                if (messageId != null){
                    handleIncomingMessage(msg)
                }
                responseFuture.complete(responses)
                responses.clear()
            }
        } else {
            // Handle unsolicited messages (e.g., messages from other users)
            println("接收到了来自外界的信息: $content")
            handleIncomingMessage(content)
        }
    }

//    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
//        println("收到哈哈哈信息: $msg")
//        super.channelRead(ctx, msg)
//    }

    private fun handleIncomingMessage(msg: FullHttpResponse) {
        try{
            val json = jacksonObjectMapper().readTree(msg.content().toString(Charsets.UTF_8))
            val senderId = json.get("id")?.asInt() ?: throw IllegalArgumentException("Missing senderId")
            val messageId = json.get("messageId")?.asText()
            val timestamp = json.get("timestamp")?.asLong() ?: throw IllegalArgumentException("Missing timestamp")
            val sender = false
            val text = json.get("text")?.asText() ?: throw IllegalArgumentException("Missing content")
            messages += Message(
                id = senderId,
                text = text,
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

    private fun handleIncomingMessage(content: String) {
        try {
            val json = jacksonObjectMapper().readTree(content)
            val messageType = json.get("type")?.asText() ?: throw IllegalArgumentException("Missing message type")
            val messageContent = json.get("content")?.asText() ?: throw IllegalArgumentException("Missing content")
            val senderName = json.get("sender")?.asText() ?: "Unknown"
            val userId = json.get("userId")?.asInt() ?: -1

            when (messageType) {
                "chat" -> {
                    println("New private message from $senderName: $messageContent")
                    messages += Message(
                        id = userId,
                        text = messageContent,
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
                        sender = userId,
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

    fun start(newHost: String = host, newPort: Int = port) {
        host = newHost
        port = newPort
        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(Thread {
            shutdown()
        })
//        println("正在启动...")
        val group = NioEventLoopGroup()

        try {
            val b = Bootstrap()
            b.group(group)
                .channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<Channel>() {
                    override fun initChannel(ch: Channel) {
                        ch.pipeline().addLast(HttpClientCodec())
                        ch.pipeline().addLast(HttpObjectAggregator(8192))
//                        ch.pipeline().addLast(LoggingHandler(LogLevel.DEBUG))
                        responseHandler = CustomHttpResponseHandler()
                        ch.pipeline().addLast(responseHandler)
                    }
                })

            val channelFuture: ChannelFuture = b.connect(host, port).sync()
            channel = channelFuture.channel()
            println("后端服务器连接成功！")

            send("", "login", "0",1) { success, responses ->
                if (success) {
                    println("登录成功")
                    responses.forEach { response ->
                        println("Response: $response")
                    }
                } else {
                    println("Error: $responses")
                }
            }
            // Add a listener to handle reconnection
            channel.closeFuture().addListener(ChannelFutureListener {
                println("启动聊天服务器服务！")
            })

            channel.closeFuture().sync()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // 进行资源的释放
            shutdown()
            group.shutdownGracefully()
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
        type: String,
        targetClientId: String,
        expectedResponses: Int,
        callback: (Boolean, List<FullHttpResponse>) -> Unit
    ) {
        println("正在发送信息")
        if (::channel.isInitialized) {
            // TODO 升级成websocket后url需要改成ws://$host:$port/send
            val uri = URI("http://$host:$port/send")
            // 传输信息，处理类型和目标用户
            val json = jacksonObjectMapper().writeValueAsString(
                mapOf(
                    "content" to message,
                    "type" to type,
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
                        callback(true, responses)
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
        val shutdownCompleted = CompletableFuture<Boolean>()
        send("Shutting down", "logout", "0", 1) { success, _ ->
            if (success) {
                println("Shutdown message sent successfully")
            } else {
                println("Failed to send shutdown message")
            }
            shutdownCompleted.complete(true)
            // 释放channel
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