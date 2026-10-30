package core.state

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ChatCursorState {
    val conversationSeqIds: StateFlow<Map<String, Long>>

    suspend fun updateConversationSeqId(conversationId: String, seqId: Long)

    suspend fun getConversationSeqId(conversationId: String): Long

    suspend fun clearConversationSeqIds()
}

internal class ChatCursors : ChatCursorState {
    // 每个会话的 seqId 跟踪（用于消息去重和顺序保证）
    private val _conversationSeqIds = MutableStateFlow<Map<String, Long>>(emptyMap())

    override val conversationSeqIds: StateFlow<Map<String, Long>> =
        _conversationSeqIds.asStateFlow()

    private val seqIdMutex = Mutex()

    /** 更新指定会话的 seqId，仅在新 seqId 大于旧 seqId 时更新。 */
    override suspend fun updateConversationSeqId(conversationId: String, seqId: Long) =
        seqIdMutex.withLock {
            val currentMap = _conversationSeqIds.value.toMutableMap()
            val oldSeqId = currentMap[conversationId]
            if (oldSeqId == null || seqId > oldSeqId) {
                currentMap[conversationId] = seqId
                _conversationSeqIds.value = currentMap
            }
        }

    /** 获取指定会话的当前 seqId，若不存在则返回 0。 */
    override suspend fun getConversationSeqId(conversationId: String): Long = seqIdMutex.withLock {
        _conversationSeqIds.value[conversationId] ?: 0L
    }

    /** 清空所有会话的 seqId 跟踪。 */
    override suspend fun clearConversationSeqIds() = seqIdMutex.withLock {
        _conversationSeqIds.value = emptyMap()
    }

    fun reset() {
        _conversationSeqIds.value = emptyMap()
    }
}
