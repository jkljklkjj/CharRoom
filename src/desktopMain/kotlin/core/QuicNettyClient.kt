@file:Suppress("DEPRECATION")
package core

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.incubator.codec.quic.*
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Netty QUIC 传输层 (桌面/JVM 平台)。
 *
 * 管理 QUIC 连接生命周期、Stream 创建、数据收发。
 * 上层通过 [QuicNettyClient.Listener] 接口接收事件。
 */
class QuicNettyClient {

    private val log = LoggerFactory.getLogger(QuicNettyClient::class.java)

    private var group: EventLoopGroup? = null
    private var datagramChannel: Channel? = null
    private var quicChannel: QuicChannel? = null

    // 活跃的 Stream 映射: streamId -> QuicStreamChannel
    val streams = ConcurrentHashMap<Long, QuicStreamChannel>()

    /** 事件监听器 */
    var listener: Listener? = null

    /** Stream 数据处理器（connect 时初始化，openStream 时复用） */
    private var _streamHandler: QuicStreamInitializer? = null
    private val streamHandler: QuicStreamInitializer
        get() = _streamHandler ?: throw IllegalStateException("QUIC 未连接，streamHandler 未初始化")

    interface Listener {
        fun onConnected()
        fun onDisconnected(cause: Throwable?)
        fun onStreamFrame(streamId: Long, data: ByteArray)
        fun onError(cause: Throwable)
    }

    /**
     * 连接到 QUIC 服务器。
     */
    fun connect(host: String, port: Int) {
        val address = InetSocketAddress(host, port)

        val sslCtx = try {
            QuicSslContextBuilder.forClient()
                .applicationProtocols(ServerConfig.QUIC_ALPN)
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            log.error("Failed to create QUIC SSL context", e)
            throw e
        }

        // stream handler 提为字段，供 connect() 和 openStream() 共用
        _streamHandler = QuicStreamInitializer(
            onStreamFrame = { streamId, data -> listener?.onStreamFrame(streamId, data) },
            onStreamInactive = { streamId -> streams.remove(streamId) }
        )

        val codec = try {
            QuicClientCodecBuilder()
                .sslContext(sslCtx)
                // 与服务端对齐：QUIC 取双方 min，客户端必须和服务端一致或更大
                .maxIdleTimeout(600_000, TimeUnit.MILLISECONDS)        // 10 min，与服务端对齐
                .initialCongestionWindowPackets(10)                    // 与服务端 QuicProperties 对齐
                .congestionControlAlgorithm(QuicCongestionControlAlgorithm.BBR)
                // 流量控制：必须显式设正值，默认 0 = 对端不能发数据
                .initialMaxData(16_777_216L)                         // 连接级 16 MB
                .initialMaxStreamDataBidirectionalLocal(4_194_304L)   // 服务端→客户端 4 MB
                .initialMaxStreamDataBidirectionalRemote(4_194_304L)  // 客户端→服务端 4 MB
                // 流数量：与服务端对齐，支持多会话并发
                .initialMaxStreamsBidirectional(256)
                .initialMaxStreamsUnidirectional(128)
                .build()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            log.error("Failed to create QUIC client codec", e)
            throw e
        }

        val elGroup = NioEventLoopGroup(1)
        group = elGroup

        datagramChannel = try {
            Bootstrap()
                .group(elGroup)
                .channel(NioDatagramChannel::class.java)
                .handler(object : ChannelInitializer<Channel>() {
                    override fun initChannel(ch: Channel) {
                        ch.pipeline().addLast(codec)
                    }
                })
                .bind(0)  // 随机本地端口
                .sync()
                .channel()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            log.error("Failed to bind UDP channel", e)
            throw e
        }

        quicChannel = createQuicConnection(datagramChannel!!, address, _streamHandler!!)

        log.info("QUIC 连接已建立: {}:{}", host, port)
        listener?.onConnected()
    }

    private fun createQuicConnection(
        udpChannel: Channel,
        address: InetSocketAddress,
        streamHandler: QuicStreamInitializer
    ): QuicChannel {
        val f: io.netty.util.concurrent.Future<QuicChannel> = QuicChannel.newBootstrap(udpChannel)
            .streamHandler(streamHandler)
            .remoteAddress(address)
            .connect()

        val done = f.awaitUninterruptibly(10, TimeUnit.SECONDS)
        if (!done) throw RuntimeException("QUIC 连接超时 (${address})")

        val channel = f.getNow()
        if (channel == null) throw f.cause() ?: RuntimeException("QUIC 连接失败")
        return channel
    }

    /**
     * 打开一条新的双向 Stream (用于聊天会话)。
     * @return streamId
     */
    fun openStream(): Long {
        val qCh = quicChannel ?: throw IllegalStateException("Not connected")
        val f = qCh.newStreamBootstrap()
            .type(QuicStreamType.BIDIRECTIONAL)
            .handler(streamHandler)
            .create()
        f.awaitUninterruptibly()
        val stream = f.getNow()
        if (stream == null) throw f.cause() ?: RuntimeException("QUIC 流创建失败")

        streams[stream.streamId()] = stream
        log.debug("QUIC 新流打开: streamId={}", stream.streamId())
        return stream.streamId()
    }

    /**
     * 检查指定 Stream 是否仍活跃。
     */
    fun isStreamActive(streamId: Long): Boolean {
        val stream = streams[streamId]
        return stream != null && stream.isActive
    }

    /**
     * 向指定 Stream 发送数据。
     */
    fun send(streamId: Long, data: ByteArray) {
        val stream = streams[streamId]
        if (stream != null && stream.isActive) {
            stream.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(data))
        } else {
            log.warn("QUIC 发送失败: stream={} 不可用 (active={})", streamId, stream?.isActive)
        }
    }

    /**
     * 关闭连接。
     */
    fun shutdown() {
        try {
            quicChannel?.close()?.awaitUninterruptibly()
            datagramChannel?.close()?.awaitUninterruptibly()
            group?.shutdownGracefully()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            log.warn("QUIC 关闭异常", e)
        }
    }
}
