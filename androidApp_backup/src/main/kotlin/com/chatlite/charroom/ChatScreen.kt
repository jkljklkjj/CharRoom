package com.chatlite.charroom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class ChatSession(
    private val user: LocalUser,
    private val onSendNetwork: (String) -> Unit = {}
) {
    val messages: SnapshotStateList<ChatMessage> = mutableStateListOf(
        ChatMessage(
            senderId = user.id,
            receiverId = user.id,
            content = "你好，我是${user.username}，你想了解什么？",
            isMe = false,
            timestamp = System.currentTimeMillis() - 600_000
        ),
        ChatMessage(
            senderId = 0,
            content = "你好！我想了解这个聊天应用的使用方式。",
            isMe = true,
            timestamp = System.currentTimeMillis() - 500_000
        ),
        ChatMessage(
            senderId = user.id,
            content = "好的，我可以帮你解答。",
            isMe = false,
            timestamp = System.currentTimeMillis() - 420_000
        )
    )

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        messages.add(
            ChatMessage(
                senderId = 0,
                receiverId = user.id,
                content = trimmed,
                isMe = true,
                timestamp = System.currentTimeMillis()
            )
        )
        onSendNetwork(trimmed)
    }

    fun receiveMessage(message: ChatMessage) {
        if (message.messageId.isBlank()) {
            messages.add(message)
            return
        }
        val idx = messages.indexOfFirst { it.messageId == message.messageId }
        if (idx >= 0) {
            val target = messages[idx]
            messages[idx] = target.copy(content = target.content + message.content)
            return
        }
        messages.add(message)
    }
}

@Composable
fun ChatScreen(user: LocalUser, appState: ChatAppState, onBack: () -> Unit, onSend: (String) -> Unit) {
    val chatSession = remember(user, onSend) { ChatSession(user, onSend) }

    DisposableEffect(chatSession, user) {
        val receiver: (ChatMessage) -> Unit = { message ->
            val shouldReceive = if (user.id < 0) {
                message.receiverId == user.id && !message.isMe
            } else {
                message.senderId == user.id && !message.isMe
            }
            if (shouldReceive) {
                chatSession.receiveMessage(message)
            }
        }
        appState.registerChatReceiver(receiver)
        onDispose {
            appState.unregisterChatReceiver()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChatUi.ScreenTopBar(title = user.username, navigationIcon = { ChatUi.BackButton(onBack) })
        ChatBody(messages = chatSession.messages, onSend = { chatSession.sendMessage(it) })
    }
}

@Composable
fun ChatBody(messages: SnapshotStateList<ChatMessage>, onSend: (String) -> Unit) {
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.messageId }) { message ->
                ChatMessageItem(message = message)
            }
        }

        Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("发送一条消息...") },
                maxLines = 4
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    onSend(input)
                    input = ""
                },
                enabled = input.isNotBlank()
            ) {
                Text("发送")
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage) {
    val alignment = if (message.isMe) Alignment.End else Alignment.Start
    val bubbleColor = if (message.isMe) MaterialTheme.colors.primary else MaterialTheme.colors.surface
    val contentColor = if (message.isMe) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(color = bubbleColor, shape = MaterialTheme.shapes.medium)
                .padding(12.dp)
        ) {
            Text(text = message.content, color = contentColor)
        }
    }
}

data class ChatMessage(
    val senderId: Int,
    val receiverId: Int = 0,
    var content: String,
    val isMe: Boolean,
    val timestamp: Long,
    val messageId: String = "msg-${senderId}-${timestamp}-${content.hashCode()}"
)
