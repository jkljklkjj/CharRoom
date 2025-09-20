import ServerConfig.Token
import ServerConfig.id
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.Icon
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.netty.util.CharsetUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

var lastMessageTime = 0L

/**
 * 好友侧边栏子窗口
 */
@Composable
fun userList(onUserClick: (User) -> Unit) {
    var userList by remember { mutableStateOf(listOf<User>()) }

    // 加载时拉取好友和群聊
    LaunchedEffect(Unit) {
        userList = updateList(Token)
    }
    println("User list: $userList")
    Column {
        LazyColumn {
            items(userList.size) { index ->
                val user = userList[index]
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clickable {
                            onUserClick(user)
                        }
                ) {
                    Text(text = user.username, style = MaterialTheme.typography.h6)
                }
            }
        }
        // 测试按钮：新增好友
        Button(onClick = {
            // 添加一条测试消息
            val newMessage = Message(
                id = 1, // 假设是用户ID为1的消息
                text = "This is a test message",
                sender = true, // 表示是发送者
                timestamp = System.currentTimeMillis(),
                isSent = mutableStateOf(true)
            )
            messages += newMessage
            println("测试消息已添加: $newMessage")
        }) {
            Text("Add Test Message")
        }
    }
}

/**
 * 添加用户或群组对话子窗口
 */
