package component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Reply
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import core.loadImageBitmapWithCache
import model.Message
import model.User

/**
 * 消息长按菜单
 */
@Composable
fun MessageLongPressMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    message: Message,
    isSelf: Boolean,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onForward: () -> Unit,
    onReply: () -> Unit
) {
    if (!expanded) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("消息操作") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 消息预览
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = message.message.take(50) + if (message.message.length > 50) "..." else "",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.body2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 操作项
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onCopy()
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("复制消息", style = MaterialTheme.typography.body1)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onReply()
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Reply, contentDescription = "回复", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("回复消息", style = MaterialTheme.typography.body1)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onForward()
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Forward, contentDescription = "转发", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("转发消息", style = MaterialTheme.typography.body1)
                    }

                    if (isSelf) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDelete()
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colors.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "删除消息",
                                style = MaterialTheme.typography.body1,
                                color = MaterialTheme.colors.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 回复预览栏
 */
@Composable
fun ReplyPreviewBar(
    replyToMessage: Message?,
    senderName: String,
    onCancel: () -> Unit
) {
    if (replyToMessage == null) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colors.surface.copy(alpha = 0.3f),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "回复 $senderName",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.primary
                )
                Text(
                    text = replyToMessage.message.take(40) + if (replyToMessage.message.length > 40) "..." else "",
                    style = MaterialTheme.typography.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
                )
            }
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "取消回复", modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * 表情选择面板
 */
@Composable
fun EmojiPickerPanel(
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 常用表情列表
    val emojis = remember {
        listOf(
            "😊", "😂", "🤣", "❤️", "😍", "😒", "😳", "😜", "😎", "😭",
            "😱", "😡", "👍", "👎", "👏", "🙏", "🔥", "🎉", "🤔", "😴",
            "🤮", "🤢", "🤧", "🥵", "🥶", "😇", "🤠", "🥳", "😷", "🤒",
            "👻", "👽", "🤖", "🎃", "😺", "😸", "😹", "😻", "😼", "😽",
            "🙀", "😿", "😾", "💋", "👄", "👅", "👂", "👃", "👁", "👀",
            "👨", "👩", "👧", "👦", "👶", "👵", "👴", "👱", "👨‍🦰", "👨‍🦱",
            "👨‍🦳", "👨‍🦲", "👩‍🦰", "👩‍🦱", "👩‍🦳", "👩‍🦲", "🧔", "🧔‍♀️", "🧑", "🧒",
            "🧓", "👨‍💼", "👩‍💼", "👨‍🔧", "👩‍🔧", "👨‍🏫", "👩‍🏫", "👨‍⚕️", "👩‍⚕️", "👨‍🌾",
            "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍈",
            "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍅", "🥑", "🥦", "🥬"
        )
    }

    Card(
        modifier = modifier.height(200.dp),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        elevation = 4.dp
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 40.dp),
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(emojis) { emoji ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onEmojiSelected(emoji) }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, style = MaterialTheme.typography.h6)
                }
            }
        }
    }
}

/**
 * 转发选择对话框
 */
@Composable
fun ForwardSelectDialog(
    users: List<User>,
    onDismiss: () -> Unit,
    onForward: (User) -> Unit
) {
    val userList by remember(users) { mutableStateOf(users.filter { it.id > 0 }) }
    var selectedUser by remember { mutableStateOf<User?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择转发对象") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                if (userList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无联系人", color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(userList) { user ->
                            val isSelected = selectedUser?.id == user.id
                            var avatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

                            androidx.compose.runtime.LaunchedEffect(user.avatarUrl, user.avatarKey) {
                                user.avatarUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                    avatarBitmap = loadImageBitmapWithCache(url, user.avatarKey)
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedUser = user }
                                    .padding(vertical = 12.dp, horizontal = 8.dp)
                                    .background(
                                        color = if (isSelected) {
                                            MaterialTheme.colors.primary.copy(alpha = 0.1f)
                                        } else {
                                            Color.Transparent
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 头像
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colors.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (avatarBitmap != null) {
                                        Image(
                                            bitmap = avatarBitmap!!,
                                            contentDescription = "avatar",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(
                                            text = user.username.firstOrNull()?.toString() ?: "U",
                                            color = MaterialTheme.colors.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.username,
                                        style = MaterialTheme.typography.body1
                                    )
                                    Text(
                                        text = if (user.online == true) "在线" else "离线",
                                        style = MaterialTheme.typography.caption,
                                        color = if (user.online == true) {
                                            MaterialTheme.colors.secondary
                                        } else {
                                            MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                        }
                                    )
                                }

                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "已选择",
                                        tint = MaterialTheme.colors.primary
                                    )
                                }
                            }

                            Divider(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedUser?.let { user ->
                        onForward(user)
                        onDismiss()
                    }
                },
                enabled = selectedUser != null
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
