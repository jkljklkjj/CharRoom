@file:Suppress("DEPRECATION")

package core

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.incubator.codec.quic.*
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory

/**
 * Netty QUIC 传输层 (桌面/JVM 平台)。
 *
 * 管理 QUIC 连接生命周期、Stream 创建、数据收发。 上层通过 [QuicNettyClient.Listener] 接口接收事件。
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
        get() = checkNotNull(_streamHandler) { "QUIC stream handler is not initialized" }

    interface Listener {
        fun onConnected()

        fun onDisconnected(cause: Throwable?)

        fun onStreamFrame(streamId: Long, data: ByteArray)

        fun onError(cause: Throwable)
    }

    fun connect(host: String, port: Int) {
        val address = InetSocketAddress(host, port)
        val ssl = createSslContext(address)
        _streamHandler =
            QuicStreamInitializer(
                onStreamFrame = { stream, data -> listener?.onStreamFrame(stream, data) },
                onStreamInactive = { stream -> streams.remove(stream) },
            )
        val codec = createCodec(address, ssl)
        val worker = NioEventLoopGroup(1)
        group = worker
        datagramChannel = bindDatagram(worker, codec)
        quicChannel = createQuicConnection(datagramChannel!!, address, streamHandler)
        log.info("QUIC connected: {}:{}", host, port)
        listener?.onConnected()
    }

    private fun createSslContext(address: InetSocketAddress): QuicSslContext =
        recoverOperation({
            log.error("Failed to create QUIC SSL context", it)
            throw it
        }) {
            QuicSslContextBuilder.forClient()
                .applicationProtocols(ServerConfig.QUIC_ALPN)
                .trustManager(QuicPeerTrust.system(address.hostString))
                .build()
        }

    private fun createCodec(
        address: InetSocketAddress,
        ssl: QuicSslContext,
    ): io.netty.channel.ChannelHandler =
        recoverOperation({
            log.error("Failed to create QUIC client codec", it)
            throw it
        }) {
            QuicClientCodecBuilder()
                .sslContext(ssl)
                .sslEngineProvider { channel ->
                    ssl.newEngine(channel.alloc(), address.hostString, address.port)
                }
                .maxIdleTimeout(180_000, TimeUnit.MILLISECONDS)
                .initialCongestionWindowPackets(10)
                .congestionControlAlgorithm(QuicCongestionControlAlgorithm.BBR)
                .initialMaxData(16_777_216L)
                .initialMaxStreamDataBidirectionalLocal(4_194_304L)
                .initialMaxStreamDataBidirectionalRemote(4_194_304L)
                .initialMaxStreamsBidirectional(256)
                .initialMaxStreamsUnidirectional(128)
                .build()
        }

    private fun bindDatagram(
        worker: EventLoopGroup,
        codec: io.netty.channel.ChannelHandler,
    ): Channel =
        recoverOperation({
            log.error("Failed to bind UDP channel", it)
            throw it
        }) {
            Bootstrap()
                .group(worker)
                .channel(NioDatagramChannel::class.java)
                .handler(
                    object : ChannelInitializer<Channel>() {
                        override fun initChannel(channel: Channel) {
                            channel.pipeline().addLast(codec)
                        }
                    }
                )
                .bind(0)
                .sync()
                .channel()
        }

    private fun createQuicConnection(
        udpChannel: Channel,
        address: InetSocketAddress,
        streamHandler: QuicStreamInitializer,
    ): QuicChannel {
        val f: io.netty.util.concurrent.Future<QuicChannel> =
            QuicChannel.newBootstrap(udpChannel)
                .streamHandler(streamHandler)
                .remoteAddress(address)
                .connect()

        val done = f.awaitUninterruptibly(10, TimeUnit.SECONDS)
        if (!done) throw java.net.SocketTimeoutException("QUIC connection timed out ($address)")

        val channel = f.getNow()
        if (channel == null) throw f.cause() ?: java.io.IOException("QUIC connection failed")
        return channel
    }

    /**
     * 打开一条新的双向 Stream (用于聊天会话)。
     *
     * @return streamId
     */
    fun openStream(): Long {
        val qCh = checkNotNull(quicChannel) { "Not connected" }
        val f =
            qCh.newStreamBootstrap()
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

    /** 检查指定 Stream 是否仍活跃。 */
    fun isStreamActive(streamId: Long): Boolean {
        val stream = streams[streamId]
        return stream != null && stream.isActive
    }

    /** 向指定 Stream 发送数据。 */
    fun send(streamId: Long, data: ByteArray) {
        val stream = streams[streamId]
        if (stream != null && stream.isActive) {
            stream.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(data))
        } else {
            log.warn("QUIC 发送失败: stream={} 不可用 (active={})", streamId, stream?.isActive)
        }
    }

    /** 关闭连接。 */
    fun shutdown() {
        core.recoverOperation(
            onFailure = { e ->
                log.warn("QUIC 关闭异常", e)
            }
        ) {
            quicChannel?.close()?.awaitUninterruptibly()
            datagramChannel?.close()?.awaitUninterruptibly()
            group?.shutdownGracefully()
        }
    }
}
