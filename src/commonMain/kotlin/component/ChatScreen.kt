package component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.AlertDialog
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import model.Message
import model.MessageType
import model.User
import core.FileUploader
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.IconButton
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import component.io.FilePicker
import component.UserAvatar
import core.LocalChatHistoryStore
import core.ServerConfig
import core.loadImageBitmapWithCache
import core.state.GlobalAppState
import presentation.viewmodel.ChatViewModel
import com.chatlite.i18n.LocalStrings
import com.chatlite.i18n.Strings
import com.chatlite.i18n.currentStrings

/**
 * 发送消息
 */
fun sendMessage(
    chatViewModel: ChatViewModel,
    user: User,
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
    chatViewModel.sendPrivateMessage(
        user = user,
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
 * 重发消息
 */
fun resendMessage(chatViewModel: ChatViewModel, user: User, message: Message) {
    // 删除原来的消息
    chatViewModel.deleteMessage(message.messageId)

    // 重新发送
    sendMessage(
        chatViewModel = chatViewModel,
        user = user,
        messageText = message.message,
        messageType = message.messageType,
        fileUrl = message.fileUrl,
        fileName = message.fileName,
        fileSize = message.fileSize,
        replyToMessageId = message.replyToMessageId,
        replyToContent = message.replyToContent,
        replyToSender = message.replyToSender
    )
}

/**
 * 好友聊天界面
 */
@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel,
    user: User,
    onAvatarClick: ((User) -> Unit)? = null, // 点击消息头像回调
    onMyAvatarClick: (() -> Unit)? = null, // 点击自己头像回调
    onBackClick: (() -> Unit)? = null, // 点击返回按钮回调
    onVideoCallClick: ((String) -> Unit)? = null, // 点击视频通话按钮回调
    onUserMenuClick: ((User) -> Unit)? = null // 点击好友菜单回调（查看资料/删除）
) {
    // 添加进入动画，避免闪动
    var isReady by remember(user.id) { mutableStateOf(false) }
    LaunchedEffect(user.id) {
        kotlinx.coroutines.delay(50) // 短暂延迟让界面准备好
        isReady = true
    }

    // 使用 Crossfade 实现平滑过渡
    Crossfade(
        targetState = isReady,
        animationSpec = tween(150, easing = FastOutSlowInEasing)
    ) { ready ->
        if (ready) {
            ChatScreenContent(
                chatViewModel = chatViewModel,
                user = user,
                onAvatarClick = onAvatarClick,
                onMyAvatarClick = onMyAvatarClick,
                onBackClick = onBackClick,
                onVideoCallClick = onVideoCallClick,
                onUserMenuClick = onUserMenuClick
            )
        } else {
            // 加载状态：显示占位符
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatScreenContent(
    chatViewModel: ChatViewModel,
    user: User,
    onAvatarClick: ((User) -> Unit)? = null,
    onMyAvatarClick: (() -> Unit)? = null,
    onBackClick: (() -> Unit)? = null,
    onVideoCallClick: ((String) -> Unit)? = null,
    onUserMenuClick: ((User) -> Unit)? = null
) {
    val s = LocalStrings.current
    var messageText by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // 从ViewModel收集消息状态
    val allMessages by chatViewModel.messagesFlow.collectAsState()
    // 从ViewModel收集用户列表状态
    val allUsers by chatViewModel.usersFlow.collectAsState()

    // 消息气泡宽度适配所有设备
    // 固定最大宽度220dp（约11个汉字），在手机和平板上都有良好的显示效果
    // 既不会过窄也不会过长，不需要动态计算屏幕宽度，跨平台兼容性最好
    val maxBubbleWidth = 220.dp

    // 使用derivedStateOf优化消息过滤，只有messages变化或user变化时才重新计算
    val userMessages by remember(user.id, allMessages) {
        derivedStateOf {
            val filtered = allMessages.filter { message ->
                // 显示和当前用户的所有对话：我发给对方的 + 对方发给我的
                (message.receiverId == user.id && message.sender) || // 我发的消息
                (message.senderId == user.id && !message.sender)   // 对方发的消息（包括AI）
            }
//            println("[ChatScreen] 用户 ${user.id} 的消息过滤结果：总数 ${allMessages.size} → 显示 ${filtered.size}")
            filtered
        }
    }
    // 获取当前登录用户信息（自己）
    val currentUser: User? by remember(allUsers) {
        derivedStateOf {
            allUsers.find { user -> user.id == GlobalAppState.currentUserId }
        }
    }
    var historyQuery by remember { mutableStateOf("") }
    val filteredMessages by remember(user.id, historyQuery) {
        derivedStateOf {
            val query = historyQuery.trim().lowercase()
            userMessages.filter { message ->
                if (query.isBlank()) return@filter true
                val senderName = if (message.sender) {
                    currentUser?.username ?: s["chat.me"]
                } else {
                    user.username
                }
                message.message.lowercase().contains(query) ||
                    senderName.lowercase().contains(query)
            }
        }
    }
    // 只保留最新 100 条消息（类似 QQ 展示策略）
    val displayMessages by remember {
        derivedStateOf {
            val msgs = filteredMessages
            if (msgs.size > 100) msgs.subList(msgs.size - 100, msgs.size) else msgs
        }
    }
    var isSending by remember { mutableStateOf(false) }
    val listState = remember(user.id) {
        LazyListState(displayMessages.lastIndex.coerceAtLeast(0), 0)
    }
    val inputFocusRequester = remember { FocusRequester() }
    var isInputFocused by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    // 当输入法弹起时（IME 可见），稍等布局稳定后贴底最新消息，解决键盘弹起后气泡未跟随的问题
    LaunchedEffect(imeVisible) {
        if (imeVisible && displayMessages.isNotEmpty()) {
            // 等待系统布局/动画稳定
            delay(100)
            runCatching {
                listState.animateScrollToItem(displayMessages.lastIndex)
            }
        }
    }
    val scope = rememberCoroutineScope()
    val isDarkMode = !MaterialTheme.colors.isLight
    val clipboardManager = LocalClipboardManager.current
    // 消息动画控制：仅新消息第一次显示时才有动画，滚动历史消息无动画
    val animatedMessageIds = remember { mutableSetOf<String>() }
    val pageCreateTime = remember { System.currentTimeMillis() }

    // 分页加载相关状态
    var currentPage by remember { mutableStateOf(1) } // 第0页已经在启动时加载
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMoreHistory by remember { mutableStateOf(true) }

    // 长按菜单相关状态
    var longPressMessage by remember { mutableStateOf<Message?>(null) }
    var showLongPressMenu by remember { mutableStateOf(false) }

    // 引用回复相关状态
    var replyToMessage by remember { mutableStateOf<Message?>(null) }

    // 表情面板相关状态
    var showEmojiPanel by remember { mutableStateOf(false) }

    // 文件上传相关状态
    var isUploading by remember { mutableStateOf(false) }

    // 转发相关状态
    var showForwardDialog by remember { mutableStateOf(false) }
    var forwardMessage by remember { mutableStateOf<Message?>(null) }

    var hasInitializedScroll by remember(user.id) { mutableStateOf(false) }
    var isViewportReady by remember(user.id) { mutableStateOf(false) }

    // 智能自动滚动：只显示最新 N 条，初始定位到底部（类似 QQ）
    // 首次进入时不滚动动画，直接定位到底部
    LaunchedEffect(user.id) {
        // 等待列表布局完成
        kotlinx.coroutines.delay(100)
        if (filteredMessages.isNotEmpty()) {
            runCatching {
                listState.scrollToItem(filteredMessages.lastIndex)
            }
        }
        isViewportReady = true
    }

    // 新消息到达时，仅在接近底部时跟随滚动
    LaunchedEffect(filteredMessages.size) {
        if (!isViewportReady || filteredMessages.isEmpty()) return@LaunchedEffect

        val lastIndex = filteredMessages.size - 1
        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        val isNearBottom = lastVisibleIndex >= lastIndex - 3

        if (isNearBottom) {
            listState.animateScrollToItem(lastIndex)
        }
    }

    fun submitMessage() {
        val text = messageText.trim()
        if (text.isEmpty() || isSending) {
            return
        }
        isSending = true
        messageText = ""

        sendMessage(
            chatViewModel = chatViewModel,
            user = user,
            messageText = text,
            replyToMessageId = replyToMessage?.messageId,
            replyToContent = replyToMessage?.message,
            replyToSender = if (replyToMessage?.sender == true) s["chat.me"] else user.username,
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
                        chatViewModel = chatViewModel,
                        user = user,
                        messageText = "",
                        messageType = MessageType.IMAGE,
                        fileUrl = fileUrl,
                        fileName = fileName,
                        fileSize = bytes.size.toLong(),
                        replyToMessageId = replyToMessage?.messageId,
                        replyToContent = replyToMessage?.message,
                        replyToSender = if (replyToMessage?.sender == true) s["chat.me"] else user.username,
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
                    sendMessage(
                        chatViewModel = chatViewModel,
                        user = user,
                        messageText = "",
                        messageType = MessageType.FILE,
                        fileUrl = fileUrl,
                        fileName = fileName,
                        fileSize = fileSize,
                        replyToMessageId = replyToMessage?.messageId,
                        replyToContent = replyToMessage?.message,
                        replyToSender = if (replyToMessage?.sender == true) s["chat.me"] else user.username,
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
    fun copyMessage(message: Message) {
        clipboardManager.setText(AnnotatedString(message.message))
    }

    /**
     * 删除消息
     */
    fun deleteMessage(message: Message) {
        // 通过ViewModel删除消息
        chatViewModel.deleteMessage(message.messageId)
    }

    /**
     * 转发消息
     */
    fun forwardMessage(message: Message, targetUser: User) {
        sendMessage(
            chatViewModel = chatViewModel,
            user = targetUser,
            messageText = message.message,
            messageType = message.messageType,
            fileUrl = message.fileUrl,
            fileName = message.fileName,
            fileSize = message.fileSize
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp).statusBarsPadding().imePadding()) {
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
                    // 返回按钮（仅在onBackClick存在时显示）
                    onBackClick?.let { onBack ->
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = s["chat.back"],
                                tint = MaterialTheme.colors.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // 用户头像：使用统一的头像组件
                    UserAvatar(
                        user = user,
                        size = 40.dp,
                        onClick = { onAvatarClick?.invoke(user) }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (user.online == true) Color(0xFF4CAF50) else Color(0xFF9AA5B1))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = user.username,
                            style = MaterialTheme.typography.h6,
                            color = MaterialTheme.colors.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (user.online == true) s["chat.status.online"] else if (user.online == false) s["chat.status.offline"] else s["chat.status.syncing"],
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onBackground.copy(alpha = 0.72f)
                        )
                    }

                    // 视频通话按钮
                    IconButton(
                        onClick = { onVideoCallClick?.invoke(user.id) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text(
                            text = "📹",
                            contentDescription = s["call.video.btn"]
                        )
                    }

                    // 好友菜单按钮（查看资料、删除好友）
                    Box {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Text(
                                text = "⋯",
                                contentDescription = s["friend.menu.info"]
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                onClick = {
                                    showMenu = false
                                    onUserMenuClick?.invoke(user)
                                }
                            ) {
                                Text(s["friend.menu.info"])
                            }
                            DropdownMenuItem(
                                onClick = {
                                    showMenu = false
                                    showDeleteConfirm = true
                                }
                            ) {
                                Text(
                                    s["friend.menu.delete"],
                                    color = MaterialTheme.colors.error
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

//        TextField(
//            value = historyQuery,
//            onValueChange = { historyQuery = it },
//            placeholder = { Text(text = "搜索本地聊天历史，支持消息内容和发送者") },
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(horizontal = 4.dp),
//            colors = TextFieldDefaults.textFieldColors(
//                backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.14f),
//                focusedIndicatorColor = Color.Transparent,
//                unfocusedIndicatorColor = Color.Transparent,
//                cursorColor = MaterialTheme.colors.primary
//            )
//        )

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
                    val olderMessages = LocalChatHistoryStore.getPrivateMessagesPage(
                        accountId = GlobalAppState.currentUserId.toString(),
                        userId = user.id,
                        page = currentPage,
                        pageSize = 50
                    )
                    if (olderMessages.isNotEmpty()) {
                        chatViewModel.prependMessages(olderMessages)
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
                } else if (!hasMoreHistory && userMessages.isNotEmpty()) {
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
                    items = displayMessages,
                    key = { index, message -> "${message.messageId}_$index" }
                ) { index, message ->
                    // 显示日期分隔线
                    if (index == 0 || !isSameDay(filteredMessages[index - 1].timestamp, message.timestamp)) {
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
                        enter = if (isNewMessage) fadeIn(
                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                        ) + scaleIn(
                            initialScale = 0.85f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) + slideInVertically(
                            initialOffsetY = { it / 4 },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) else fadeIn(initialAlpha = 1f), // 历史消息无动画直接显示
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        val bubbleBorderColor = if (message.sender) {
                            Color.Transparent
                        } else {
                            MaterialTheme.colors.primary.copy(alpha = if (isDarkMode) 0.24f else 0.14f)
                        }
                        val bubbleTextColor = if (message.sender) {
                            MaterialTheme.colors.onPrimary
                        } else {
                            MaterialTheme.colors.onSurface
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (message.sender) Arrangement.End else Arrangement.Start
                        ) {
                                    Row(verticalAlignment = Alignment.Bottom) {
                                if (message.sender && !message.isSent) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = s["chat.resend"],
                                        tint = MaterialTheme.colors.secondary,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable { resendMessage(chatViewModel, user, message) }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }

                                // If message is from the other user, show their avatar on the left
                                if (!message.sender) {
                                    // 消息头像：使用统一的头像组件
                                    UserAvatar(
                                        user = user,
                                        size = 32.dp,
                                        onClick = { onAvatarClick?.invoke(user) }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Box(
                                    modifier = Modifier
                                        .widthIn(max = maxBubbleWidth) // 最大宽度为屏幕的40%，内容短自动适配
                                        .shadow(
                                            elevation = if (message.sender) 6.dp else 3.dp,
                                            shape = RoundedCornerShape(18.dp),
                                            ambientColor = Color.Black.copy(alpha = 0.1f),
                                            spotColor = Color.Black.copy(alpha = 0.08f)
                                        )
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(refinedMessageBubbleBrush(message.sender, isDarkMode))
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
                                            if (message.sender && !message.isSent) {
                                                it.alpha(0.7f)
                                            } else {
                                                it
                                            }
                                        }
                                        .padding(horizontal = 11.dp, vertical = 9.dp)
                                ) {
                                    Column {
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
                                                        text = s["chat.reply.to"].format(message.replyToSender.orEmpty()),
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
                                                    text = message.message,
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
                                                            contentDescription = s["chat.image"],
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
                                                        contentDescription = s["chat.file"],
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

                                        // 已移除聊天气泡内的时间戳显示
                                    }
                                }

                                // 如果是自己发送的消息，在右侧显示自己的头像
                                if (message.sender) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    // 我的头像：使用统一的头像组件
                                    if (currentUser != null) {
                                        UserAvatar(
                                            user = currentUser!!,
                                            size = 32.dp,
                                            onClick = { onMyAvatarClick?.invoke() }
                                        )
                                    }
                                }

                                // 长按菜单
                                if (longPressMessage?.messageId == message.messageId) {
                                    MessageLongPressMenu(
                                        expanded = showLongPressMenu,
                                        onDismiss = { showLongPressMenu = false },
                                        message = message,
                                        isSelf = message.sender,
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

                    if (message.sender && !message.isSent) {
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

        // agent 流式输出时，同一条消息内容会持续增长，需要在内容变化后再次贴底
        if (ServerConfig.isAgentAssistant(user.id)) {
            val latestMessage = userMessages.lastOrNull()
            LaunchedEffect(latestMessage?.messageId, latestMessage?.message) {
                if (latestMessage != null) {
                    kotlinx.coroutines.delay(16)
                    runCatching {
                        listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column {
            // 引用回复预览栏
            ReplyPreviewBar(
                replyToMessage = replyToMessage,
                senderName = if (replyToMessage?.sender == true) s["chat.me"] else user.username,
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
                                contentDescription = s["chat.emoji"],
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
                                    contentDescription = s["chat.attachment"],
                                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }

                        TextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(inputFocusRequester)
                                .onFocusChanged { state ->
                                    val focused = state.isFocused
                                    if (focused && !isInputFocused) {
                                        isInputFocused = true
                                        // 输入框获焦时滚动到底部，覆盖因键盘弹起导致消息被遮挡的问题
                                        scope.launch {
                                            if (filteredMessages.isNotEmpty()) {
                                                listState.animateScrollToItem(filteredMessages.lastIndex)
                                            }
                                        }
                                    } else if (!focused) {
                                        isInputFocused = false
                                    }
                                }
                                .onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyUp && event.key == Key.Enter && !event.isShiftPressed && !isSending) {
                                        submitMessage()
                                        true
                                    } else {
                                        false
                                    }
                                },
                            placeholder = { Text(s["chat.placeholder"], color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)) },
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
                            Text(if (isSending) s["chat.sending"] else s["chat.send"])
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

    // 删除好友确认对话框
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(s["friend.delete.title"]) },
            text = { Text(s["friend.delete.confirm"]) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error),
                    onClick = {
                        showDeleteConfirm = false
                        chatViewModel.deleteFriend(user.id) { success ->
                            if (success) {
                                chatViewModel.loadContacts()
                            }
                        }
                    }
                ) {
                    Text(s["friend.delete.title"])
                }
            },
            dismissButton = {
                Button(onClick = { showDeleteConfirm = false }) {
                    Text(s["dialog.add.cancel"])
                }
            }
        )
    }
}

/**
 * 格式化消息时间
 */
private fun formatTime(timestamp: Long): String {
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
            msgDay == nowDay -> currentStrings["chat.today"]
            msgDay == sdfDay.format(java.util.Date(now - 86400000)) -> currentStrings["chat.yesterday"]
            else -> {
                val sdf = java.text.SimpleDateFormat(currentStrings["chat.date.format"], java.util.Locale.getDefault())
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
@Suppress("DefaultLocale")
private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
        size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024))
        else -> String.format("%.1f GB", size / (1024.0 * 1024 * 1024))
    }
}
