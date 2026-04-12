package component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.Icon
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import model.User
import model.messages

/**
 * 好友聊天界面
 */
@Composable
fun ChatScreen(user: User) {
    var messageText by remember { mutableStateOf("") }
    val userMessages = messages.filter { it.senderId == user.id }
    var isSending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun submitMessage() {
        val text = messageText.trim()
        if (text.isEmpty() || isSending) {
            return
        }
        isSending = true
        messageText = ""
        sendMessage(user, text) {
            scope.launch {
                isSending = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colors.surface,
            elevation = 2.dp,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = user.username,
                    style = MaterialTheme.typography.h6,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (user.online == true) "在线" else if (user.online == false) "离线" else "状态未知",
                    style = MaterialTheme.typography.caption,
                    color = if (user.online == true) Color(0xFF2E7D32) else MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState
        ) {
            items(userMessages.size) { index ->
                val message = userMessages[index]
                val bubbleColor = if (message.sender) Color(0xFF1E88E5) else Color(0xFFF0F0F0)
                val contentColor = if (message.sender) Color.White else MaterialTheme.colors.onSurface
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.sender) Arrangement.End else Arrangement.Start
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        if (message.sender && !message.isSent.value) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "重发",
                                tint = Color(0xFFD32F2F),
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { resendMessage(user, message) }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        Surface(
                            color = bubbleColor,
                            shape = RoundedCornerShape(14.dp),
                            elevation = 1.dp,
                            modifier = Modifier.widthIn(max = 420.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(
                                    text = message.message,
                                    style = MaterialTheme.typography.body1,
                                    color = contentColor
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Text(
                                        text = formatTime(message.timestamp),
                                        style = MaterialTheme.typography.caption,
                                        color = if (message.sender) Color.White.copy(alpha = 0.78f) else MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }
                    }
                }

                if (message.sender && !message.isSent.value) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            text = "发送失败，可点击图标重试",
                            style = MaterialTheme.typography.caption,
                            color = Color(0xFFD32F2F)
                        )
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
                        if (event.type == KeyEventType.KeyUp && event.key == Key.Enter && !event.isShiftPressed && !isSending) {
                            submitMessage()
                            true // 消费事件
                        } else {
                            false // 不消费事件
                        }
                    },
                label = { Text("输入消息") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { submitMessage() },
                enabled = !isSending && messageText.isNotBlank()
            ) { Text(if (isSending) "发送中..." else "发送") }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    return try {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        sdf.format(java.util.Date(timestamp))
    } catch (_: Exception) {
        ""
    }
}
