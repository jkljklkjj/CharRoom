package core

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled

/**
 * QUIC Stream 帧编解码器。
 *
 * 帧格式：
 * ┌───────────────────────────────────┐
 * │ Frame Length (4 bytes, big-endian)│
 * ├───────────────────────────────────┤
 * │ protobuf MessageWrapper bytes     │
 * └───────────────────────────────────┘
 */
object QuicStreamProtocol {

    /**
     * 编码消息为 QUIC 帧 ByteBuf。
     * @param payload protobuf 序列化后的 MessageWrapper 字节
     * @return 含长度前缀的完整帧
     */
    fun encodeFrame(payload: ByteArray): ByteBuf {
        val buf = Unpooled.buffer(4 + payload.size)
        buf.writeInt(payload.size)    // 4字节大端长度
        buf.writeBytes(payload)       // protobuf 负载
        return buf
    }

    /**
     * 从 ByteBuf 解码一帧。
     * @param buf 包含完整帧的 ByteBuf
     * @return 帧负载（protobuf MessageWrapper 字节），或 null 如果数据不足
     */
    fun decodeFrame(buf: ByteBuf): ByteArray? {
        if (buf.readableBytes() < 4) return null
        buf.markReaderIndex()
        val frameLen = buf.readInt()
        if (frameLen <= 0 || frameLen > 1024 * 1024) { // 最大 1MB
            buf.resetReaderIndex()
            return null
        }
        if (buf.readableBytes() < frameLen) {
            buf.resetReaderIndex() // 数据不足，回滚
            return null
        }
        val payload = ByteArray(frameLen)
        buf.readBytes(payload)
        return payload
    }

    /**
     * 创建 Stream 初始化帧（用于首次打开聊天流时发送元数据）。
     * @param conversationType 会话类型: "private" / "group" / "agent"
     * @param targetId 目标 ID
     * @return 完整帧 ByteBuf
     */
    fun createStreamInitFrame(conversationType: String, targetId: String): ByteBuf {
        val meta = "$conversationType:$targetId"
        // 写入 [4字节长度][协议格式: "type:targetId"]
        val initBytes = meta.toByteArray()
        val buf = Unpooled.buffer(4 + initBytes.size)
        buf.writeInt(initBytes.size)
        buf.writeBytes(initBytes)
        return buf
    }
}
