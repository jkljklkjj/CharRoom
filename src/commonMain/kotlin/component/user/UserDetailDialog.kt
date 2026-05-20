package component.user

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.draw.shadow
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
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colors.surface.copy(alpha = 0.24f),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.08f)),
                    elevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .shadow(8.dp, CircleShape, clip = true)
                                .clip(CircleShape)
                                .background(MaterialTheme.colors.primary.copy(alpha = 0.10f)),
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

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = user.username,
                            style = MaterialTheme.typography.h6,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Surface(
                            color = if (user.online == true) MaterialTheme.colors.secondary.copy(alpha = 0.14f) else MaterialTheme.colors.onSurface.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = when (user.online) {
                                    true -> "在线"
                                    false -> "离线"
                                    else -> "未知状态"
                                },
                                style = MaterialTheme.typography.caption,
                                color = if (user.online == true) MaterialTheme.colors.secondary else MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

                        user.signature?.takeIf { it.isNotBlank() }?.let { signature ->
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = signature,
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.72f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colors.surface.copy(alpha = 0.20f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.08f)),
                    elevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        DetailDialogRow("用户ID", user.id.toString())
                    }
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
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("发消息")
                }
            }
        },
        dismissButton = if (onSendMessage != null) {
            {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("关闭")
                }
            }
        } else {
            null
        }
    )
}

@Composable
private fun DetailDialogRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.58f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface
        )
    }
}