@Composable
fun addUserOrGroupDialog(onDismiss: () -> Unit) {
    var account by remember { mutableStateOf("") }
    var isUser by remember { mutableStateOf(true) }
    var responseMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add User or Group") },
        text = {
            Column {
                TextField(
                    value = account,
                    onValueChange = { account = it },
                    label = { Text("Account") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isUser,
                        onClick = { isUser = true }
                    )
                    Text("User")
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(
                        selected = !isUser,
                        onClick = { isUser = false }
                    )
                    Text("Group")
                }
                responseMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = if (it == "添加成功") Color.Green else Color.Red
                    )
                }
            }
        },
        confirmButton = {
            // 添加用户或群组的按钮
            Button(onClick = {
                println("正在添加。。。${account}")
                val payload = if (isUser) {
                    mapOf("friendId" to account)
                } else {
                    mapOf("groupId" to account)
                }
                val requestBody = jacksonObjectMapper().writeValueAsString(payload)
                val uri = if (isUser) {
                    "http://${ServerConfig.SERVER_IP}:${ServerConfig.SPRING_SERVER_PORT}/friend/add"
                } else {
                    "http://${ServerConfig.SERVER_IP}:${ServerConfig.SPRING_SERVER_PORT}/user/addgroup"
                }
                val client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build()

                val request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .header("Authorization", "Bearer $Token")
                    .timeout(Duration.ofSeconds(10))
                    .build()

                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                responseMessage = when (response.body()) {
                    "true" -> "添加成功"
                    "false" -> "添加失败"
                    else -> response.body()
                }
                if (responseMessage == "添加成功") {
                    val newRequest = if (isUser) {
                        HttpRequest.newBuilder()
                            .uri(URI.create("http://${ServerConfig.SERVER_IP}:${ServerConfig.SPRING_SERVER_PORT}/user/get?id=$account"))
                            .header("Authorization", "Bearer $Token")
                            .header("Content-Type", "application/json")
                            .build()
                    } else {
                        HttpRequest.newBuilder()
                            .uri(URI.create("http://${ServerConfig.SERVER_IP}:${ServerConfig.SPRING_SERVER_PORT}/group/getDetail?id=$account"))
                            .header("Authorization", "Bearer $Token")
                            .header("Content-Type", "application/json")
                            .build()
                    }

                    val detail = client.send(newRequest, HttpResponse.BodyHandlers.ofString())
                    println(detail.body())
                    val json = Json { ignoreUnknownKeys = true }
                    val user = json.decodeFromString<User>(detail.body())
                    val isuser = if (isUser) 1 else -1
                    val updatedUser = user.copy(id = isuser * user.id)
                    users += updatedUser
                }
            }) {
                Text("Add")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * 群组聊天界面
 */
@Composable
fun groupChatScreen(group: User) {
    var messageText by remember { mutableStateOf("") }
    val filteredGroupMessages = groupMessages.filter { it.groupId == -group.id }
    var isSending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = group.username, style = MaterialTheme.typography.h4)
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState
        ) {
            items(filteredGroupMessages.size) { index ->
                val message = filteredGroupMessages[index]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.sender == Integer.valueOf(ServerConfig.id)) Arrangement.End else Arrangement.Start
                ) {
                    if (message.sender != Integer.valueOf(ServerConfig.id)) {
                        Text(
                            text = message.senderName,
                            style = MaterialTheme.typography.body1,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (message.sender == Integer.valueOf(ServerConfig.id) && !message.isSent.value) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Resend",
                                tint = Color.Red,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable {
                                        resendMessage(User(-message.groupId, ""), message)
                                    }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.body1,
                            modifier = Modifier
                                .background(if (message.sender == Integer.valueOf(ServerConfig.id)) Color(0xFF1E88E5) else Color.LightGray)
                                .padding(8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // 自动滚动到底部
        LaunchedEffect(filteredGroupMessages.size) {
            listState.animateScrollToItem(filteredGroupMessages.size - 1)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier.weight(1f),
                label = { Text("Type a message") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (!isSending) {
                    isSending = true
                    sendMessage(group, messageText)
                    messageText = ""
                    isSending = false
                }
            }) {
                Text("Send")
            }
        }
    }
}

/**
 * 聊天信息界面
 * 和特有的用户
 */
@Composable
fun chatScreen(user: User) {
    var messageText by remember { mutableStateOf("") }
    val userMessages = messages.filter { it.id == user.id }
    var isSending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Chat with ${user.username}", style = MaterialTheme.typography.h4)
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState
        ) {
            items(userMessages.size) { index ->
                val message = userMessages[index]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.sender) Arrangement.End else Arrangement.Start
                ) {
                    if (!message.sender) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.body1,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!message.isSent.value) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Resend",
                                    tint = Color.Red,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable {
                                            resendMessage(user, message)
                                        }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.body1,
                                modifier = Modifier
                                    .background(if (message.sender) Color(0xFF1E88E5) else Color.LightGray)
                                    .padding(8.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // 自动滚动到底部
        LaunchedEffect(userMessages.size) {
            listState.animateScrollToItem(userMessages.size - 1)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier.weight(1f).onKeyEvent { event: KeyEvent ->
                    if (event.key == Key.Enter && event.isShiftPressed && !isSending) {
                        isSending = true
                        sendMessage(user, messageText)
                        messageText = ""
                        isSending = false
                        true
                    } else {
                        false
                    }
                },
                label = { Text("Type a message") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (!isSending) {
                    isSending = true
                    sendMessage(user, messageText)
                    messageText = ""
                    isSending = false
                }
            }) {
                Text("Send")
            }
        }
    }
}

fun sendMessage(user: User, messageText: String) {
    val currentTime = System.currentTimeMillis()
    if (currentTime - lastMessageTime >= 2000) {
        lastMessageTime = currentTime
        println("Sending message to ${user.username}: $messageText")

        if (user.id > 0) {
            val newSendMessage = Message(
                id = Integer.valueOf(id),
                text = messageText,
                sender = true,
                timestamp = currentTime,
                isSent = mutableStateOf(true)
            )
            val newMessage = Message(
                id = user.id,
                text = messageText,
                sender = true,
                timestamp = currentTime,
                isSent = mutableStateOf(true)
            )
            messages += newMessage
            val messageJson = jacksonObjectMapper().writeValueAsString(newSendMessage)
            Chat.send(messageJson, "chat", user.id.toString(), 1) { success, response ->
                if (success && response[response.size - 1].status().code() == 200) {
                    println("Message sent successfully")
                } else {
                    println("Error: $response")
                    newMessage.isSent.value = false
                }
            }
        } else {
            // 群组消息
            val newSendMessage = GroupMessage(
                groupId = user.id,
                sender = Integer.valueOf(id),
                text = messageText,
                senderName = "",// TODO 登录时记录自己的用户名
                timestamp = currentTime,
                isSent = mutableStateOf(true)
            )
            val messageJson = jacksonObjectMapper().writeValueAsString(newSendMessage)
            Chat.send(messageJson, "groupChat", user.id.toString(), 1) { success, response ->
                if (success && response[response.size - 1].status().code() == 200) {
                    println("Message sent successfully")

                } else {
                    println("Error: $response")
                    newSendMessage.isSent.value = false
                }
            }
        }
    } else {
        println("You can only send a message every 2 seconds.")
    }
}

