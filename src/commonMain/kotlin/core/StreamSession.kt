package core

/**
 * QUIC Stream 会话状态。
 * 跟踪每条聊天流的元数据和生命周期。
 */
class StreamSession(
    /** 流ID (0=控制流, N≥1=会话流) */
    val streamId: Long,

    /** 会话ID */
    val conversationId: String,

    /** 会话类型 */
    val conversationType: ConversationType,

    /** 目标用户/群组 ID */
    val targetId: String,

    /** 流是否活跃 */
    var isActive: Boolean = true
) {
    /** 会话类型枚举 */
    enum class ConversationType {
        PRIVATE,    // 私聊
        GROUP,      // 群聊
        AGENT,      // AI Agent
        CONTROL     // 控制流
    }

    override fun toString(): String {
        return "StreamSession(id=$streamId, type=$conversationType, target=$targetId, active=$isActive)"
    }
}
