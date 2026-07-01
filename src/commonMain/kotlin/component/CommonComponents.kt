package component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.loadImageBitmapWithCache
import core.getCachedImage
import model.Message
import model.User
import com.chatlite.i18n.LocalStrings

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
    onReply: () -> Unit,
    onShare: (() -> Unit)? = null
) {
    if (!expanded) return
    val s = LocalStrings.current

    ModernDialog(onDismissRequest = onDismiss) {
        Text(
            text = s["message.actions"],
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))

        // 消息预览
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.05f),
            shape = RoundedCornerShape(8.dp),
            elevation = 0.dp
        ) {
            Text(
                text = message.message.take(50) + if (message.message.length > 50) "..." else "",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.body2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 操作项
        listOfNotNull(
            Triple(Icons.Default.ContentCopy, s["message.copy"], { onCopy(); onDismiss() }),
            if (onShare != null) Triple(Icons.Default.Share, s["message.share"], { onShare(); onDismiss() }) else null,
            Triple(Icons.Default.Reply, s["message.reply"], { onReply(); onDismiss() }),
            Triple(Icons.Default.Forward, s["message.forward"], { onForward(); onDismiss() }),
            if (isSelf) Triple(Icons.Default.Delete, s["message.delete"], { onDelete(); onDismiss() }) else null
        ).forEach { (icon, label, action) ->
            val itemInteraction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = itemInteraction,
                        indication = null
                    ) { action() }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    modifier = Modifier.size(20.dp),
                    tint = if (label == s["message.delete"]) MaterialTheme.colors.error
                           else MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.body1,
                    color = if (label == s["message.delete"]) MaterialTheme.colors.error
                            else MaterialTheme.colors.onSurface
                )
            }
            if (label != s["message.delete"]) {
                Divider(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.06f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(s["message.cancel"])
        }
    }
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
    val s = LocalStrings.current
    if (replyToMessage == null) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colors.surface.copy(alpha = 0.3f),
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .background(MaterialTheme.colors.primary, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = s["chat.reply.to"].format(senderName),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.primary
                )
                Text(
                    text = replyToMessage.message.take(40) + if (replyToMessage.message.length > 40) "..." else "",
                    style = MaterialTheme.typography.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
            }
            val closeInteraction = remember { MutableInteractionSource() }
            val closeScale = rememberElasticScale(closeInteraction, pressedScale = 0.86f)
            IconButton(
                onClick = onCancel,
                interactionSource = closeInteraction,
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer { scaleX = closeScale; scaleY = closeScale }
            ) {
                Icon(Icons.Default.Close, contentDescription = s["message.cancel.reply"], modifier = Modifier.size(18.dp))
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
    val s = LocalStrings.current
    val userList by remember(users) { mutableStateOf(users.filter { it.id > 0 }) }
    var selectedUser by remember { mutableStateOf<User?>(null) }

    ModernDialog(onDismissRequest = onDismiss) {
        Text(
            text = s["message.forward.select"],
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (userList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(s["message.forward.no.contacts"], color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                items(userList, key = { it.id }) { user ->
                    val isSelected = selectedUser?.id == user.id
                    var avatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

                    LaunchedEffect(user.avatarUrl, user.avatarKey) {
                        user.avatarUrl?.takeIf { it.isNotBlank() }?.let { url ->
                            avatarBitmap = loadImageBitmapWithCache(url, user.avatarKey)
                        }
                    }

                    val itemInteraction = remember { MutableInteractionSource() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = itemInteraction, indication = null) { selectedUser = user }
                            .background(
                                color = if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.08f)
                                        else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colors.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatarBitmap != null) {
                                Image(bitmap = avatarBitmap!!, contentDescription = "avatar", modifier = Modifier.fillMaxSize())
                            } else {
                                Text(user.username.firstOrNull()?.toString() ?: "U", color = MaterialTheme.colors.primary)
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = user.username,
                                style = MaterialTheme.typography.body1,
                                color = MaterialTheme.colors.onSurface
                            )
                            Text(
                                text = if (user.online == true) s["user.detail.online"] else s["user.detail.offline"],
                                style = MaterialTheme.typography.caption,
                                color = if (user.online == true) MaterialTheme.colors.secondary
                                        else MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                            )
                        }

                        if (isSelected) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = s["message.forward.selected"],
                                tint = MaterialTheme.colors.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Divider(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colors.onSurface.copy(alpha = 0.04f))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text(s["message.cancel"])
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    selectedUser?.let { user ->
                        onForward(user)
                        onDismiss()
                    }
                },
                enabled = selectedUser != null,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = MaterialTheme.colors.onPrimary
                )
            ) {
                Text(s["message.forward.confirm"])
            }
        }
    }
}

/**
 * 统一的头像组件
 */
@Composable
fun UserAvatar(
    user: User,
    size: Dp = 40.dp,
    onClick: (() -> Unit)? = null
) {
    val s = LocalStrings.current
    // 在 remember 中立即从缓存加载，避免闪动
    val avatarBitmapState = remember(user.id) {
        // 先从缓存同步读取
        val cached = if (!user.avatarUrl.isNullOrBlank()) {
            getCachedImage(user.avatarUrl!!)
        } else {
            null
        }
        mutableStateOf(cached)
    }

    var isAvatarLoading by remember { mutableStateOf(false) }
    val avatarBitmap = avatarBitmapState.value

    LaunchedEffect(user.avatarUrl, user.avatarKey) {
        if (!isAvatarLoading && avatarBitmap == null && !user.avatarUrl.isNullOrBlank()) {
            isAvatarLoading = true
            avatarBitmapState.value = loadImageBitmapWithCache(user.avatarUrl!!, user.avatarKey)
            isAvatarLoading = false
        }
    }

    // 使用 Crossfade 实现平滑过渡，避免闪动
    Crossfade(
        targetState = avatarBitmap,
        animationSpec = tween(200, easing = FastOutSlowInEasing)
    ) { bitmap ->
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = s["user.avatar"],
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .clickable { onClick?.invoke() }
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colors.primary)
                    .clickable { onClick?.invoke() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.username.firstOrNull()?.toString() ?: "U",
                    color = Color.White,
                    style = MaterialTheme.typography.caption
                )
            }
        }
    }
}

/**
 * 统一的页面顶栏组件
 *
 * @param title 主标题
 * @param subtitle 副标题（可选）
 * @param onBack 返回回调（为 null 时不显示返回按钮）
 * @param actions 右侧操作按钮（可选）
 * @param useGradient 是否使用渐变背景（默认 true，与侧边栏/聊天顶栏风格一致）
 */
@Composable
fun AppTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null,
    useGradient: Boolean = true,
    modifier: Modifier = Modifier
) {
    val s = LocalStrings.current
    val isDark = !MaterialTheme.colors.isLight

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (useGradient) Color.Transparent else MaterialTheme.colors.surface.copy(alpha = 0.18f),
        shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
        elevation = 0.dp
    ) {
        Box(
            modifier = if (useGradient) {
                Modifier
                    .fillMaxWidth()
                    .background(chatHeaderBrush(isDark))
            } else {
                Modifier.fillMaxWidth()
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = s["chat.back"],
                            tint = MaterialTheme.colors.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.h6,
                        color = MaterialTheme.colors.onBackground
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }

                if (actions != null) {
                    Row(content = actions)
                }
            }
        }
    }
}
