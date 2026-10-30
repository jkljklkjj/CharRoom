package component.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chatlite.i18n.LocalStrings
import component.bubbleShape
import component.qingliaoPalette
import component.sidebarHeaderBrush
import core.loadImageBitmapWithCache
import model.ChatMessage
import model.MessageType

/**
 * 统一消息气泡组件，供私聊和群聊复用。
 *
 * @param message 消息数据（通过 ChatMessage 接口统一访问）
 * @param isMine 是否是自己发送的消息
 * @param isDarkMode 是否深色模式
 * @param maxBubbleWidth 气泡最大宽度
 * @param bubbleBrush 气泡背景画刷
 * @param showSenderName 是否显示发送者名称（群聊用）
 * @param senderName 发送者名称（showSenderName=true 时使用）
 * @param showTimestamp 是否在气泡内显示时间戳
 * @param senderAvatar 对方头像内容（null 表示不显示）
 * @param myAvatar 我的头像内容（null 表示不显示）
 * @param onSenderAvatarClick 点击对方头像回调
 * @param onMyAvatarClick 点击我的头像回调
 * @param onResend 点击重发回调（null 表示不显示重发按钮）
 * @param onLongClick 长按回调
 */
@Composable
fun MessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    isDarkMode: Boolean,
    maxBubbleWidth: Dp,
    bubbleBrush: Brush,
    showSenderName: Boolean = false,
    senderName: String? = null,
    showTimestamp: Boolean = true,
    avatars: MessageAvatars = MessageAvatars(),
    onResend: (() -> Unit)? = null,
    onLongClick: () -> Unit,
) {
    val s = LocalStrings.current
    val palette = qingliaoPalette(isDarkMode)
    val bubbleBorderColor =
        if (isMine) {
            Color.Transparent
        } else {
            palette.line.copy(alpha = 0.65f)
        }
    val bubbleTextColor =
        if (isMine) {
            palette.onMessage
        } else {
            palette.ink
        }
    val bubbleShape = bubbleShape(isMine)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            // 重发按钮
            val retryable = isMine && message.chatDeliveryState == model.MessageDeliveryState.FAILED
            if (retryable && onResend != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = s["chat.resend"],
                    tint = MaterialTheme.colors.secondary,
                    modifier = Modifier.size(20.dp).clickable { onResend() },
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            // 对方头像
            if (!isMine && avatars.senderAvatar != null) {
                avatars.senderAvatar()
                Spacer(modifier = Modifier.width(8.dp))
            }

            // 气泡主体
            Box(
                modifier =
                    Modifier.widthIn(max = maxBubbleWidth)
                        .clip(bubbleShape)
                        .background(bubbleBrush)
                        .border(1.dp, bubbleBorderColor, bubbleShape)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = onLongClick,
                        )
                        .let {
                            if (isMine && !message.chatIsSent) it.alpha(0.7f) else it
                        }
                        .padding(horizontal = 15.dp, vertical = 12.dp)
            ) {
                Column {
                    // 发送者名称（群聊）
                    val showPeerName = showSenderName && !isMine
                    if (showPeerName && !senderName.isNullOrBlank()) {
                        Text(
                            text = senderName,
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.primary,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }

                    // 引用消息
                    message.chatReplyToContent?.let { replyContent ->
                        Surface(
                            color = MaterialTheme.colors.surface.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text =
                                        s["chat.reply.to"].format(
                                            message.chatReplyToSender.orEmpty()
                                        ),
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.primary,
                                    maxLines = 1,
                                )
                                Text(
                                    text = replyContent,
                                    style = MaterialTheme.typography.body2,
                                    color = bubbleTextColor.copy(alpha = 0.8f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    // 消息内容
                    MessageContent(
                        message = message,
                        bubbleTextColor = bubbleTextColor,
                    )

                    if (
                        isMine && message.chatDeliveryState != model.MessageDeliveryState.ACCEPTED
                    ) {
                        val key =
                            when (message.chatDeliveryState) {
                                model.MessageDeliveryState.QUEUED -> "chat.queued"
                                model.MessageDeliveryState.SENDING -> "chat.sending"
                                else -> "chat.send.failed"
                            }
                        Text(
                            s[key],
                            style = MaterialTheme.typography.caption,
                            color = bubbleTextColor.copy(alpha = 0.7f),
                        )
                    }

                    // 时间戳
                    if (showTimestamp) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatTime(message.chatTimestamp),
                            style = MaterialTheme.typography.caption,
                            color = bubbleTextColor.copy(alpha = 0.76f),
                            modifier =
                                Modifier.align(if (isMine) Alignment.End else Alignment.Start),
                        )
                    }
                }
            }

            // 我的头像
            if (isMine && avatars.myAvatar != null) {
                Spacer(modifier = Modifier.width(8.dp))
                avatars.myAvatar()
            }
        }
    }
}

/** 消息内容渲染（文本/图片/文件） */
@Composable
private fun MessageContent(
    message: ChatMessage,
    bubbleTextColor: androidx.compose.ui.graphics.Color,
) {
    val s = LocalStrings.current
    when (message.chatMessageType) {
        MessageType.TEXT -> {
            Text(
                text = message.chatText,
                style = MaterialTheme.typography.body1,
                color = bubbleTextColor,
            )
        }
        MessageType.IMAGE -> {
            message.chatFileUrl?.let { url ->
                var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(url) {
                    imageBitmap = loadImageBitmapWithCache(url, url)
                }
                imageBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = s["chat.image"],
                        modifier =
                            Modifier.fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(8.dp)),
                    )
                }
                    ?: Box(
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = bubbleTextColor,
                        )
                    }
            }
        }
        MessageType.FILE -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = s["chat.file"],
                    tint = bubbleTextColor,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = message.chatFileName.orEmpty(),
                        style = MaterialTheme.typography.body1,
                        color = bubbleTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatFileSize(message.chatFileSize ?: 0),
                        style = MaterialTheme.typography.caption,
                        color = bubbleTextColor.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

/** 对方头像（通用，加载网络图片） */
@Composable
fun AvatarImage(
    avatarUrl: String?,
    avatarKey: String?,
    size: Dp = 32.dp,
    isViewportReady: Boolean = true,
    username: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val s = LocalStrings.current
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(avatarUrl, avatarKey, isViewportReady) {
        bitmap =
            if (isViewportReady && !avatarUrl.isNullOrBlank()) {
                loadImageBitmapWithCache(avatarUrl, avatarKey)
            } else null
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = s["user.avatar"],
            modifier =
                Modifier.size(size)
                    .clip(CircleShape)
                    .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        )
    } else {
        // 占位符：显示用户名首字符（无用户名时用 ?）
        Box(
            modifier =
                Modifier.size(size)
                    .clip(CircleShape)
                    .background(
                        brush = sidebarHeaderBrush(true),
                        shape = CircleShape,
                    )
                    .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = username?.firstOrNull()?.toString() ?: "?",
                color = Color.White,
                style = MaterialTheme.typography.caption,
            )
        }
    }
}
