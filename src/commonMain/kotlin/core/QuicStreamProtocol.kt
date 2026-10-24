package core

import java.nio.ByteBuffer
import java.nio.ByteOrder

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
    const val FRAME_HEADER_SIZE = Int.SIZE_BYTES
    const val MAX_FRAME_SIZE = 1024 * 1024

    /** One decoder per stream: QUIC/Cronet read callbacks are not message boundaries. */
    class Decoder {
        private val header = ByteBuffer.allocate(FRAME_HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
        private var payload: ByteBuffer? = null

        fun reset() { header.clear(); payload = null }

        fun decode(bytes: ByteArray): List<ByteArray> {
            val frames = mutableListOf<ByteArray>()
            var offset = 0
            while (offset < bytes.size) {
                val destination = payload ?: header
                val count = minOf(destination.remaining(), bytes.size - offset)
                destination.put(bytes, offset, count)
                offset += count
                if (!destination.hasRemaining()) {
                    if (payload == null) {
                        header.flip()
                        val length = header.int
                        header.clear()
                        require(length in 1..MAX_FRAME_SIZE) { "Invalid QUIC frame length: $length" }
                        payload = ByteBuffer.allocate(length)
                    } else {
                        frames.add(destination.array())
                        payload = null
                    }
                }
            }
            return frames
        }

        /** Cronet leaves position at the end of the bytes it wrote into the read buffer. */
        fun decodeReadBuffer(buffer: ByteBuffer): List<ByteArray> {
            buffer.flip()
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            return decode(bytes)
        }
    }

    /**
     * 编码消息为 QUIC 帧字节数组。
     * @param payload protobuf 序列化后的 MessageWrapper 字节
     * @return 含长度前缀的完整帧
     */
    fun encodeFrame(payload: ByteArray): ByteArray {
        require(payload.size in 1..MAX_FRAME_SIZE) { "Invalid QUIC payload size: ${payload.size}" }
        val buf = ByteBuffer.allocate(FRAME_HEADER_SIZE + payload.size).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(payload.size)
        buf.put(payload)
        return buf.array()
    }

    /**
     * 从字节数组解码一帧。
     * @param data 包含帧数据的字节数组
     * @param offset 起始偏移
     * @return Pair(解码后的负载, 消耗的字节数)，或 null 如果数据不足
     */
    fun decodeFrame(data: ByteArray, offset: Int = 0): Pair<ByteArray, Int>? {
        require(offset in 0..data.size)
        if (data.size - offset < FRAME_HEADER_SIZE) return null
        val buf = ByteBuffer.wrap(data, offset, data.size - offset).order(ByteOrder.BIG_ENDIAN)
        val frameLen = buf.getInt()
        if (frameLen <= 0 || frameLen > MAX_FRAME_SIZE) return null
        if (buf.remaining() < frameLen) return null
        val payload = ByteArray(frameLen)
        buf.get(payload)
        return payload to (FRAME_HEADER_SIZE + frameLen)
    }

    /**
     * 创建 Stream 初始化帧（用于首次打开聊天流时发送元数据）。
     * @param conversationType 会话类型: "private" / "group" / "agent"
     * @param targetId 目标 ID
     * @return 完整帧字节数组
     */
    fun createStreamInitFrame(conversationType: String, targetId: String): ByteArray {
        val meta = "$conversationType:$targetId"
        val initBytes = meta.toByteArray()
        val buf = ByteBuffer.allocate(4 + initBytes.size).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(initBytes.size)
        buf.put(initBytes)
        return buf.array()
    }
}
