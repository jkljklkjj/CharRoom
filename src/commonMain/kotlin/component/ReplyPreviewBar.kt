package component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import model.GroupMessage
import model.Message
import model.MessageType

/**
 * 引用回复预览栏，显示在输入框上方
 */
@Composable
fun ReplyPreviewBar(
    replyToMessage: Any?,
    senderName: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (replyToMessage == null) return

    val content = getMessagePreviewContent(replyToMessage)
    val isDarkMode = !MaterialTheme.colors.isLight

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isDarkMode) MaterialTheme.colors.surface.copy(alpha = 0.5f)
                else MaterialTheme.colors.primary.copy(alpha = 0.05f)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧竖线标识
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(32.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colors.primary)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 内容区域
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "回复 $senderName",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.primary,
                maxLines = 1
            )
            Text(
                text = content,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 关闭按钮
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "取消回复",
            modifier = Modifier
                .size(20.dp)
                .clickable { onCancel() },
            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
        )
    }
}

/**
 * 获取消息预览内容
 */
private fun getMessagePreviewContent(message: Any): String {
    return when (message) {
        is Message -> when (message.messageType) {
            MessageType.TEXT -> message.message
            MessageType.IMAGE -> "[图片]"
            MessageType.FILE -> "[文件] ${message.fileName.orEmpty()}"
        }
        is GroupMessage -> when (message.messageType) {
            MessageType.TEXT -> message.text
            MessageType.IMAGE -> "[图片]"
            MessageType.FILE -> "[文件] ${message.fileName.orEmpty()}"
        }
        else -> ""
    }
}
