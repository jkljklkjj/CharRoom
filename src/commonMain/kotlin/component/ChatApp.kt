package component

import core.ServerConfig
import core.Chat
import core.MsgType
import core.ApiService
import model.User
import model.Message
import model.GroupMessage
import model.messages
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
import core.buildGroupChatPayload
import core.buildCheckPayload
import core.parseProtoResponse

var lastMessageTime = 0L

fun sendMessage(user: User, messageText: String) {
    val currentTime = System.currentTimeMillis()
    if (currentTime - lastMessageTime >= 2000) {
        lastMessageTime = currentTime
        if (user.id > 0) {
            val localCopy = Message(
                senderId = user.id,
                message = messageText,
                sender = true,
                timestamp = currentTime,
                isSent = mutableStateOf(true)
            )
            messages += localCopy

            // build protobuf ChatMessage via platform builder
            val userIdInt = ServerConfig.id.toIntOrNull() ?: 0
            val payload = buildChatPayload(user.id.toString(), messageText, userIdInt, currentTime)

            Chat.send(payload, MsgType.CHAT, user.id.toString(), 1) { success, resp ->
                if (!(success && resp.isNotEmpty())) {
                    localCopy.isSent.value = false
                } else {
                    // parse proto response
                    val lastBytes = resp.last() as? ByteArray
                    if (lastBytes != null) {
                        val unwrap = parseProtoResponse(lastBytes)
                        if (!(unwrap.success)) {
                            localCopy.isSent.value = false
                        }
                    } else {
                        localCopy.isSent.value = false
                    }
                }
            }
        } else {
            val outbound = GroupMessage(
                groupId = user.id,
                senderId = Integer.valueOf(ServerConfig.id),
                text = messageText,
                senderName = "",
                timestamp = currentTime,
                isSent = mutableStateOf(true)
            )
            // build protobuf GroupChatMessage via platform builder
            val userIdInt = ServerConfig.id.toIntOrNull() ?: 0
            val payload = buildGroupChatPayload(user.id.toString(), messageText, userIdInt)

            Chat.send(payload, MsgType.GROUP_CHAT, user.id.toString(), 1) { success, resp ->
                if (!(success && resp.isNotEmpty())) {
                    outbound.isSent.value = false
                } else {
                    val lastBytes = resp.last() as? ByteArray
                    if (lastBytes != null) {
                        val unwrap = parseProtoResponse(lastBytes)
                        if (!(unwrap.success)) {
                            outbound.isSent.value = false
                        }
                    } else {
                        outbound.isSent.value = false
                    }
                }
            }
        }
    }
}

fun resendMessage(user: User, message: Message) {
    val payload = buildChatPayload(user.id.toString(), message.message, message.senderId, message.timestamp)
    Chat.send(payload, MsgType.CHAT, user.id.toString(), 1) { success, resp ->
        if (success && resp.isNotEmpty()) {
            val lastBytes = resp.last() as? ByteArray
            if (lastBytes != null) {
                val unwrap = parseProtoResponse(lastBytes)
                if (unwrap.success) message.isSent.value = true
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
                        selectedUser = user
                        if (user.id > 0) {
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
                                        selectedUser = selectedUser?.copy(
                                            username = if (online) {
                                                selectedUser!!.username.replace(" (offline)", "")
                                            } else if (!selectedUser!!.username.contains(" (offline)")) {
                                                selectedUser!!.username + " (offline)"
                                            } else selectedUser!!.username
                                        )
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
                    if (user.id > 0) {
                        val payload = buildCheckPayload(user.id.toString())
                        Chat.send(payload, MsgType.CHECK, user.id.toString(), 1) { success, resp ->
                            if (success && resp.isNotEmpty()) {
                                val lastBytes = resp.last() as? ByteArray
                                if (lastBytes != null) {
                                    val unwrap = parseProtoResponse(lastBytes)
                                    val dataStr = unwrap.dataJson ?: String(lastBytes, CharsetUtil.UTF_8)
                                    val map = Util.jsonToMap(dataStr)
                                    val online = map["online"] as? Boolean ?: false
                                    selectedUser = selectedUser?.copy(
                                        username = if (online) {
                                            selectedUser!!.username.replace(" (offline)", "")
                                        } else if (!selectedUser!!.username.contains(" (offline)")) {
                                            selectedUser!!.username + " (offline)"
                                        } else selectedUser!!.username
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                selectedUser?.let { u -> if (u.id < 0) GroupChatScreen(u) else ChatScreen(u) }
            }
        }

        IconButton(onClick = { showDialog = true }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            Icon(Icons.Default.Search, contentDescription = "Search")
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