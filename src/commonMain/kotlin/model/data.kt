package model

import androidx.compose.runtime.*
import kotlinx.serialization.Serializable
import core.ApiService

@Serializable
data class User(val id: Int, var username: String)
@Serializable
data class Message(
    val senderId: Int,                // 消息的发送用户
    val message: String,           // 消息内容
    val sender: Boolean = false,        // 是否是发送者
    val receiverId: Int = -1,      // 目标用户ID
    val timestamp: Long,        // 消息的时间戳
    var isSent: MutableState<Boolean> = mutableStateOf(true),  // 消息是否发送成功
    var messageId: String = ""// 消息ID
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
    var messageId: String = ""  // 消息ID
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

//var users by mutableStateOf(listOf(
//    User(1, "Alice"),
//    User(2, "Bob"),
//    User(3, "Charlie"),
//    User(-1, "fucking group")
//))
//
//var messages = mutableStateListOf(
//    Message(1, "Hello from Alice", false, timestamp = 1698765600000, isSent = mutableStateOf(true)),
//    Message(2, "Hello from Bob", false, timestamp = 1698765660000, isSent = mutableStateOf(true)),
//    Message(3, "Hello from Charlie", false, timestamp = 1698765720000, isSent = mutableStateOf(true)),
//    Message(1, "How are you?", false, timestamp = 1698765780000, isSent = mutableStateOf(true)),
//    Message(2, "I'm fine, thanks!", false, timestamp = 1698765840000, isSent = mutableStateOf(true)),
//    Message(1, "I'm fine, thanks!", true, timestamp = 1698765900000, isSent = mutableStateOf(false))
//)
//
//var groupMessages = mutableStateListOf(
//    GroupMessage(1, "Alice", "Hello from Alice", 1, timestamp = 1698765600000, isSent = mutableStateOf(true)),
//    GroupMessage(1, "Bob", "Hello from Bob", 2, timestamp = 1698765660000, isSent = mutableStateOf(true)),
//    GroupMessage(1, "Charlie", "Hello from Charlie", 3, timestamp = 1698765720000, isSent = mutableStateOf(true)),
//    GroupMessage(1, "Alice", "How are you?", 1, timestamp = 1698765780000, isSent = mutableStateOf(true)),
//    GroupMessage(1, "Bob", "I'm fine, thanks!", 2, timestamp = 1698765840000, isSent = mutableStateOf(false))
//)
var users by mutableStateOf<List<User>>(emptyList())
var messages = mutableStateListOf<Message>()
var groupMessages = mutableStateListOf<GroupMessage>()

/**
 * 获取好友列表（委托 ApiService，使用全局 Token）
 */
fun fetchFriends(): List<User> = ApiService.fetchFriends()

/**
 * 获取群组列表（委托 ApiService，使用全局 Token）
 */
fun fetchGroups(): List<User> = ApiService.fetchGroups()

/**
 * 更新好友列表
 */
fun updateFriendList(): List<User> {
    val tmp = fetchFriends()
    users = users + tmp
    return tmp
}

/**
 * 更新群组列表
 */
fun updateGroupList(): List<User> {
    val tmp = fetchGroups()
    users = users + tmp
    return tmp
}

/**
 * 更新好友和群组列表：同时写回全局 users 以触发 UI 重组
 */
fun updateList(): List<User> {
    val friends = fetchFriends()
    val groups = fetchGroups()
    val merged = friends + groups
    users = merged
    return merged
}