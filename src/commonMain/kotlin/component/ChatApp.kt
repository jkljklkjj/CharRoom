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
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import core.buildAgentChatPayload
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

        val userIdInt = ServerConfig.id.toIntOrNull() ?: 0
        val payload = buildAgentChatPayload(user.id.toString(), normalizedMessage, userIdInt, currentTime)

        Chat.send(payload, MsgType.AGENT_CHAT, user.id.toString(), 1) { success, resp ->
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
    val payload = if (ServerConfig.isAgentAssistant(user.id)) {
        buildAgentChatPayload(user.id.toString(), message.message, message.senderId, message.timestamp)
    } else {
        buildChatPayload(user.id.toString(), message.message, message.senderId, message.timestamp)
    }
    val type = if (ServerConfig.isAgentAssistant(user.id)) MsgType.AGENT_CHAT else MsgType.CHAT

    Chat.send(payload, type, user.id.toString(), 1) { success, resp ->
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
fun ChatApp(windowSize: DpSize, token: String) {
    ServerConfig.Token = token
    var selectedUser by remember { mutableStateOf<User?>(null) }
    var showDialog by remember { mutableStateOf(false) }

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

    Box(modifier = Modifier.fillMaxSize()) {
        if (windowSize.width > windowSize.height) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    UserList { user ->
                        selectedUser = user
                        if (user.id > 0 && !ServerConfig.isAgentAssistant(user.id)) {
                            // build CHECK wrapper via builder
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
                Box(Modifier.weight(2f)) {
                    selectedUser?.let { u -> if (u.id < 0) GroupChatScreen(u) else ChatScreen(u) }
                }
            }
        } else {
            if (selectedUser == null) {
                UserList { user ->
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
            } else {
                selectedUser?.let { u -> if (u.id < 0) GroupChatScreen(u) else ChatScreen(u) }
            }
        }

        IconButton(onClick = {
            // log search action
            try { ActionLogger.log(Action(type = ActionType.SEARCH, metadata = mapOf("ui" to "top_search"))) } catch (_: Exception) {}
            showDialog = true
        }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            Icon(Icons.Default.Search, contentDescription = "搜索")
        }
        if (showDialog) {
            AddUserOrGroupDialog { showDialog = false }
        }
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        ChatApp(DpSize(800.dp, 600.dp), "token")
    }
}