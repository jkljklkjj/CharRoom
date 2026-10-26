package core

import kotlinx.serialization.Serializable
import model.Message
import model.GroupMessage
import model.MessageType

@Serializable
data class RecentHistoryCursor(val windowEnd: Long? = null, val afterTimestamp: Long? = null,
    val afterId: String? = null, val limit: Int = 100)

@Serializable
data class RecentHistoryPage(val messages: List<RecentHistoryMessage>, val windowEnd: Long,
    val nextTimestamp: Long? = null, val nextId: String? = null, val hasMore: Boolean = false)

/** Explicit REST wire model: IDs remain decimal strings, timestamps are epoch milliseconds. */
@Serializable
data class RecentHistoryMessage(val id: String, val senderId: String, val receiverId: String,
    val message: String, val timestamp: Long, val conversationId: String = "", val seqId: String = "0",
    val messageType: Int? = null, val fileUrl: String? = null, val fileName: String? = null,
    val fileSize: Long? = null, val replyToMessageId: String? = null,
    val replyToContent: String? = null, val replyToSender: String? = null) {
    val isGroup: Boolean get() = conversationId.startsWith("group:")
    private fun type() = when (messageType) { 1 -> MessageType.IMAGE; 2 -> MessageType.FILE; else -> MessageType.TEXT }
    fun privateMessage(accountId: Int) = Message(senderId = senderId.toInt(), receiverId = receiverId.toInt(),
        sender = senderId == accountId.toString(), message = message, timestamp = timestamp, messageId = id,
        seqId = seqId.toLong(), conversationId = conversationId, messageType = type(), fileUrl = fileUrl,
        fileName = fileName, fileSize = fileSize, replyToMessageId = replyToMessageId,
        replyToContent = replyToContent, replyToSender = replyToSender)
    fun groupMessage() = GroupMessage(groupId = conversationId.removePrefix("group:").toInt(),
        senderName = senderId, senderId = senderId.toInt(), text = message, timestamp = timestamp, messageId = id,
        seqId = seqId.toLong(), conversationId = conversationId, messageType = type(), fileUrl = fileUrl,
        fileName = fileName, fileSize = fileSize, replyToMessageId = replyToMessageId,
        replyToContent = replyToContent, replyToSender = replyToSender)
}

/** History recovery is independent of delivery and incremental cursors. Failed pages propagate. */
suspend fun restoreRecentHistory(
    isCurrentAccount: () -> Boolean,
    fetch: suspend (RecentHistoryCursor) -> RecentHistoryPage,
    apply: suspend (List<RecentHistoryMessage>) -> Unit
) {
    var cursor = RecentHistoryCursor()
    while (isCurrentAccount()) {
        val page = fetch(cursor)
        if (!isCurrentAccount()) return
        apply(page.messages)
        if (!page.hasMore) return
        check(page.messages.isNotEmpty() && page.nextId != null && page.nextTimestamp != null &&
            (page.nextId != cursor.afterId || page.nextTimestamp != cursor.afterTimestamp)) { "History cursor did not advance" }
        check(cursor.windowEnd == null || cursor.windowEnd == page.windowEnd) { "History window changed" }
        cursor = RecentHistoryCursor(page.windowEnd, page.nextTimestamp, page.nextId)
    }
}