fun resendMessage(user: User, message: Message) {
    println("Resending message: ${message.text}")
    val messageJson = jacksonObjectMapper().writeValueAsString(message)

    if (user.id > 0) {
        Chat.send(messageJson, "chat", user.id.toString(), 1) { success, response ->
            if (success && response[response.size - 1].status().code() == 200) {
                println("Message resent successfully")
                message.isSent.value = true
            } else {
                println("Error: $response")
            }
        }
    }
}

fun resendMessage(user: User, groupMessage: GroupMessage) {
    println("Resending group message: ${groupMessage.text}")
    val messageJson = jacksonObjectMapper().writeValueAsString(groupMessage)

    Chat.send(messageJson, "groupChat", user.id.toString(), 1) { success, response ->
        if (success && response[response.size - 1].status().code() == 200) {
            println("Group message resent successfully")
            groupMessage.isSent.value = true
        } else {
            println("Error: $response")
        }
    }
}
/**
 * 聊天应用
 */
@Composable
fun chatApp(windowSize: DpSize, token: String) {
    Token = token
    var selectedUser by remember { mutableStateOf<User?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            Chat.start()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (windowSize.width > windowSize.height) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    userList(onUserClick = { user ->
                        selectedUser = user
                        if (user.id > 0) {
                            Chat.send("chat", "check", user.id.toString(), 1) { success, response ->
                                if (!success) {
                                    println("Error: $response")
                                } else {
                                    val res = Util.jsonToMap(response[response.size - 1].content().toString(CharsetUtil.UTF_8))
                                    val isOnline = res["online"] as Boolean
                                    selectedUser = selectedUser?.copy(
                                        username = if (isOnline) {
                                            selectedUser!!.username.replace(" (offline)", "")
                                        } else {
                                            if (!selectedUser!!.username.contains(" (offline)")) {
                                                selectedUser!!.username + " (offline)"
                                            } else {
                                                selectedUser!!.username
                                            }
                                        }
                                    )
                                }
                            }
                        } else {
                            // TODO
                            println("Group chat")
                        }
                    })
                }
                Box(modifier = Modifier.weight(2f)) {
                    selectedUser?.let { user ->
                        if (user.id < 0) {
                            groupChatScreen(group = user)
                        } else {
                            chatScreen(user = user)
                        }
                    }
                }
            }
        } else {
            if (selectedUser == null) {
                userList(onUserClick = { user ->
                    selectedUser = user
                    if (user.id > 0) {
                        Chat.send("chat", "check", user.id.toString(), 1) { success, response ->
                            if (success) {
                                val res = Util.jsonToMap(response[response.size - 1].content().toString(CharsetUtil.UTF_8))
                                val isOnline = res["online"] as Boolean
                                selectedUser = selectedUser?.copy(
                                    username = if (isOnline) {
                                        selectedUser!!.username.replace(" (offline)", "")
                                    } else {
                                        if (!selectedUser!!.username.contains(" (offline)")) {
                                            selectedUser!!.username + " (offline)"
                                        } else {
                                            selectedUser!!.username
                                        }
                                    }
                                )
                            } else {
                                println("Error: $response")
                            }
                        }
                    }
                })
            } else {
                selectedUser?.let { user ->
                    if (user.id < 0) {
                        groupChatScreen(group = user)
                    } else {
                        chatScreen(user = user)
                    }
                }
            }
        }

        IconButton(
            onClick = { showDialog = true },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = "Search")
        }

        if (showDialog) {
            addUserOrGroupDialog(onDismiss = { showDialog = false })
        }
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        val windowSize = DpSize(800.dp, 600.dp)
        chatApp(windowSize, "token")
    }
}