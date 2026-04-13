package component

import core.ServerConfig
import core.Chat
import core.MsgType
import core.ApiService
import model.User
import model.Message
import model.GroupMessage
import model.messages
import model.users
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.netty.util.CharsetUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import core.buildChatPayload
import core.buildGroupChatPayload
import core.buildCheckPayload
import core.parseProtoResponse
import core.Action
import core.ActionLogger
import core.ActionType

private fun updateUserOnlineStatus(userId: Int, online: Boolean) {
    users = users.map { user ->
        if (user.id == userId) user.copy(online = online) else user
    }
}

fun sendMessage(
    user: User,
    messageText: String,
    onDone: (Boolean) -> Unit = {}
) {
    val normalizedMessage = messageText.trim()
    if (normalizedMessage.isEmpty()) {
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
            isSent = mutableStateOf(true)
        )
        messages += localCopy

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

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reply = ApiService.callAgent(normalizedMessage)
                if (reply.isBlank()) {
                    localCopy.isSent.value = false
                    onDone(false)
                    return@launch
                }

                messages += Message(
                    senderId = user.id,
                    message = reply,
                    sender = false,
                    timestamp = System.currentTimeMillis(),
                    isSent = mutableStateOf(true)
                )
                onDone(true)
            } catch (_: Exception) {
                localCopy.isSent.value = false
                onDone(false)
            }
        }
        return
    }

    if (user.id > 0) {
        val localCopy = Message(
            senderId = user.id,
            message = normalizedMessage,
            sender = true,
            timestamp = currentTime,
            isSent = mutableStateOf(true)
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
        val payload = buildChatPayload(user.id.toString(), normalizedMessage, userIdInt, currentTime)

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
        isSent = mutableStateOf(true)
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
    val payload = buildGroupChatPayload(user.id.toString(), normalizedMessage, userIdInt)

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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reply = ApiService.callAgent(message.message)
                if (reply.isNotBlank()) {
                    message.isSent.value = true
                    messages += Message(
                        senderId = user.id,
                        message = reply,
                        sender = false,
                        timestamp = System.currentTimeMillis(),
                        isSent = mutableStateOf(true)
                    )
                }
            } catch (_: Exception) {
            }
        }
        return
    }

    val payload = buildChatPayload(user.id.toString(), message.message, message.senderId, message.timestamp)

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
    val payload = buildGroupChatPayload(user.id.toString(), groupMessage.text, groupMessage.senderId)
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
fun ChatApp(
    windowSize: DpSize,
    token: String,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onLogout: () -> Unit
) {
    ServerConfig.Token = token
    var selectedUser by remember { mutableStateOf<User?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

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
                if (targetUser.id < 0) GroupChatScreen(targetUser) else ChatScreen(targetUser)
            }
        }
    }

    // 拉取离线消息
    LaunchedEffect(Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            launch {
                while (true) {
                    val resp = ApiService.getOfflineMessages()
                    if (resp.isEmpty()) break
                    messages += resp
                }
            }
            launch { // 启动 Chat
                Chat.start()
            }
        }
    }

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

        if (windowSize.width > windowSize.height) {
            Row(Modifier.fillMaxSize().padding(12.dp)) {
                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    color = MaterialTheme.colors.surface.copy(alpha = if (isDarkMode) 0.52f else 0.72f),
                    shape = RoundedCornerShape(22.dp),
                    elevation = 8.dp
                ) {
                    Box(Modifier.fillMaxSize()) {
                        UserList(
                            selectedUserId = selectedUser?.id,
                            onOpenSearch = { openSearchDialog() },
                            onOpenSettings = { showSettings = true }
                        ) { user ->
                            selectedUser = user
                            if (user.id > 0 && !ServerConfig.isAgentAssistant(user.id)) {
                                val payload = buildCheckPayload(user.id.toString())

                                Chat.send(payload, MsgType.CHECK, user.id.toString(), 1) { success, resp ->
                                    if (success && resp.isNotEmpty()) {
                                        val lastBytes = resp.last() as? ByteArray
                                        if (lastBytes != null) {
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
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Surface(
                    modifier = Modifier.weight(2f).fillMaxHeight(),
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
                        onOpenSettings = { showSettings = true }
                    ) { user ->
                        selectedUser = user
                        if (user.id > 0 && !ServerConfig.isAgentAssistant(user.id)) {
                            val payload = buildCheckPayload(user.id.toString())
                            Chat.send(payload, MsgType.CHECK, user.id.toString(), 1) { success, resp ->
                                if (success && resp.isNotEmpty()) {
                                    val lastBytes = resp.last() as? ByteArray
                                    if (lastBytes != null) {
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
                            }
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    color = MaterialTheme.colors.surface.copy(alpha = if (isDarkMode) 0.5f else 0.66f),
                    shape = RoundedCornerShape(20.dp),
                    elevation = 6.dp
                ) {
                    animatedChatTransition()
                }
            }
        }

        if (showDialog) {
            AddUserOrGroupDialog { showDialog = false }
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
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        var isDarkMode by remember { mutableStateOf(false) }
        ChatApp(
            windowSize = DpSize(800.dp, 600.dp),
            token = "token",
            isDarkMode = isDarkMode,
            onToggleDarkMode = { isDarkMode = !isDarkMode },
            onLogout = {}
        )
    }
}