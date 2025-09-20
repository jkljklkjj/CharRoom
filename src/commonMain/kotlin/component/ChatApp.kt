package component

import GroupMessage
import Message
import User
import ServerConfig.Token
import ServerConfig.id
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
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.netty.util.CharsetUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import messages

var lastMessageTime = 0L

// 仅保留消息发送相关逻辑
fun sendMessage(user: User, messageText: String) {
    val currentTime = System.currentTimeMillis()
    if (currentTime - lastMessageTime >= 2000) {
        lastMessageTime = currentTime
        println("Sending message to ${user.username}: $messageText")
        if (user.id > 0) {
            val outbound = Message(
                id = Integer.valueOf(id),
                text = messageText,
                sender = true,
                timestamp = currentTime,
                isSent = mutableStateOf(true)
            )
            val localCopy = Message(
                id = user.id,
                text = messageText,
                sender = true,
                timestamp = currentTime,
                isSent = mutableStateOf(true)
            )
            messages += localCopy
            val json = jacksonObjectMapper().writeValueAsString(outbound)
            Chat.send(json, "chat", user.id.toString(), 1) { success, resp ->
                if (!(success && resp.last().status().code() == 200)) {
                    localCopy.isSent.value = false
                }
            }
        } else {
            val outbound = GroupMessage(
                groupId = user.id,
                sender = Integer.valueOf(id),
                text = messageText,
                senderName = "",
                timestamp = currentTime,
                isSent = mutableStateOf(true)
            )
            val json = jacksonObjectMapper().writeValueAsString(outbound)
            Chat.send(json, "groupChat", user.id.toString(), 1) { success, resp ->
                if (!(success && resp.last().status().code() == 200)) {
                    outbound.isSent.value = false
                }
            }
        }
    } else {
        println("You can only send a message every 2 seconds.")
    }
}

fun resendMessage(user: User, message: Message) {
    val json = jacksonObjectMapper().writeValueAsString(message)
    Chat.send(json, "chat", user.id.toString(), 1) { success, resp ->
        if (success && resp.last().status().code() == 200) {
            message.isSent.value = true
        }
    }
}

fun resendMessage(user: User, groupMessage: GroupMessage) {
    val json = jacksonObjectMapper().writeValueAsString(groupMessage)
    Chat.send(json, "groupChat", user.id.toString(), 1) { success, resp ->
        if (success && resp.last().status().code() == 200) {
            groupMessage.isSent.value = true
        }
    }
}

@Composable
fun ChatApp(windowSize: DpSize, token: String) {
    Token = token
    var selectedUser by remember { mutableStateOf<User?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        CoroutineScope(Dispatchers.IO).launch { Chat.start() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (windowSize.width > windowSize.height) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    UserList { user ->
                        selectedUser = user
                        if (user.id > 0) {
                            Chat.send("", "check", user.id.toString(), 1) { success, resp ->
                                if (success) {
                                    val map = Util.jsonToMap(resp.last().content().toString(CharsetUtil.UTF_8))
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
                Box(Modifier.weight(2f)) {
                    selectedUser?.let { u ->
                        if (u.id < 0) groupChatScreen(u) else ChatScreen(u)
                    }
                }
            }
        } else {
            if (selectedUser == null) {
                UserList { user ->
                    selectedUser = user
                    if (user.id > 0) {
                        Chat.send("", "check", user.id.toString(), 1) { success, resp ->
                            if (success) {
                                val map = Util.jsonToMap(resp.last().content().toString(CharsetUtil.UTF_8))
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
            } else {
                selectedUser?.let { u -> if (u.id < 0) groupChatScreen(u) else ChatScreen(u) }
            }
        }

        IconButton(onClick = { showDialog = true }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            Icon(Icons.Default.Search, contentDescription = "Search")
        }
        if (showDialog) {
            addUserOrGroupDialog { showDialog = false }
        }
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        ChatApp(DpSize(800.dp, 600.dp), "token")
    }
}