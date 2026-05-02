package component

import core.ServerConfig
import core.Chat
import core.MsgType
import core.ApiService
import model.User
import model.Message
import model.GroupMessage
import model.MessageType
import model.messages
import model.updateList
import Util
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.netty.util.CharsetUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableStateOf
import core.buildAgentChatPayload
import core.buildChatPayload
import core.buildGroupChatPayload
import core.buildCheckPayload
import core.parseProtoResponse
import core.Action
import core.ActionLogger
import core.ActionType
import core.LocalChatHistoryStore
import model.groupMessages
import viewmodel.chatViewModel
import kotlin.collections.isNotEmpty
import kotlin.collections.last

private fun updateUserOnlineStatus(userId: Int, online: Boolean) {
    chatViewModel.updateUserOnlineStatus(userId, online)
}

fun appendAgentChunk(messageId: String, chunk: String) {
    if (chunk.isEmpty()) {
        return
    }
    val idx = messages.indexOfFirst { it.messageId == messageId }
    if (idx < 0) {
        messages += Message(
            senderId = ServerConfig.AGENT_ASSISTANT_ID,
            message = chunk,
            sender = false,
            receiverId = ServerConfig.AGENT_ASSISTANT_ID,
            timestamp = System.currentTimeMillis(),
            isSent = mutableStateOf(true),
            messageId = messageId
        )
        return
    }
    val target = messages[idx]
    messages[idx] = target.copy(message = target.message + chunk)
}

fun sendMessage(
    user: User,
    messageText: String,
    replyToMessageId: String? = null,
    replyToContent: String? = null,
    replyToSender: String? = null,
    messageType: MessageType = MessageType.TEXT,
    fileUrl: String? = null,
    fileName: String? = null,
    fileSize: Long? = null,
    onDone: (Boolean) -> Unit = {}
) {
    val normalizedMessage = messageText.trim()
    if (normalizedMessage.isEmpty() && messageType == MessageType.TEXT) {
        onDone(false)
        return
    }

    val currentTime = System.currentTimeMillis()

    if (ServerConfig.isAgentAssistant(user.id)) {
        val localCopy = Message(
            senderId = user.id,
            message = normalizedMessage,
            sender = true,
            timestamp = currentTime,
            isSent = mutableStateOf(true),
            replyToMessageId = replyToMessageId,
            replyToContent = replyToContent,
            replyToSender = replyToSender,
            messageType = messageType,
            fileUrl = fileUrl,
            fileName = fileName,
            fileSize = fileSize
        )
        messages += localCopy

        val userIdInt = ServerConfig.id.toIntOrNull() ?: 0
        val streamMessageId = currentTime.toString()
        messages += Message(
            senderId = user.id,
            message = "",
            sender = false,
            timestamp = System.currentTimeMillis(),
            isSent = mutableStateOf(true),
            messageId = streamMessageId
        )

        try {
            ActionLogger.log(
                Action(
                    type = ActionType.SEND_MESSAGE,
                    targetId = user.id.toString(),
                    metadata = mapOf("text" to normalizedMessage.take(64), "agent" to "true")
                )
            )
        } catch (_: Exception) {
        }

        val payload = buildAgentChatPayload(
            targetClientId = user.id.toString(),
            content = normalizedMessage,
            userId = userIdInt,
            timestamp = streamMessageId.toLongOrNull() ?: currentTime,
            replyToMessageId = replyToMessageId,
            replyToContent = replyToContent,
            replyToSender = replyToSender,
            messageType = messageType.ordinal,
            fileUrl = fileUrl,
            fileName = fileName,
            fileSize = fileSize
        )
        Chat.send(payload, MsgType.AGENT_CHAT, user.id.toString(), 1) { success, resp ->
            if (!success) {
                localCopy.isSent.value = false
                val idx = messages.indexOfFirst { it.messageId == streamMessageId }
                if (idx >= 0 && messages[idx].message.isBlank()) {
                    messages.removeAt(idx)
                }
            }
            onDone(success)
        }
        return
    }

    if (user.id > 0) {
        val localCopy = Message(
            senderId = user.id,
            message = normalizedMessage,
            sender = true,
            timestamp = currentTime,
            isSent = mutableStateOf(true),
            replyToMessageId = replyToMessageId,
            replyToContent = replyToContent,
            replyToSender = replyToSender,
            messageType = messageType,
            fileUrl = fileUrl,
            fileName = fileName,
            fileSize = fileSize
        )
        messages += localCopy

        try {
            ActionLogger.log(
                Action(
                    type = ActionType.SEND_MESSAGE,
                    targetId = user.id.toString(),
                    metadata = mapOf("text" to normalizedMessage.take(64))
                )
            )
        } catch (_: Exception) {
        }

        val userIdInt = ServerConfig.id.toIntOrNull() ?: 0
        val payload = buildChatPayload(
            targetClientId = user.id.toString(),
            content = normalizedMessage,
            userId = userIdInt,
            timestamp = currentTime,
            replyToMessageId = replyToMessageId,
            replyToContent = replyToContent,
            replyToSender = replyToSender,
            messageType = messageType.ordinal,
            fileUrl = fileUrl,
            fileName = fileName,
            fileSize = fileSize
        )

        Chat.send(payload, MsgType.CHAT, user.id.toString(), 1) { success, resp ->
            val delivered = if (!(success && resp.isNotEmpty())) {
                false
            } else {
                val lastBytes = resp.last() as? ByteArray
                if (lastBytes != null) {
                    val unwrap = parseProtoResponse(lastBytes)
                    !unwrap.hasEnvelope || unwrap.success
                } else {
                    false
                }
            }

            if (!delivered) {
                localCopy.isSent.value = false
            }
            onDone(delivered)
        }
        return
    }

    val outbound = GroupMessage(
        groupId = user.id,
        senderId = Integer.valueOf(ServerConfig.id),
        text = normalizedMessage,
        senderName = "",
        timestamp = currentTime,
        isSent = mutableStateOf(true),
        replyToMessageId = replyToMessageId,
        replyToContent = replyToContent,
        replyToSender = replyToSender,
        messageType = messageType,
        fileUrl = fileUrl,
        fileName = fileName,
        fileSize = fileSize
    )

    try {
        ActionLogger.log(
            Action(
                type = ActionType.SEND_MESSAGE,
                targetId = user.id.toString(),
                metadata = mapOf("text" to normalizedMessage.take(64), "group" to "true")
            )
        )
    } catch (_: Exception) {
    }

    val userIdInt = ServerConfig.id.toIntOrNull() ?: 0
    val payload = buildGroupChatPayload(
        targetClientId = user.id.toString(),
        content = normalizedMessage,
        userId = userIdInt,
        replyToMessageId = replyToMessageId,
        replyToContent = replyToContent,
        replyToSender = replyToSender,
        messageType = messageType.ordinal,
        fileUrl = fileUrl,
        fileName = fileName,
        fileSize = fileSize
    )

    Chat.send(payload, MsgType.GROUP_CHAT, user.id.toString(), 1) { success, resp ->
        val delivered = if (!(success && resp.isNotEmpty())) {
            false
        } else {
            val lastBytes = resp.last() as? ByteArray
            if (lastBytes != null) {
                val unwrap = parseProtoResponse(lastBytes)
                !unwrap.hasEnvelope || unwrap.success
            } else {
                false
            }
        }

        if (!delivered) {
            outbound.isSent.value = false
        }
        onDone(delivered)
    }
}

