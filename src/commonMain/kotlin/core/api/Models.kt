package core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import model.Message

@Serializable
data class LoginTokenBundle(val accessToken: String = "", val refreshToken: String = "")

@Serializable
data class ApiResponse<T>(val code: Int = 0, val message: String? = null, val data: T? = null) {
    val isSuccess: Boolean get() = code == 0
}

@Serializable
data class FriendRequest(
    val id: Int, val senderId: Int, val senderName: String, val senderAvatar: String? = null,
    val message: String, val status: Int, val createTime: Long
)

@Serializable
data class MessageSerializer(
    val id: Int? = null, val senderId: Int, val receiverId: Int, val content: String,
    val timestamp: Long, val messageType: String = "TEXT", val fileUrl: String? = null,
    val fileName: String? = null, val fileSize: Long? = null, val replyToMessageId: String? = null,
    val replyToContent: String? = null, val replyToSender: String? = null, val messageId: String? = null
)

@Serializable
data class GroupMessageSerializer(
    val id: Int? = null, val groupId: Int, val senderId: Int, val senderName: String,
    val content: String, val timestamp: Long, val messageType: String = "TEXT",
    val fileUrl: String? = null, val fileName: String? = null, val fileSize: Long? = null,
    val replyToMessageId: String? = null, val replyToContent: String? = null,
    val replyToSender: String? = null, val messageId: String? = null
)

@Serializable
data class SyncMessagesResult(
    val messages: List<Message> = emptyList(), val nextSeqId: Long = 0L,
    val serverSeqId: Long = 0L, val hasMore: Boolean = false
)

@Serializable
data class SyncEvent(
    val conversationId: String = "", val eventType: String = "",
    val seqId: Long = 0L, val payload: JsonElement? = null
)

@Serializable
data class GlobalSyncResult(
    val events: List<SyncEvent> = emptyList(), val nextGlobalSeqId: Long = 0L, val hasMore: Boolean = false
)

@Serializable
data class DeviceInfo(val deviceType: String = "", val deviceId: String = "", val lastActiveTime: Long = 0L)

const val GROUP_JOIN_PENDING_CODE = 1005
