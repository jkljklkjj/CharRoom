package component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import core.FileUploader
import core.LocalChatHistoryStore
import core.ServerConfig
import core.loadImageBitmapWithCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import model.GroupMessage
import model.MessageType
import model.User
import model.groupMessages
import model.users
import viewmodel.chatViewModel

/**
 * 群聊界面
 */
@Composable
fun GroupChatScreen(group: User) {
    var messageText by remember { mutableStateOf("") }
    // 使用derivedStateOf优化群聊消息过滤
    val filteredGroupMessages by remember(group.id) {
        derivedStateOf {
            groupMessages.filter { it.groupId == -group.id }
        }
    }
    var isSending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isDarkMode = !MaterialTheme.colors.isLight
    val clipboardManager = LocalClipboardManager.current
    val currentUserId = ServerConfig.id.toIntOrNull() ?: 0

    // 分页加载相关状态
    var currentPage by remember { mutableStateOf(1) } // 第0页已经在启动时加载
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMoreHistory by remember { mutableStateOf(true) }

    // 长按菜单相关状态
    var longPressMessage by remember { mutableStateOf<GroupMessage?>(null) }
    var showLongPressMenu by remember { mutableStateOf(false) }

    // 引用回复相关状态
    var replyToMessage by remember { mutableStateOf<GroupMessage?>(null) }

    // 表情面板相关状态
    var showEmojiPanel by remember { mutableStateOf(false) }

    // 文件上传相关状态
    var isUploading by remember { mutableStateOf(false) }

    // 转发相关状态
    var showForwardDialog by remember { mutableStateOf(false) }
    var forwardMessage by remember { mutableStateOf<GroupMessage?>(null) }

    fun submitMessage() {
        val text = messageText.trim()
        if (text.isEmpty() || isSending) {
            return
        }
        isSending = true
        messageText = ""

        sendMessage(
            user = group,
            messageText = text,
            replyToMessageId = replyToMessage?.messageId,
            replyToContent = replyToMessage?.text,
            replyToSender = replyToMessage?.senderName,
            onDone = {
                scope.launch {
                    isSending = false
                    replyToMessage = null // 发送后清空回复状态
                }
            }
        )
    }

    /**
     * 处理表情选择
     */
    fun onEmojiSelected(emoji: String) {
        messageText += emoji
        showEmojiPanel = false
    }

    /**
     * 处理图片选择
     */
    fun pickImage() {
        FilePicker.pickImage { bytes, fileName ->
            scope.launch {
                val maxFileSize = 5 * 1024 * 1024 // 5MB
                if (bytes.size > maxFileSize) {
                    // 可以在这里添加错误提示
                    isUploading = false
                    return@launch
                }

                isUploading = true
                val fileUrl = withContext(Dispatchers.IO) {
                    FileUploader.uploadImage(bytes, fileName)
                }
                if (fileUrl != null) {
                    // 发送图片消息
                    sendMessage(
                        user = group,
                        messageText = "",
                        messageType = MessageType.IMAGE,
                        fileUrl = fileUrl,
                        fileName = fileName,
                        fileSize = bytes.size.toLong(),
                        replyToMessageId = replyToMessage?.messageId,
                        replyToContent = replyToMessage?.text,
                        replyToSender = replyToMessage?.senderName,
                        onDone = {
                            replyToMessage = null
                        }
                    )
                }
                isUploading = false
            }
        }
    }

    /**
     * 复制消息内容
     */
    fun copyMessage(message: GroupMessage) {
        clipboardManager.setText(AnnotatedString(message.text))
    }

    /**
     * 删除消息
     */
    fun deleteMessage(message: GroupMessage) {
        // 仅本地删除
        val index = groupMessages.indexOfFirst { it.messageId == message.messageId }
        if (index != -1) {
            groupMessages.removeAt(index)
        }
    }

    /**
     * 转发消息
     */
    fun forwardMessage(message: GroupMessage, targetUser: User) {
        sendMessage(
            user = targetUser,
            messageText = message.text,
            messageType = message.messageType,
            fileUrl = message.fileUrl,
            fileName = message.fileName,
            fileSize = message.fileSize
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colors.surface.copy(alpha = 0.2f),
            shape = RoundedCornerShape(18.dp),
            elevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(chatHeaderBrush(isDarkMode))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF0A35E))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = group.username,
                            style = MaterialTheme.typography.h6,
                            color = MaterialTheme.colors.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "群聊灵感广场 · ${filteredGroupMessages.size} 条消息",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onBackground.copy(alpha = 0.72f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            color = MaterialTheme.colors.surface.copy(alpha = if (isDarkMode) 0.3f else 0.6f),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.08f)),
            elevation = 0.dp
        ) {
            // 监听滚动位置，滚动到顶部时加载更多历史
            LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
                if (!isLoadingMore && hasMoreHistory && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                    isLoadingMore = true
                    val olderMessages = LocalChatHistoryStore.getGroupMessagesPage(
                        accountId = ServerConfig.id,
                        groupId = -group.id,
                        page = currentPage,
                        pageSize = 50
                    )
                    if (olderMessages.isNotEmpty()) {
                        chatViewModel.prependGroupMessages(olderMessages)
                        currentPage++
                    } else {
                        hasMoreHistory = false
                    }
                    isLoadingMore = false
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
                state = listState,
                reverseLayout = false // 正常顺序，最新消息在底部
            ) {
                // 加载更多提示
                if (isLoadingMore) {
                    item(key = "loading_indicator") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colors.primary
                            )
                        }
                    }
                } else if (!hasMoreHistory && filteredGroupMessages.isNotEmpty()) {
                    item(key = "no_more_messages") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "没有更早的消息了",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = filteredGroupMessages,
                    key = { _, message -> message.messageId }
                ) { index, message ->
                    // 显示日期分隔线
                    if (index == 0 || !isSameDay(filteredGroupMessages[index - 1].timestamp, message.timestamp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp),
                                elevation = 0.dp,
                                modifier = Modifier.shadow(1.dp, RoundedCornerShape(12.dp))
                            ) {
                                Text(
                                    text = formatDate(message.timestamp),
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    val self = message.senderId == ServerConfig.id.toIntOrNull()
                    var visible by remember(message.messageId) { mutableStateOf(false) }
                    LaunchedEffect(message.messageId) {
                        visible = true
                    }

                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn() + scaleIn(
                            initialScale = 0.9f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        val bubbleBorderColor = if (self) {
                            Color.Transparent
                        } else {
                            MaterialTheme.colors.primary.copy(alpha = if (isDarkMode) 0.22f else 0.15f)
                        }
                        val bubbleTextColor = if (self) {
                            MaterialTheme.colors.onPrimary
                        } else {
                            MaterialTheme.colors.onSurface
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (self) Arrangement.End else Arrangement.Start
                        ) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                if (self && !message.isSent.value) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "重发",
                                        tint = MaterialTheme.colors.secondary,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable { resendMessage(User(-message.groupId, ""), message) }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }

                                // 非自己发送的消息显示发送者头像
                                if (!self) {
                                    val sender = remember(message.senderId) { users.find { it.id == message.senderId } }
                                    var senderAvatar by remember { mutableStateOf<ImageBitmap?>(null) }
                                    LaunchedEffect(sender?.avatarUrl, sender?.avatarKey) {
                                        senderAvatar = if (!sender?.avatarUrl.isNullOrBlank()) {
                                            loadImageBitmapWithCache(sender!!.avatarUrl!!, sender.avatarKey)
                                        } else {
                                            null
                                        }
                                    }
                                    if (senderAvatar != null) {
                                        Image(
                                            bitmap = senderAvatar!!,
                                            contentDescription = "avatar",
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .shadow(2.dp, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    } else {
                                        // 没有头像时显示首字母
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colors.primary)
                                                .shadow(2.dp, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = message.senderName.firstOrNull()?.toString() ?: "?",
                                                color = MaterialTheme.colors.onPrimary,
                                                style = MaterialTheme.typography.caption
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .widthIn(max = 460.dp)
                                        .shadow(
                                            elevation = if (self) 4.dp else 2.dp,
                                            shape = RoundedCornerShape(18.dp)
                                        )
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(messageBubbleBrush(self, isDarkMode))
                                        .border(1.dp, bubbleBorderColor, RoundedCornerShape(18.dp))
                                        .combinedClickable(
                                            onClick = {},
                                            onLongClick = {
                                                longPressMessage = message
                                                showLongPressMenu = true
                                            }
                                        )
                                        .let {
                                            // 发送中的消息添加半透明效果
                                            if (self && !message.isSent.value) {
                                                it.alpha(0.7f)
                                            } else {
                                                it
                                            }
                                        }
                                        .padding(horizontal = 11.dp, vertical = 9.dp)
                                ) {
                                    Column {
                                        if (!self) {
                                            Text(
                                                text = message.senderName,
                                                style = MaterialTheme.typography.caption,
                                                color = MaterialTheme.colors.secondary
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                        }

                                        // 显示引用的消息
                                        message.replyToContent?.let { replyContent ->
                                            Surface(
                                                color = MaterialTheme.colors.surface.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 6.dp)
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(8.dp)
                                                ) {
                                                    Text(
                                                        text = "回复 ${message.replyToSender.orEmpty()}",
                                                        style = MaterialTheme.typography.caption,
                                                        color = MaterialTheme.colors.primary,
                                                        maxLines = 1
                                                    )
                                                    Text(
                                                        text = replyContent,
                                                        style = MaterialTheme.typography.body2,
                                                        color = bubbleTextColor.copy(alpha = 0.8f),
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }

                                        // 消息内容
                                        when (message.messageType) {
                                            MessageType.TEXT -> {
                                                Text(
                                                    text = message.text,
                                                    style = MaterialTheme.typography.body1,
                                                    color = bubbleTextColor
                                                )
                                            }
                                            MessageType.IMAGE -> {
                                                message.fileUrl?.let { url ->
                                                    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
                                                    LaunchedEffect(url) {
                                                        imageBitmap = loadImageBitmapWithCache(url, url)
                                                    }
                                                    imageBitmap?.let { bitmap ->
                                                        Image(
                                                            bitmap = bitmap,
                                                            contentDescription = "图片",
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .heightIn(max = 200.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                        )
                                                    } ?: Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(150.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(24.dp),
                                                            color = bubbleTextColor
                                                        )
                                                    }
                                                }
                                            }
                                            MessageType.FILE -> {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.AttachFile,
                                                        contentDescription = "文件",
                                                        tint = bubbleTextColor,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = message.fileName.orEmpty(),
                                                            style = MaterialTheme.typography.body1,
                                                            color = bubbleTextColor,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = formatFileSize(message.fileSize ?: 0),
                                                            style = MaterialTheme.typography.caption,
                                                            color = bubbleTextColor.copy(alpha = 0.7f)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Text(
                                                text = formatGroupTime(message.timestamp),
                                                style = MaterialTheme.typography.caption,
                                                color = bubbleTextColor.copy(alpha = 0.74f),
                                                textAlign = TextAlign.End
                                            )
                                        }
                                    }
                                }

                                // 长按菜单
                                if (longPressMessage?.messageId == message.messageId) {
                                    MessageLongPressMenu(
                                        expanded = showLongPressMenu,
                                        onDismiss = { showLongPressMenu = false },
                                        message = message,
                                        isSelf = self,
                                        onCopy = { copyMessage(message) },
                                        onDelete = { deleteMessage(message) },
                                        onForward = {
                                            forwardMessage = message
                                            showLongPressMenu = false
                                            showForwardDialog = true
                                        },
                                        onReply = {
                                            replyToMessage = message
                                            showLongPressMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (self && !message.isSent.value) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Text(
                                text = "发送失败，点击图标重试",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.error
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        LaunchedEffect(filteredGroupMessages.size) {
            if (filteredGroupMessages.isNotEmpty()) {
                listState.animateScrollToItem(filteredGroupMessages.size - 1)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column {
            // 引用回复预览栏
            ReplyPreviewBar(
                replyToMessage = replyToMessage,
                senderName = replyToMessage?.senderName ?: "",
                onCancel = { replyToMessage = null }
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colors.surface.copy(alpha = if (isDarkMode) 0.36f else 0.8f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.08f)),
                elevation = 0.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 表情按钮
                        IconButton(
                            onClick = { showEmojiPanel = !showEmojiPanel },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEmotions,
                                contentDescription = "表情",
                                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                            )
                        }

                        // 附件按钮
                        IconButton(
                            onClick = {
                                // 弹出选择菜单：图片/文件
                                // 这里简化处理，先选择图片，后续可以扩展
                                pickImage()
                            },
                            modifier = Modifier.size(40.dp),
                            enabled = !isUploading && !isSending
                        ) {
                            if (isUploading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colors.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AttachFile,
                                    contentDescription = "附件",
                                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }

                        TextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            modifier = Modifier
                                .weight(1f)
                                .onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyUp && event.key == Key.Enter && !event.isShiftPressed && !isSending) {
                                        submitMessage()
                                        true
                                    } else {
                                        false
                                    }
                                },
                            placeholder = { Text("和群友聊点新鲜事...", color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)) },
                            colors = TextFieldDefaults.textFieldColors(
                                backgroundColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            maxLines = 5, // 最多显示5行，超过后滚动
                            textStyle = MaterialTheme.typography.body1
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        val sendInteraction = remember { MutableInteractionSource() }
                        val sendScale = rememberElasticScale(sendInteraction, pressedScale = 0.9f)
                        Button(
                            onClick = { submitMessage() },
                            enabled = !isSending && (messageText.isNotBlank() || replyToMessage != null),
                            interactionSource = sendInteraction,
                            modifier = Modifier
                                .height(42.dp)
                                .graphicsLayer {
                                    scaleX = sendScale
                                    scaleY = sendScale
                                },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = MaterialTheme.colors.primary,
                                contentColor = MaterialTheme.colors.onPrimary,
                                disabledBackgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.45f)
                            )
                        ) {
                            Text(if (isSending) "发送中..." else "发送")
                        }
                    }

                    // 表情面板
                    if (showEmojiPanel) {
                        EmojiPickerPanel(
                            onEmojiSelected = ::onEmojiSelected,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    // 转发选择对话框
    if (showForwardDialog) {
        ForwardSelectDialog(
            onDismiss = { showForwardDialog = false },
            onForward = { targetUser ->
                forwardMessage?.let { message ->
                    forwardMessage(message, targetUser)
                }
                showForwardDialog = false
            }
        )
    }
}

/**
 * 格式化消息时间
 */
private fun formatGroupTime(timestamp: Long): String {
    return try {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        sdf.format(java.util.Date(timestamp))
    } catch (_: Exception) {
        ""
    }
}

/**
 * 格式化日期
 */
private fun formatDate(timestamp: Long): String {
    return try {
        val now = System.currentTimeMillis()
        val msgDate = java.util.Date(timestamp)
        val nowDate = java.util.Date(now)

        val sdfDay = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val msgDay = sdfDay.format(msgDate)
        val nowDay = sdfDay.format(nowDate)

        return when {
            msgDay == nowDay -> "今天"
            msgDay == sdfDay.format(java.util.Date(now - 86400000)) -> "昨天"
            else -> {
                val sdf = java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.getDefault())
                sdf.format(msgDate)
            }
        }
    } catch (_: Exception) {
        ""
    }
}

/**
 * 判断两个时间戳是否是同一天
 */
private fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        sdf.format(java.util.Date(timestamp1)) == sdf.format(java.util.Date(timestamp2))
    } catch (_: Exception) {
        false
    }
}

/**
 * 格式化文件大小
 */
private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
        size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024))
        else -> String.format("%.1f GB", size / (1024.0 * 1024 * 1024))
    }
}
