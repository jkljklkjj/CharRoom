package core

/** Optional attachment metadata shared by private and group wire builders. */
data class MessageAttachment(
    val fileUrl: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
)
