package component

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import model.GroupMessage
import model.Message
import model.MessageType

/**
 * 消息长按菜单
 */
@Composable
fun MessageLongPressMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    message: Any,
    isSelf: Boolean,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onForward: () -> Unit,
    onReply: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(180.dp)
    ) {
        // 复制按钮：只有文本消息可以复制
        if (isTextMessage(message)) {
            DropdownMenuItem(onClick = {
                onCopy()
                onDismiss()
            }) {
                Text("复制")
            }
        }

        // 回复按钮
        DropdownMenuItem(onClick = {
            onReply()
            onDismiss()
        }) {
            Text("回复")
        }

        // 转发按钮
        DropdownMenuItem(onClick = {
            onForward()
            onDismiss()
        }) {
            Text("转发")
        }

        // 删除按钮：只有自己发送的消息可以删除
        if (isSelf) {
            DropdownMenuItem(onClick = {
                onDelete()
                onDismiss()
            }, contentPadding = PaddingValues(start = 16.dp)) {
                Text("删除", color = MaterialTheme.colors.error)
            }
        }
    }
}

/**
 * 判断是否是文本消息
 */
private fun isTextMessage(message: Any): Boolean {
    return when (message) {
        is Message -> message.messageType == MessageType.TEXT
        is GroupMessage -> message.messageType == MessageType.TEXT
        else -> false
    }
}

/**
 * 获取消息内容（用于复制）
 */
fun getMessageContent(message: Any): String {
    return when (message) {
        is Message -> message.message
        is GroupMessage -> message.text
        else -> ""
    }
}

/**
 * 获取消息发送者名称
 */
fun getMessageSenderName(message: Any, currentUserId: Int): String {
    return when (message) {
        is Message -> if (message.sender) "我" else "对方"
        is GroupMessage -> if (message.senderId == currentUserId) "我" else message.senderName
        else -> ""
    }
}
