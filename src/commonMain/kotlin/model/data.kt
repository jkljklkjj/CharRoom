package model

import androidx.compose.runtime.*
import core.state.GlobalChatState
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
// 数据层只定义纯数据模型，不依赖任何其他层

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
    val username: String,
    val email: String? = null,
    val phone: String? = null,
    val signature: String? = null,
    val avatarUrl: String? = null,
    val avatarKey: String? = null,
    val online: Boolean? = null,
    val createdAt: Long? = null // 注册时间，时间戳
)

@Serializable
data class Message(
    val senderId: Int,                // 消息的发送用户
    val message: String,           // 消息内容
    val sender: Boolean = false,        // 是否是发送者
    val receiverId: Int = -1,      // 目标用户ID
    val timestamp: Long,        // 消息的时间戳
    val isSent: Boolean = true,  // 消息是否发送成功
    val messageId: String,      // 消息ID（必填）
    val replyToMessageId: String? = null, // 引用的消息ID
    val replyToContent: String? = null,   // 引用的消息内容预览
    val replyToSender: String? = null,    // 引用消息的发送者名称
    val messageType: MessageType = MessageType.TEXT, // 消息类型
    val fileUrl: String? = null,          // 文件/图片URL
    val fileName: String? = null,         // 文件名称
    val fileSize: Long? = null            // 文件大小
) {
    /**
     * 复制消息并更新发送状态
     */
    fun copy(isSent: Boolean): Message = copy(
        senderId = senderId,
        message = message,
        sender = sender,
        receiverId = receiverId,
        timestamp = timestamp,
        isSent = isSent,
        messageId = messageId,
        replyToMessageId = replyToMessageId,
        replyToContent = replyToContent,
        replyToSender = replyToSender,
        messageType = messageType,
        fileUrl = fileUrl,
        fileName = fileName,
        fileSize = fileSize
    )
}

@Serializable
data class GroupMessage(
    val groupId: Int,           // 群组ID
    val senderName: String,     // 发送者名称
    val text: String,           // 消息内容
    val senderId: Int,            // 发送者ID
    val timestamp: Long,        // 消息的时间戳
    val isSent: Boolean = true,  // 消息是否发送成功
    val messageId: String,      // 消息ID（必填）
    val replyToMessageId: String? = null, // 引用的消息ID
    val replyToContent: String? = null,   // 引用的消息内容预览
    val replyToSender: String? = null,    // 引用消息的发送者名称
    val messageType: MessageType = MessageType.TEXT, // 消息类型
    val fileUrl: String? = null,          // 文件/图片URL
    val fileName: String? = null,         // 文件名称
    val fileSize: Long? = null            // 文件大小
) {
    /**
     * 复制消息并更新发送状态
     */
    fun copy(isSent: Boolean): GroupMessage = copy(
        groupId = groupId,
        senderName = senderName,
        text = text,
        senderId = senderId,
        timestamp = timestamp,
        isSent = isSent,
        messageId = messageId,
        replyToMessageId = replyToMessageId,
        replyToContent = replyToContent,
        replyToSender = replyToSender,
        messageType = messageType,
        fileUrl = fileUrl,
        fileName = fileName,
        fileSize = fileSize
    )
}

@Serializable
data class Group(val id: Int, val name: String)

/**
 * 消息ID生成工具
 */
object MessageIdGenerator {
    /**
     * 生成私聊消息ID
     */
    fun generateMessageId(senderId: Int, content: String, timestamp: Long): String {
        val minuteTimestamp = timestamp / 60000 // 转换为分钟级时间戳
        val generatedId = (senderId.toString() + content.hashCode() + minuteTimestamp).hashCode()
        return generatedId.toString()
    }

    /**
     * 生成群聊消息ID
     */
    fun generateGroupMessageId(groupId: Int, senderId: Int, content: String, timestamp: Long): String {
        val minuteTimestamp = timestamp / 60000 // 转换为分钟级时间戳
        val generatedId = (groupId.toString() + senderId.toString() + content.hashCode() + minuteTimestamp).hashCode()
        return generatedId.toString()
    }
}

/**
 * 群组转换为用户类型（用于UI显示）
 */
fun Group.toUiUser(): User = User(id = -this.id, username = this.name)

/**
 * 添加AI助手到用户列表
 */
fun List<User>.withAgentAssistant(): List<User> {
    val agentId = 900000001 // 从ServerConfig移动到这里，避免依赖
    if (this.any { it.id == agentId }) {
        return this
    }
    return listOf(User(id = agentId, username = "AI助手")) + this
}