package component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import core.loadImageBitmapFromUrl
import model.User
import kotlinx.coroutines.launch

/**
 * 用户详情弹窗
 */
@Composable
fun UserDetailDialog(
    user: User,
    onDismiss: () -> Unit,
    onSendMessage: (() -> Unit)? = null // 发送消息按钮回调，可选
) {
    var avatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val scope = rememberCoroutineScope()

    // 加载头像
    LaunchedEffect(user.avatarUrl, user.avatarKey) {
        scope.launch {
            user.avatarUrl?.let { url ->
                if (url.isNotBlank()) {
                    avatarBitmap = loadImageBitmapFromUrl(url, user.avatarKey)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("用户信息")
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 头像
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colors.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarBitmap != null) {
                        Image(
                            bitmap = avatarBitmap!!,
                            contentDescription = "头像",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = user.username.firstOrNull()?.toString() ?: "U",
                            style = MaterialTheme.typography.h4,
                            color = MaterialTheme.colors.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 用户名
                Text(
                    text = user.username,
                    style = MaterialTheme.typography.h6,
                    textAlign = TextAlign.Center
                )

                // 在线状态
                Text(
                    text = when (user.online) {
                        true -> "在线"
                        false -> "离线"
                        else -> "未知状态"
                    },
                    style = MaterialTheme.typography.caption,
                    color = if (user.online == true) MaterialTheme.colors.secondary else MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 个性签名（如果有）
                user.signature?.takeIf { it.isNotBlank() }?.let { signature ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "个性签名",
                            style = MaterialTheme.typography.subtitle2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = signature,
                            style = MaterialTheme.typography.body1,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 用户ID
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "用户ID",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = user.id.toString(),
                        style = MaterialTheme.typography.body2
                    )
                }
            }
        },
        confirmButton = {
            onSendMessage?.let { onSend ->
                Button(
                    onClick = {
                        onDismiss()
                        onSend()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("发消息")
                }
            }
        },
        dismissButton = if (onSendMessage != null) {
            {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("关闭")
                }
            }
        } else {
            null
        }
    )
}