fun resendMessage(user: User, message: Message) {
    if (ServerConfig.isAgentAssistant(user.id)) {
        val streamMessageId = message.timestamp.toString()
        messages += Message(
            senderId = user.id,
            message = "",
            sender = false,
            timestamp = System.currentTimeMillis(),
            isSent = mutableStateOf(true),
            messageId = streamMessageId
        )

        CoroutineScope(Dispatchers.IO).launch {
            val payload = buildAgentChatPayload(
                targetClientId = user.id.toString(),
                content = message.message,
                userId = message.senderId,
                timestamp = streamMessageId.toLongOrNull() ?: message.timestamp,
                replyToMessageId = message.replyToMessageId,
                replyToContent = message.replyToContent,
                replyToSender = message.replyToSender,
                messageType = message.messageType.ordinal,
                fileUrl = message.fileUrl,
                fileName = message.fileName,
                fileSize = message.fileSize
            )
            Chat.send(payload, MsgType.AGENT_CHAT, user.id.toString(), 1) { success, _ ->
                if (success) {
                    message.isSent.value = true
                } else {
                    message.isSent.value = false
                    val idx = messages.indexOfFirst { it.messageId == streamMessageId }
                    if (idx >= 0 && messages[idx].message.isBlank()) {
                        messages.removeAt(idx)
                    }
                }
            }
        }
        return
    }

    val payload = buildChatPayload(
        targetClientId = user.id.toString(),
        content = message.message,
        userId = message.senderId,
        timestamp = message.timestamp,
        replyToMessageId = message.replyToMessageId,
        replyToContent = message.replyToContent,
        replyToSender = message.replyToSender,
        messageType = message.messageType.ordinal,
        fileUrl = message.fileUrl,
        fileName = message.fileName,
        fileSize = message.fileSize
    )

    Chat.send(payload, MsgType.CHAT, user.id.toString(), 1) { success, resp ->
        if (success && resp.isNotEmpty()) {
            val lastBytes = resp.last() as? ByteArray
            if (lastBytes != null) {
                val unwrap = parseProtoResponse(lastBytes)
                if (!unwrap.hasEnvelope || unwrap.success) message.isSent.value = true
            }
        }
    }
}

