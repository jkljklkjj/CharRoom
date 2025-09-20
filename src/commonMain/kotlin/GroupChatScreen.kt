import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
                                    .clickable { resendMessage(User(-message.groupId, ""), message) }
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

        LaunchedEffect(filteredGroupMessages.size) {
            if (filteredGroupMessages.isNotEmpty()) {
                listState.animateScrollToItem(filteredGroupMessages.size - 1)
            }
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
            }) { Text("Send") }
        }
    }
}

