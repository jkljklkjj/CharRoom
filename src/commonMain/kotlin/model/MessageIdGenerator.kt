package model

import java.util.UUID

/** Stable opaque request IDs; retries reuse the ID already stored with the message. */
object MessageIdGenerator {
    fun nextId(): String = "local_" + UUID.randomUUID().toString()

    // Preserve source compatibility with callers; account, content, and clock are not ID entropy.
    @Suppress("UNUSED_PARAMETER")
    fun generateMessageId(senderId: Int, content: String, timestamp: Long): String = nextId()

    @Suppress("UNUSED_PARAMETER")
    fun generateGroupMessageId(groupId: Int, senderId: Int, content: String, timestamp: Long): String = nextId()
}