fun resendMessage(user: User, groupMessage: GroupMessage) {
    val payload = buildGroupChatPayload(
        targetClientId = user.id.toString(),
        content = groupMessage.text,
        userId = groupMessage.senderId,
        replyToMessageId = groupMessage.replyToMessageId,
        replyToContent = groupMessage.replyToContent,
        replyToSender = groupMessage.replyToSender,
        messageType = groupMessage.messageType.ordinal,
        fileUrl = groupMessage.fileUrl,
        fileName = groupMessage.fileName,
        fileSize = groupMessage.fileSize
    )
    Chat.send(payload, MsgType.GROUP_CHAT, user.id.toString(), 1) { success, resp ->
        if (success && resp.isNotEmpty()) {
            val lastBytes = resp.last() as? ByteArray
            if (lastBytes != null) {
                val unwrap = parseProtoResponse(lastBytes)
                if (unwrap.success) groupMessage.isSent.value = true
            }
        }
    }
}

@Composable
@OptIn(FlowPreview::class)
fun ChatApp(
    windowSize: DpSize,
    token: String,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onLogout: () -> Unit,
    onBackPressed: ((() -> Boolean) -> Unit)? = null // 安卓端返回键回调
) {
    ServerConfig.Token = token
    val scope = rememberCoroutineScope()
    var selectedUser by remember { mutableStateOf<User?>(null) }
    // 同步全局状态管理器中的选中用户状态
    LaunchedEffect(selectedUser) {
        chatViewModel.selectedUser = selectedUser
    }

    var showDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) } // 个人信息页面
    var showApplications by remember { mutableStateOf(false) } // 统一申请管理对话框
    var showUserDetail: User? by remember { mutableStateOf(null) } // 显示用户详情弹窗
    var clearHistoryHint by remember { mutableStateOf("") }

    fun handleCheckResponse(user: User, success: Boolean, resp: List<Any>) {
        if (!success || resp.isEmpty()) return
        scope.launch {
            val lastBytes = resp.last() as? ByteArray ?: return@launch
            val unwrap = parseProtoResponse(lastBytes)
            val dataStr = unwrap.dataJson ?: String(lastBytes, CharsetUtil.UTF_8)
            val map = Util.jsonToMap(dataStr)
            val online = map["online"] as? Boolean ?: false
            updateUserOnlineStatus(user.id, online)
            if (selectedUser?.id == user.id) {
                selectedUser = selectedUser?.copy(online = online)
            }
        }
    }

    // 注册返回键处理逻辑
    LaunchedEffect(showDialog, showProfile, showSettings, showApplications, showUserDetail, selectedUser) {
        onBackPressed?.invoke {
            when {
                showDialog -> {
                    showDialog = false
                    true
                }
                showProfile -> {
                    showProfile = false
                    true
                }
                showSettings -> {
                    showSettings = false
                    true
                }
                showApplications -> {
                    showApplications = false
                    true
                }
                showUserDetail != null -> {
                    showUserDetail = null
                    true
                }
                selectedUser != null -> {
                    selectedUser = null
                    true
                }
                else -> false // 没有可返回的页面，让系统处理（退出应用）
            }
        }
    }

    fun openSearchDialog() {
        try {
            ActionLogger.log(Action(type = ActionType.SEARCH, metadata = mapOf("ui" to "sidebar_search")))
        } catch (_: Exception) {
        }
        showDialog = true
    }

    val animatedChatTransition = @Composable {
        AnimatedContent(
            targetState = selectedUser?.id,
            transitionSpec = {
                (slideInHorizontally(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) { fullWidth -> fullWidth / 3 } + fadeIn() + scaleIn(initialScale = 0.96f)).togetherWith(
                    slideOutHorizontally { fullWidth -> -fullWidth / 6 } + fadeOut()
                ).using(SizeTransform(clip = false))
            },
            label = "chat-pane-transition"
        ) { targetId ->
            val targetUser = selectedUser?.takeIf { it.id == targetId }
            if (targetUser == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "从左侧选择会话，开始沉浸聊天",
                        style = MaterialTheme.typography.subtitle1,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.72f)
                    )
                }
            } else {
                if (targetUser.id < 0) {
                    GroupChatScreen(
                        group = targetUser,
                        onAvatarClick = { user ->
                            if (user.id > 0 && !ServerConfig.isAgentAssistant(user.id)) {
                                showUserDetail = user
                            }
                        },
                        onMyAvatarClick = {
                            // 点击自己的头像打开个人信息页面
                            showProfile = true
                        }
                    )
                } else {
                    ChatScreen(
                        user = targetUser,
                        onAvatarClick = { user ->
                            if (user.id > 0 && !ServerConfig.isAgentAssistant(user.id)) {
                                showUserDetail = user
                            }
                        },
                        onMyAvatarClick = {
                            // 点击自己的头像打开个人信息页面
                            showProfile = true
                        }
                    )
                }
            }
        }
    }

    // 启动时先恢复本地历史，再并发启动自动保存、离线拉取和长连接
    LaunchedEffect(ServerConfig.id) {
        val accountId = ServerConfig.id

        // 注册登录状态监听器，token失效时自动退出
        val authListener = Chat.AuthStateListener { reason ->
            scope.launch(Dispatchers.Main) {
                // 清除本地保存的无效凭证
                try {
                    val authFile = java.io.File(System.getProperty("user.home"), ".qingliao/auth.txt")
                    if (authFile.exists()) authFile.delete()
                } catch (_: Exception) {}
                // 回到登录页面
                onLogout()
            }
        }
        Chat.addAuthStateListener(authListener)

        try {
            // 启动时只加载最近100条消息，降低内存占用
            val restored = withContext(Dispatchers.IO) { LocalChatHistoryStore.restorePage(accountId, page = 0, pageSize = 100) }
            messages.clear()
            messages += restored.privateMessages
            groupMessages.clear()
            groupMessages += restored.groupMessages

            launch {
                snapshotFlow {
                    LocalChatHistoryStore.capture(messages, groupMessages)
                }
                    .distinctUntilChanged()
                    .debounce(350)
                    .collect { snapshot ->
                        if (ServerConfig.Token.isNotBlank()) {
                            withContext(Dispatchers.IO) {
                                LocalChatHistoryStore.save(accountId, snapshot)
                            }
                        }
                    }
            }

            launch(Dispatchers.IO) {
                while (true) {
                    val resp = ApiService.getOfflineMessages()
                    if (resp.isEmpty()) {
                        break
                    }
                    messages += resp
                }
            }

            launch(Dispatchers.IO) {
                Chat.start()
            }
        } finally {
            // 页面销毁时移除监听器
            Chat.removeAuthStateListener(authListener)
        }
    }

    // 返回键处理已经移到Activity层面实现，兼容性更好

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = immersiveBackgroundBrush(isDarkMode))
    ) {
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .offset(x = (-110).dp, y = (-100).dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colors.secondary.copy(alpha = 0.26f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 100.dp, y = 110.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colors.primary.copy(alpha = 0.24f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }

        // 响应式布局判断：宽度大于700dp时使用双栏布局
        val isWideScreen = windowSize.width > 700.dp

        if (isWideScreen) {
            Row(Modifier.fillMaxSize().padding(12.dp)) {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 320.dp) // 侧边栏最大宽度限制
                        .weight(1f)
                        .fillMaxHeight(),
                    color = MaterialTheme.colors.surface.copy(alpha = if (isDarkMode) 0.52f else 0.72f),
                    shape = RoundedCornerShape(22.dp),
                    elevation = 8.dp
                ) {
                    Box(Modifier.fillMaxSize()) {
                        UserList(
                            selectedUserId = selectedUser?.id,
                            onOpenSearch = { openSearchDialog() },
                            onOpenSettings = { showSettings = true },
                            onOpenApplications = { showApplications = true },
                            onOpenProfile = { showProfile = true }, // 打开个人信息页面
                            onUserClick = { user ->
                                selectedUser = user
                                if (user.id > 0 && !ServerConfig.isAgentAssistant(user.id)) {
                                    val payload = buildCheckPayload(user.id.toString())

                                    Chat.send(payload, MsgType.CHECK, user.id.toString(), 1) { success, resp ->
                                        handleCheckResponse(user, success, resp)
                                    }
                                }
                            },
                            onUserLongClick = { user ->
                                if (user.id > 0 && !ServerConfig.isAgentAssistant(user.id)) { // 普通用户才能查看详情
                                    showUserDetail = user
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Surface(
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxHeight(),
                    color = MaterialTheme.colors.surface.copy(alpha = if (isDarkMode) 0.5f else 0.66f),
                    shape = RoundedCornerShape(22.dp),
                    elevation = 8.dp
                ) {
                    animatedChatTransition()
                }
            }
        } else {
            if (selectedUser == null) {
                Surface(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    color = MaterialTheme.colors.surface.copy(alpha = if (isDarkMode) 0.54f else 0.74f),
                    shape = RoundedCornerShape(20.dp),
                    elevation = 6.dp
                ) {
                    UserList(
                        selectedUserId = selectedUser?.id,
                        onOpenSearch = { openSearchDialog() },
                        onOpenSettings = { showSettings = true },
                        onOpenApplications = { showApplications = true },
                        onOpenProfile = { showProfile = true }, // 打开个人信息页面
                        onUserClick = { user ->
                            selectedUser = user
                            if (user.id > 0 && !ServerConfig.isAgentAssistant(user.id)) {
                                val payload = buildCheckPayload(user.id.toString())
                                Chat.send(payload, MsgType.CHECK, user.id.toString(), 1) { success, resp ->
                                    handleCheckResponse(user, success, resp)
                                }
                            }
                        },
                        onUserLongClick = { user ->
                            if (user.id > 0 && !ServerConfig.isAgentAssistant(user.id)) { // 普通用户才能查看详情
                                showUserDetail = user
                            }
                        }
                    )
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    color = MaterialTheme.colors.surface.copy(alpha = if (isDarkMode) 0.5f else 0.66f),
                    shape = RoundedCornerShape(20.dp),
                    elevation = 6.dp
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 移动端返回按钮
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colors.surface.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                            elevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(chatHeaderBrush(isDarkMode))
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "返回",
                                    tint = MaterialTheme.colors.onBackground,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable { selectedUser = null }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "返回会话列表",
                                    style = MaterialTheme.typography.subtitle2,
                                    color = MaterialTheme.colors.onBackground
                                )
                            }
                        }

                        // 聊天内容区域
                        Box(modifier = Modifier.weight(1f)) {
                            animatedChatTransition()
                        }
                    }
                }
            }
        }

        if (showDialog) {
            AddUserOrGroupDialog { showDialog = false }
        }

        if (showApplications) {
            ApplicationDialog(
                onDismiss = { showApplications = false }
            )
        }

        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                title = { Text("设置") },
                text = {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("夜间模式", modifier = Modifier.weight(1f))
                            Switch(
                                checked = isDarkMode,
                                onCheckedChange = { onToggleDarkMode() }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                showSettings = false
                                showProfile = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("个人信息")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                messages.clear()
                                groupMessages.clear()
                                selectedUser = null

                                val cleared = LocalChatHistoryStore.clear(ServerConfig.id)
                                clearHistoryHint = if (cleared) "聊天历史已清空" else "清空失败，请稍后重试"
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.secondary)
                        ) {
                            Text("清空聊天历史")
                        }

                        if (clearHistoryHint.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = clearHistoryHint,
                                style = MaterialTheme.typography.caption,
                                color = if (clearHistoryHint.contains("失败")) MaterialTheme.colors.error else MaterialTheme.colors.secondary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                showSettings = false
                                runCatching {
                                    val snapshot = LocalChatHistoryStore.capture(messages, groupMessages)
                                    LocalChatHistoryStore.save(ServerConfig.id, snapshot)
                                }
                                Chat.logoutAndDisconnect()
                                onLogout()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.error)
                        ) {
                            Text("退出登录")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettings = false }) {
                        Text("关闭")
                    }
                }
            )
        }

        // 个人信息页面
        if (showProfile) {
            ProfileScreen(
                onBack = { showProfile = false },
                onProfileUpdated = {
                    // 更新用户信息后刷新好友列表
                    scope.launch {
                        updateList()
                    }
                }
            )
        }

        // 用户详情页面
        showUserDetail?.let { user ->
            UserDetailScreen(
                userId = user.id,
                onBack = { showUserDetail = null },
                onAddFriend = {
                    showUserDetail = null
                    // 添加好友后刷新列表
                    scope.launch {
                        updateList()
                    }
                }
            )
        }

        // 个人信息页面
        if (showProfile) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colors.background
            ) {
                ProfileScreen(
                    onBack = { showProfile = false },
                    onProfileUpdated = {
                        // 个人信息更新后刷新用户列表
                        scope.launch {
                            model.updateList()
                        }
                    }
                )
            }
        }

        // 用户详情弹窗
        showUserDetail?.let { user ->
            UserDetailDialog(
                user = user,
                onDismiss = { showUserDetail = null },
                onSendMessage = {
                    selectedUser = user
                }
            )
        }
    }
}