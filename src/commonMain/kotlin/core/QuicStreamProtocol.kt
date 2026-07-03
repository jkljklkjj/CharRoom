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

    /**
     * 编码消息为 QUIC 帧字节数组。
     * @param payload protobuf 序列化后的 MessageWrapper 字节
     * @return 含长度前缀的完整帧
     */
    fun encodeFrame(payload: ByteArray): ByteArray {
        val buf = ByteBuffer.allocate(4 + payload.size).order(ByteOrder.BIG_ENDIAN)
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
        if (data.size - offset < 4) return null
        val buf = ByteBuffer.wrap(data, offset, data.size - offset).order(ByteOrder.BIG_ENDIAN)
        val frameLen = buf.getInt()
        if (frameLen <= 0 || frameLen > 1024 * 1024) return null
        if (buf.remaining() < frameLen) return null
        val payload = ByteArray(frameLen)
        buf.get(payload)
        return payload to (4 + frameLen)
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
