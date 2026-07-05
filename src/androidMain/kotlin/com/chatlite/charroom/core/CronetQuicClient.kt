package com.chatlite.charroom.core

import android.content.Context
import core.*
import core.state.GlobalAppState
import core.state.GlobalChatState
import kotlinx.coroutines.*
import com.google.android.gms.net.CronetProviderInstaller
import org.chromium.net.CronetEngine
import org.chromium.net.BidirectionalStream
import org.chromium.net.CronetException
import org.chromium.net.UrlResponseInfo
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android Cronet QUIC 客户端。
 *
 * 使用 Google Cronet 库实现 QUIC 传输（Android 原生支持）。
 * Cronet 提供 BidirectionalStream API，等效于 QUIC stream。
 * 消息格式与桌面端相同：4字节长度前缀 + Protobuf MessageWrapper。
 */
class CronetQuicClient(private val context: Context) : ChatTransport {

    private val log = LoggerFactory.getLogger(CronetQuicClient::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var cronetEngine: CronetEngine? = null
    private var controlStream: BidirectionalStream? = null
    private val connected = AtomicBoolean(false)
    private val sessionStreams = ConcurrentHashMap<String, BidirectionalStream>()
    private val executor: Executor = Executors.newSingleThreadExecutor()

    internal val messageListeners = mutableListOf<MessageReceiveListener>()
    internal val authStateListeners = mutableListOf<AuthStateListener>()

    // ── 生命周期 ──

    override fun start(host: String?, port: Int?) {
        val h = host ?: ServerConfig.QUIC_HOST
        val p = port ?: ServerConfig.QUIC_PORT

        cronetEngine = CronetEngine.Builder(context)
            .enableHttp2(true)
            .enableQuic(true)
            .enableBrotli(true)
            .build()

        scope.launch {
            try {
                connect(h, p)
                connected.set(true)
                doLogin()
                startHeartbeat()
            } catch (e: Exception) {
                log.error("Cronet QUIC 连接失败", e)
                notifyAuthInvalidated(e.message ?: "Connection failed")
            }
        }
    }

    override fun stop() {
        connected.set(false)
        controlStream?.cancel()
        sessionStreams.values.forEach { it.cancel() }
        sessionStreams.clear()
        cronetEngine?.shutdown()
        cronetEngine = null
    }

    override fun isConnected(): Boolean = connected.get()

    override fun reconnect() {
        stop()
        start()
    }

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
                val stream = getOrCreateStream(type, targetClientId)
                val framed = QuicStreamProtocol.encodeFrame(payload)
                val buf = ByteBuffer.allocateDirect(framed.size)
                buf.put(framed)
                buf.flip()
                stream.write(buf, false)
                stream.flush()
                callback(true, emptyList())
            } catch (e: Exception) {
                log.error("发送失败 type={}", type, e)
                callback(false, emptyList())
            }
        }
    }

    override fun sendText(content: String, callback: (Boolean) -> Unit) = callback(false)

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
        // 安装 Play Services Cronet（确保系统 Cronet 可用）
        CronetProviderInstaller.installProvider(context)

        val url = "https://$host:$port/.well-known/webtransport"

        controlStream = cronetEngine!!.newBidirectionalStreamBuilder(url, createControlStreamCallback(), executor)
            .build()

        controlStream!!.start()
        log.info("Cronet QUIC 连接已发起: {}:{}", host, port)
    }

    private fun createControlStreamCallback(): BidirectionalStream.Callback {
        return ControlStreamCallback()
    }

    private inner class ControlStreamCallback : BidirectionalStream.Callback() {
        override fun onStreamReady(stream: BidirectionalStream) {
            log.info("控制流就绪")
        }

        override fun onResponseHeadersReceived(stream: BidirectionalStream, info: UrlResponseInfo) {
            log.info("控制流响应头: {}", info.httpStatusCode)
        }

        override fun onReadCompleted(stream: BidirectionalStream, info: UrlResponseInfo,
                                     buffer: ByteBuffer, endOfStream: Boolean) {
            if (buffer.hasRemaining()) {
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                handleStreamData(bytes)
            }
            if (!endOfStream) {
                buffer.clear()
                stream.read(buffer)
            }
        }

        override fun onWriteCompleted(stream: BidirectionalStream, info: UrlResponseInfo,
                                      buffer: ByteBuffer, endOfStream: Boolean) {}

        override fun onSucceeded(stream: BidirectionalStream, info: UrlResponseInfo) {
            log.info("控制流完成")
        }

        override fun onFailed(stream: BidirectionalStream, info: UrlResponseInfo, error: CronetException) {
            log.error("控制流失败: {}", error.message)
        }

        override fun onCanceled(stream: BidirectionalStream, info: UrlResponseInfo) {
            log.info("控制流取消")
        }
    }

    // ── 登录 & 心跳 ──

    private fun doLogin() {
        val loginPayload = buildLoginPayload(GlobalAppState.currentToken ?: "")
        val framed = QuicStreamProtocol.encodeFrame(loginPayload)
        val buf = ByteBuffer.allocateDirect(framed.size)
        buf.put(framed)
        buf.flip()
        controlStream!!.write(buf, false)
        controlStream!!.flush()
        log.info("登录消息已发送")
    }

    private fun startHeartbeat() = scope.launch {
        while (connected.get()) {
            delay(20_000)
            if (!connected.get()) break
            try {
                val payload = buildHeartbeatPayload()
                val framed = QuicStreamProtocol.encodeFrame(payload)
                val buf = ByteBuffer.allocateDirect(framed.size)
                buf.put(framed)
                buf.flip()
                controlStream?.write(buf, false)
                controlStream?.flush()
            } catch (e: Exception) {
                log.debug("心跳发送失败: {}", e.message)
            }
        }
    }

    // ── 流管理 ──

    private fun getOrCreateStream(type: MsgType, targetId: String): BidirectionalStream {
        return when (type) {
            MsgType.LOGIN, MsgType.LOGOUT, MsgType.HEARTBEAT -> {
                controlStream ?: throw IllegalStateException("控制流未就绪")
            }
            MsgType.CHAT, MsgType.GROUP_CHAT, MsgType.AGENT_CHAT -> {
                val existing = sessionStreams[targetId]
                if (existing != null) return existing

                val url = "https://${ServerConfig.QUIC_HOST}:${ServerConfig.QUIC_PORT}/stream/$targetId"
                val stream = cronetEngine!!.newBidirectionalStreamBuilder(url, createSessionStreamCallback(targetId), executor)
                    .build()

                stream.start()
                sessionStreams[targetId] = stream
                stream
            }
            else -> throw IllegalArgumentException("不支持的消息类型: $type")
        }
    }

    private fun createSessionStreamCallback(targetId: String): BidirectionalStream.Callback {
        return SessionStreamCallback(targetId)
    }

    private inner class SessionStreamCallback(private val targetId: String) : BidirectionalStream.Callback() {
        override fun onStreamReady(stream: BidirectionalStream) {
            log.debug("会话流就绪: {}", targetId)
        }

        override fun onResponseHeadersReceived(stream: BidirectionalStream, info: UrlResponseInfo) {}

        override fun onReadCompleted(stream: BidirectionalStream, info: UrlResponseInfo,
                                     buffer: ByteBuffer, endOfStream: Boolean) {
            if (buffer.hasRemaining()) {
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                handleStreamData(bytes)
            }
            if (!endOfStream) {
                buffer.clear()
                stream.read(buffer)
            }
        }

        override fun onWriteCompleted(stream: BidirectionalStream, info: UrlResponseInfo,
                                      buffer: ByteBuffer, endOfStream: Boolean) {}

        override fun onSucceeded(stream: BidirectionalStream, info: UrlResponseInfo) {
            sessionStreams.remove(targetId)
        }

        override fun onFailed(stream: BidirectionalStream, info: UrlResponseInfo, error: CronetException) {
            sessionStreams.remove(targetId)
            log.warn("会话流失败: {}", targetId)
        }

        override fun onCanceled(stream: BidirectionalStream, info: UrlResponseInfo) {
            sessionStreams.remove(targetId)
        }
    }

    // ── 消息处理 ──

    private fun handleStreamData(data: ByteArray) {
        try {
            val wrapper = com.chatlite.proto.MessageProtos.MessageWrapper.parseFrom(data)
            synchronized(messageListeners) {
                messageListeners.forEach { listener ->
                    when (wrapper.type) {
                        MsgType.RESPONSE.wire -> {
                            if (wrapper.hasResponse()) {
                                val resp = wrapper.response
                                if (resp.success && resp.clientId.isNotBlank()) {
                                    resp.clientId.toIntOrNull()?.let {
                                        if (it > 0) GlobalAppState.setCurrentUserId(it)
                                    }
                                }
                            }
                        }
                        MsgType.CHAT.wire -> if (wrapper.hasChat()) {
                            val chat = wrapper.chat
                            sendAck(chat.messageId, "chat")
                            listener.onPrivateMessageReceived(
                                senderId = chat.userId.toIntOrNull() ?: 0,
                                message = chat.content,
                                timestamp = chat.timestamp.toLongOrNull() ?: System.currentTimeMillis()
                            )
                        }
                        MsgType.GROUP_CHAT.wire -> if (wrapper.hasGroupChat()) {
                            val gc = wrapper.groupChat
                            sendAck(gc.messageId, "group:${gc.targetClientId}")
                            listener.onGroupMessageReceived(
                                groupId = gc.targetClientId.toIntOrNull() ?: 0,
                                senderId = gc.userId.toIntOrNull() ?: 0,
                                senderName = "用户${gc.userId}",
                                message = gc.content,
                                timestamp = gc.timestamp.toLongOrNull() ?: System.currentTimeMillis()
                            )
                        }
                        MsgType.AGENT_CHAT.wire -> if (wrapper.hasChat()) {
                            val chat = wrapper.chat
                            listener.onPrivateMessageReceived(
                                senderId = chat.userId.toIntOrNull() ?: 0,
                                message = chat.content,
                                timestamp = chat.timestamp.toLongOrNull() ?: System.currentTimeMillis()
                            )
                        }
                        MsgType.ACK.wire -> if (wrapper.hasAck()) {
                            val ack = wrapper.ack
                            if (ack.conversationId.isNotBlank() && ack.seqId > 0) {
                                scope.launch {
                                    GlobalChatState.updateConversationSeqId(ack.conversationId, ack.seqId)
                                }
                            }
                        }
                        MsgType.AGENT_CHAT_STREAM.wire -> if (wrapper.hasAgentStream()) {
                            val stream = wrapper.agentStream
                            if (stream.done) {
                                sendAck(stream.messageId, "agent")
                            }
                            listener.onAgentStreamChunk(
                                messageId = stream.messageId,
                                fullContent = stream.chunk,
                                done = stream.done,
                                error = stream.error
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * 向服务端发送 ACK，确认消息已收到。
     */
    private fun sendAck(messageId: String, conversationId: String) {
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
            sendInternal(ackWrapper.toByteArray())
        } catch (_: Exception) {}
    }

    private fun sendInternal(payload: ByteArray) {
        try {
            val framed = QuicStreamProtocol.encodeFrame(payload)
            val buf = ByteBuffer.allocateDirect(framed.size)
            buf.put(framed)
            buf.flip()
            controlStream?.write(buf, false)
            controlStream?.flush()
        } catch (_: Exception) {}
    }

    private fun notifyAuthInvalidated(reason: String) {
        authStateListeners.forEach { it.onAuthInvalidated(reason) }
    }
}
