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
        println("[StreamInit] initChannel 被调用: streamId=${ch.streamId()}")
        ch.pipeline().addLast(object : SimpleChannelInboundHandler<ByteBuf>() {
            override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
                val readable = msg.readableBytes()
                println("[StreamData] streamId=${ch.streamId()}, readableBytes=$readable")
                if (readable < 4) {
                    println("[StreamData] 数据不足 4 字节，跳过")
                    return
                }
                val bytes = ByteArray(readable)
                msg.readBytes(bytes)
                val (payload, _) = QuicStreamProtocol.decodeFrame(bytes) ?: run {
                    println("[StreamData] 帧解码失败: dataLen=$readable")
                    return
                }
                println("[StreamData] 帧解码成功: payloadLen=${payload.size}")
                onStreamFrame(ch.streamId(), payload)
            }

            override fun channelInactive(ctx: ChannelHandlerContext) {
                println("[StreamInit] streamId=${ch.streamId()} 已关闭")
                super.channelInactive(ctx)
            }

            override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                println("[StreamInit] streamId=${ch.streamId()} 异常: ${cause.message}")
                log.warn("QUIC stream handler exception, streamId={}", ch.streamId(), cause)
            }
        })
    }
}
