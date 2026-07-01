package core

import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.incubator.codec.quic.QuicStreamChannel
import io.netty.buffer.ByteBuf
import org.slf4j.LoggerFactory

/**
 * QUIC Stream 初始化器。
 * 为每条新 Stream 配置处理器。
 */
class QuicStreamInitializer(
    private val onStreamFrame: (streamId: Long, data: ByteArray) -> Unit
) : ChannelInitializer<QuicStreamChannel>() {

    private val log = LoggerFactory.getLogger(QuicStreamInitializer::class.java)

    override fun initChannel(ch: QuicStreamChannel) {
        // 启用半开状态：服务端发 FIN 后写端仍可用（类似 WebTransport writable）
        ch.config().isAllowHalfClosure = true
        ch.pipeline().addLast(object : SimpleChannelInboundHandler<ByteBuf>() {
            override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
                val readable = msg.readableBytes()
                if (readable < 4) return
                val payload = ByteArray(readable)
                msg.readBytes(payload)
                onStreamFrame(ch.streamId(), payload)
            }

            override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                log.warn("QUIC stream handler exception, streamId={}", ch.streamId(), cause)
            }
        })
    }
}
