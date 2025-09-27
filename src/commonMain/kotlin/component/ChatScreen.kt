package component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.Icon
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import model.User
import model.messages

/**
 * 好友聊天界面
 */
@Composable
fun ChatScreen(user: User) {
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
                                        .clickable { resendMessage(user, message) }
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

        LaunchedEffect(userMessages.size) {
            if (userMessages.isNotEmpty()) {
                listState.animateScrollToItem(userMessages.size - 1)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier
                    .weight(1f)
                    .onKeyEvent { event ->
                        if (event.key == Key.Enter && event.isShiftPressed && !isSending) {
                            if (messageText.isNotBlank()) {
                                isSending = true
                                sendMessage(user, messageText)
                                messageText = ""
                                isSending = false
                            }
                            true // 消费事件
                        } else {
                            false // 不消费事件
                        }
                    },
                label = { Text("Type a message") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (!isSending && messageText.isNotBlank()) {
                    isSending = true
                    sendMessage(user, messageText)
                    messageText = ""
                    isSending = false
                }
            }) { Text("Send") }
        }
    }
}
