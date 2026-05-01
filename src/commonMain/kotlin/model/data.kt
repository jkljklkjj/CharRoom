package model

import androidx.compose.runtime.*
import kotlinx.serialization.Serializable
import core.ApiService
import core.ServerConfig

/**
 * 消息类型
 */
enum class MessageType {
    TEXT,  // 文本消息
    IMAGE, // 图片消息
    FILE   // 文件消息
}

@Serializable
data class User(
    val id: Int,
    var username: String,
    var email: String? = null,
    var phone: String? = null,
    var signature: String? = null,
    var avatarUrl: String? = null,
    var avatarKey: String? = null,
    var online: Boolean? = null
)
@Serializable
data class Message(
    val senderId: Int,                // 消息的发送用户
    val message: String,           // 消息内容
    val sender: Boolean = false,        // 是否是发送者
    val receiverId: Int = -1,      // 目标用户ID
    val timestamp: Long,        // 消息的时间戳
    var isSent: MutableState<Boolean> = mutableStateOf(true),  // 消息是否发送成功
    var messageId: String = "",// 消息ID
    val replyToMessageId: String? = null, // 引用的消息ID
    val replyToContent: String? = null,   // 引用的消息内容预览
    val replyToSender: String? = null,    // 引用消息的发送者名称
    val messageType: MessageType = MessageType.TEXT, // 消息类型
    val fileUrl: String? = null,          // 文件/图片URL
    val fileName: String? = null,         // 文件名称
    val fileSize: Long? = null            // 文件大小
) {
    init {
        if (messageId.isEmpty()) {
            val minuteTimestamp = timestamp / 60000 // 转换为分钟级时间戳
            val generatedId = (senderId.toString() + message.hashCode() + minuteTimestamp).hashCode()
            this.messageId = generatedId.toString()
        }
    }
}
@Serializable
data class GroupMessage(
    val groupId: Int,           // 群组ID
    val senderName: String,     // 发送者名称
    val text: String,           // 消息内容
    val senderId: Int,            // 发送者ID
    val timestamp: Long,        // 消息的时间戳
    var isSent: MutableState<Boolean>,  // 消息是否发送成功
    var messageId: String = "",  // 消息ID
    val replyToMessageId: String? = null, // 引用的消息ID
    val replyToContent: String? = null,   // 引用的消息内容预览
    val replyToSender: String? = null,    // 引用消息的发送者名称
    val messageType: MessageType = MessageType.TEXT, // 消息类型
    val fileUrl: String? = null,          // 文件/图片URL
    val fileName: String? = null,         // 文件名称
    val fileSize: Long? = null            // 文件大小
) {
    init {
        if (messageId.isEmpty()) {
            val minuteTimestamp = timestamp / 60000 // 转换为分钟级时间戳
            val generatedId = (groupId.toString() + senderId.toString() + text.hashCode() + minuteTimestamp).hashCode()
            this.messageId = generatedId.toString()
        }
    }
}
@Serializable
data class Group(val id: Int, val name: String)

fun convertMessages(messages: List<Group>): List<User> {
    return messages.map { message ->
        User(id = -message.id, username = message.name)
    }
}

var users by mutableStateOf<List<User>>(emptyList())
var messages = mutableStateListOf<Message>()
//var messages = mutableStateListOf<Message>(
//    Message(
//        senderId = 1,
//        receiverId = 3, // 假设当前登录账号是3
//        message = "来自账号1的第一条消息",
//        sender = false,
//        timestamp = 1698765600000,
//        isSent = mutableStateOf(true),
//        messageId = "msg-1"
//    ),
//    Message(
//        senderId = 1,
//        receiverId = 1,
//        message = "我回复账号1",
//        sender = true,
//        timestamp = 1698765660000,
//        isSent = mutableStateOf(true),
//        messageId = "msg-2"
//    ),
//    Message(
//        senderId = 1,
//        receiverId = 3,
//        message = "来自账号1的第二条消息",
//        sender = false,
//        timestamp = 1698765720000,
//        isSent = mutableStateOf(true),
//        messageId = "msg-3"
//    )
//)
var groupMessages = mutableStateListOf<GroupMessage>()

private fun withAgentAssistant(list: List<User>): List<User> {
    if (list.any { ServerConfig.isAgentAssistant(it.id) }) {
        return list
    }
    return listOf(User(ServerConfig.AGENT_ASSISTANT_ID, ServerConfig.AGENT_ASSISTANT_NAME)) + list
}

/**
 * 获取好友列表（委托 ApiService，使用全局 Token）
 */
suspend fun fetchFriends(): List<User> = ApiService.fetchFriends()

/**
 * 获取群组列表（委托 ApiService，使用全局 Token）
 */
suspend fun fetchGroups(): List<User> = ApiService.fetchGroups()

/**
 * 更新好友列表
 */
suspend fun updateFriendList(): List<User> {
    val tmp = fetchFriends()
    users = withAgentAssistant(users + tmp)
    return tmp
}

/**
 * 更新群组列表
 */
suspend fun updateGroupList(): List<User> {
    val tmp = fetchGroups()
    users = withAgentAssistant(users + tmp)
    return tmp
}

/**
 * 更新好友和群组列表：同时写回全局 users 以触发 UI 重组
 */
suspend fun updateList(): List<User> {
    val friends = fetchFriends()
    val groups = fetchGroups()
    val merged = friends + groups
    users = withAgentAssistant(merged)
    return users
}