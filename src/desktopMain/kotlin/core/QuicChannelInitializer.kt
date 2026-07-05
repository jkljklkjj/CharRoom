package core

import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.incubator.codec.quic.QuicStreamChannel
import io.netty.buffer.ByteBuf
import org.slf4j.LoggerFactory

/**
 * QUIC Stream 初始化器。
 * 为每条新 Stream 配置处理器。
 */
class QuicStreamInitializer(
    private val onStreamFrame: (streamId: Long, data: ByteArray) -> Unit,
    private val onStreamInactive: ((streamId: Long) -> Unit)? = null
) : ChannelInitializer<QuicStreamChannel>() {

    private val log = LoggerFactory.getLogger(QuicStreamInitializer::class.java)

    override fun initChannel(ch: QuicStreamChannel) {
        ch.pipeline().addLast(object : SimpleChannelInboundHandler<ByteBuf>() {
            override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
                val readable = msg.readableBytes()
                if (readable < 4) return
                val bytes = ByteArray(readable)
                msg.readBytes(bytes)
                // 循环解码：QUIC 流可能在一次 channelRead0 中包含多帧数据
                var offset = 0
                while (offset < bytes.size) {
                    val result = QuicStreamProtocol.decodeFrame(bytes, offset) ?: break
                    val (payload, consumed) = result
                    onStreamFrame(ch.streamId(), payload)
                    offset += consumed
                }
            }

            override fun channelInactive(ctx: ChannelHandlerContext) {
                log.info("QUIC stream 已关闭: streamId={}", ch.streamId())
                onStreamInactive?.invoke(ch.streamId())
                super.channelInactive(ctx)
            }

            override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                log.warn("QUIC stream handler exception, streamId={}", ch.streamId(), cause)
            }
        })

        // 自动 flush：QUIC 流控窗口恢复后自动发数据（与服务端对齐）
        ch.pipeline().addLast("autoFlush", object : ChannelInboundHandlerAdapter() {
            override fun channelWritabilityChanged(ctx: ChannelHandlerContext) {
                if (ctx.channel().isWritable) {
                    ctx.flush()
                }
                super.channelWritabilityChanged(ctx)
            }
        })
    }
}
