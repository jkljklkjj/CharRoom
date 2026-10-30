package core

/** Reply identity and preview travel together when a pending send is converted to the wire. */
data class MessageReply(
    val id: String? = null,
    val content: String? = null,
    val sender: String? = null,
)
