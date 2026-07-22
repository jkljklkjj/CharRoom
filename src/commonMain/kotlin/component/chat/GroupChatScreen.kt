package component.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import model.Group
import model.GroupMessage
import model.MessageType
import model.User
import core.FileUploader
import component.io.FilePicker
import core.LocalChatHistoryStore
import component.chatHeaderBrush
import component.messageBubbleBrush
import com.chatlite.i18n.LocalStrings
import core.state.GlobalAppState
import model.Message
import presentation.viewmodel.ChatViewModel

/**
 * 群聊界面
 */
@Composable
fun GroupChatScreen(
    chatViewModel: ChatViewModel,
    group: Group,
    token: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAvatarClick: ((User) -> Unit)? = null, // 点击消息头像回调
    onMyAvatarClick: (() -> Unit)? = null // 点击自己头像回调
) {
    var messageText by remember { mutableStateOf("") }
    // 从ViewModel收集状态
    val allGroupMessages by chatViewModel.groupMessagesFlow.collectAsState()
    val allUsers by chatViewModel.usersFlow.collectAsState()

    // 使用derivedStateOf优化消息过滤，只有groupMessages变化或group变化时才重新计算
    val groupMessageList by remember(group.id, allGroupMessages) {
        derivedStateOf {
            allGroupMessages.filter { it.groupId == group.id }
        }
    }
    // 获取当前登录用户信息（自己）
    val currentUser: User? by remember(allUsers) {
        derivedStateOf {
            allUsers.find { user -> user.id == GlobalAppState.currentUserId }
        }
    }

    // 消息气泡宽度适配所有设备
    // 固定最大宽度220dp（约11个汉字），在手机和平板上都有良好的显示效果
    val maxBubbleWidth = 220.dp
    var historyQuery by remember { mutableStateOf("") }
    val filteredMessages by remember(group.id, historyQuery) {
        derivedStateOf {
            val query = historyQuery.trim().lowercase()
            groupMessageList.filter { message ->
                if (query.isBlank()) return@filter true
                message.text.lowercase().contains(query) ||
                    message.senderName.lowercase().contains(query)
            }
        }
    }
    var isSending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    // 键盘弹起时滚动到底部，等待布局稳定后执行
    LaunchedEffect(imeVisible) {
        if (imeVisible && filteredMessages.isNotEmpty()) {
            delay(100)
            runCatching {
                listState.animateScrollToItem(filteredMessages.lastIndex)
            }
        }
    }
    val scope = rememberCoroutineScope()
    val isDarkMode = !MaterialTheme.colors.isLight
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    // 消息动画控制：仅新消息第一次显示时才有动画，滚动历史消息无动画
    val animatedMessageIds = remember { mutableSetOf<String>() }
    val pageCreateTime = remember { System.currentTimeMillis() }

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

    var hasInitializedScroll by remember(group.id) { mutableStateOf(false) }
    var isViewportReady by remember(group.id) { mutableStateOf(false) }
    val s = LocalStrings.current

    fun submitMessage() {
        val text = messageText.trim()
        if (text.isEmpty() || isSending) {
            return
        }
        isSending = true
        messageText = ""

        sendGroupMessage(
            chatViewModel = chatViewModel,
            group = group,
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
                    sendGroupMessage(
                        chatViewModel = chatViewModel,
                        group = group,
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
     * 处理文件选择
     */
    fun pickFile() {
        FilePicker.pickFile { bytes, fileName, fileSize ->
            scope.launch {
                val maxFileSize = 5 * 1024 * 1024 // 5MB
                if (fileSize > maxFileSize) {
                    // 可以在这里添加错误提示
                    isUploading = false
                    return@launch
                }

                isUploading = true
                val fileUrl = withContext(Dispatchers.IO) {
                    FileUploader.uploadFile(bytes, fileName)
                }
                if (fileUrl != null) {
                    // 发送文件消息
                    sendGroupMessage(
                        chatViewModel = chatViewModel,
                        group = group,
                        messageText = "",
                        messageType = MessageType.FILE,
                        fileUrl = fileUrl,
                        fileName = fileName,
                        fileSize = fileSize,
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
        chatViewModel.deleteGroupMessage(message.messageId)
    }

    /**
     * 转发消息到指定用户（私聊）
     */
    fun forwardMessage(message: GroupMessage, targetUser: User) {
        chatViewModel.sendPrivateMessage(
            user = targetUser,
            messageText = message.text,
            messageType = message.messageType,
            fileUrl = message.fileUrl,
            fileName = message.fileName,
            fileSize = message.fileSize
        )
    }

    Column(modifier = modifier.fillMaxSize().padding(14.dp).statusBarsPadding().imePadding()) {
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
                    Column {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.h6,
                            color = MaterialTheme.colors.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = s["groupChat.title"],
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onBackground.copy(alpha = 0.72f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        TextField(
            value = historyQuery,
            onValueChange = { historyQuery = it },
            placeholder = { Text(text = s["chat.search.group.history"]) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.14f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colors.primary
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth().alpha(if (isViewportReady) 1f else 0f),
            color = MaterialTheme.colors.surface.copy(alpha = if (isDarkMode) 0.3f else 0.6f),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.08f)),
            elevation = 0.dp
        ) {
            // 监听滚动位置，滚动到顶部时加载更多历史
            LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, hasInitializedScroll) {
                if (hasInitializedScroll && !isLoadingMore && hasMoreHistory && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                    isLoadingMore = true
                    val olderMessages = LocalChatHistoryStore.getGroupMessagesPage(
                        accountId = GlobalAppState.currentUserId.toString(),
                        groupId = group.id,
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
                } else if (!hasMoreHistory && groupMessageList.isNotEmpty()) {
                    item(key = "no_more_messages") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = s["chat.no.earlier.messages"],
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = filteredMessages,
                    key = { index, message -> "${message.messageId}_$index" }
                ) { index, message ->
                    // 判断是否是自己发送的消息，定义在最外层作用域
                    val isMine = message.senderId == currentUser?.id

                    // 显示日期分隔线
                    if (index == 0 || !isSameDay(filteredMessages[index - 1].timestamp, message.timestamp)) {
                        DateSeparator(timestamp = message.timestamp)
                    }

                    // 动画规则：仅新消息（页面打开后新收到/新发送的）显示弹出动画
                    // 历史消息（包括滚动加载的）直接显示，无动画
                    val messageTime = message.timestamp
                    val isNewMessage = messageTime > pageCreateTime && !animatedMessageIds.contains(message.messageId)
                    var visible by remember(message.messageId) { mutableStateOf(!isNewMessage) }

                    LaunchedEffect(message.messageId) {
                        if (isNewMessage) {
                            visible = true
                            animatedMessageIds.add(message.messageId)
                        }
                    }

                    AnimatedVisibility(
                        visible = visible,
                        enter = if (isNewMessage) fadeIn() + scaleIn(
                            initialScale = 0.9f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ) else fadeIn(initialAlpha = 1f), // 历史消息无动画直接显示
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            MessageBubble(
                                message = message,
                                isMine = isMine,
                                isDarkMode = isDarkMode,
                                maxBubbleWidth = maxBubbleWidth,
                                bubbleBrush = messageBubbleBrush(isMine, isDarkMode),
                                showSenderName = true,
                                senderName = message.senderName,
                                showTimestamp = false,
                                senderAvatar = if (!isMine) {
                                    {
                                        val senderUser = allUsers.find { it.id == message.senderId }
                                        AvatarImage(
                                            avatarUrl = senderUser?.avatarUrl,
                                            avatarKey = senderUser?.avatarKey,
                                            isViewportReady = isViewportReady,
                                            onClick = { senderUser?.let { onAvatarClick?.invoke(it) } }
                                        )
                                    }
                                } else null,
                                myAvatar = if (isMine) {
                                    {
                                        AvatarImage(
                                            avatarUrl = currentUser?.avatarUrl,
                                            avatarKey = currentUser?.avatarKey,
                                            isViewportReady = isViewportReady,
                                            onClick = { onMyAvatarClick?.invoke() }
                                        )
                                    }
                                } else null,
                                onResend = { resendGroupMessage(chatViewModel, group, message) },
                                onLongClick = {
                                    longPressMessage = message
                                    showLongPressMenu = true
                                }
                            )

                            // 长按菜单
                            if (longPressMessage?.messageId == message.messageId) {
                                // 将GroupMessage转换为Message适配公共组件
                                val adapterMessage = Message(
                                    senderId = message.senderId,
                                    message = message.text,
                                    sender = isMine,
                                    receiverId = group.id,
                                    timestamp = message.timestamp,
                                    isSent = message.isSent,
                                    messageId = message.messageId,
                                    replyToMessageId = message.replyToMessageId,
                                    replyToContent = message.replyToContent,
                                    replyToSender = message.replyToSender,
                                    messageType = message.messageType,
                                    fileUrl = message.fileUrl,
                                    fileName = message.fileName,
                                    fileSize = message.fileSize
                                )
                                MessageLongPressMenu(
                                    expanded = showLongPressMenu,
                                    onDismiss = { showLongPressMenu = false },
                                    message = adapterMessage,
                                    isSelf = isMine,
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
                                    },
                                    onShare = { /* shareText unavailable on Android */ }
                                )
                            }
                        }
                    }

                    if (isMine && !message.isSent) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Text(
                                text = s["chat.send.failed"],
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.error
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        // 组件挂载和消息变化时自动滚动到底部
        LaunchedEffect(group.id, groupMessageList.size) {
            if (groupMessageList.isNotEmpty()) {
                if (!hasInitializedScroll) {
                    hasInitializedScroll = true
                    kotlinx.coroutines.delay(16)
                    runCatching {
                        listState.scrollToItem(groupMessageList.lastIndex)
                    }
                    isViewportReady = true
                    return@LaunchedEffect
                }

                kotlinx.coroutines.delay(50)
                runCatching {
                    listState.scrollToItem(groupMessageList.lastIndex)
                }
                kotlinx.coroutines.delay(50)
                runCatching {
                    listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 聊天输入框
        ChatInputBar(
            messageText = messageText,
            onTextChange = { messageText = it },
            onSubmit = { submitMessage() },
            isSending = isSending,
            isUploading = isUploading,
            replyPreview = replyToMessage?.let { msg ->
                {
                    // 将GroupMessage转换为Message适配公共组件
                    val adapterMessage = Message(
                        senderId = msg.senderId,
                        message = msg.text,
                        sender = msg.senderId == GlobalAppState.currentUserId,
                        receiverId = group.id,
                        timestamp = msg.timestamp,
                        isSent = msg.isSent,
                        messageId = msg.messageId,
                        replyToMessageId = msg.replyToMessageId,
                        replyToContent = msg.replyToContent,
                        replyToSender = msg.replyToSender,
                        messageType = msg.messageType,
                        fileUrl = msg.fileUrl,
                        fileName = msg.fileName,
                        fileSize = msg.fileSize
                    )
                    ReplyPreviewBar(
                        replyToMessage = adapterMessage,
                        senderName = msg.senderName.orEmpty(),
                        onCancel = { replyToMessage = null }
                    )
                }
            },
            onEmojiClick = { showEmojiPanel = !showEmojiPanel },
            onAttachClick = { pickImage() }
        )

        // 表情面板
        if (showEmojiPanel) {
            EmojiPickerPanel(
                onEmojiSelected = ::onEmojiSelected,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // 转发选择对话框
    if (showForwardDialog) {
        ForwardSelectDialog(
            users = allUsers,
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
 * 发送群消息
 */
fun sendGroupMessage(
    chatViewModel: ChatViewModel,
    group: Group,
    messageText: String,
    messageType: MessageType = MessageType.TEXT,
    fileUrl: String? = null,
    fileName: String? = null,
    fileSize: Long? = null,
    replyToMessageId: String? = null,
    replyToContent: String? = null,
    replyToSender: String? = null,
    onDone: () -> Unit = {}
) {
    // 委托给ViewModel处理，使用ViewModel的协程作用域
    chatViewModel.sendGroupMessage(
        group = group,
        messageText = messageText,
        messageType = messageType,
        fileUrl = fileUrl,
        fileName = fileName,
        fileSize = fileSize,
        replyToMessageId = replyToMessageId,
        replyToContent = replyToContent,
        replyToSender = replyToSender,
        onDone = onDone
    )
}

/**
 * 重发群消息
 */
fun resendGroupMessage(chatViewModel: ChatViewModel, group: Group, message: GroupMessage) {
    // 删除原来的消息
    chatViewModel.deleteGroupMessage(message.messageId)

    // 重新发送
    sendGroupMessage(
        chatViewModel = chatViewModel,
        group = group,
        messageText = message.text,
        messageType = message.messageType,
        fileUrl = message.fileUrl,
        fileName = message.fileName,
        fileSize = message.fileSize,
        replyToMessageId = message.replyToMessageId,
        replyToContent = message.replyToContent,
        replyToSender = message.replyToSender
    )
}

// formatTime, formatDate, isSameDay, formatFileSize 已提取到 component.chat.ChatUtils
