package com.chatlite.charroom

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import component.EmojiPickerPanel
import component.FilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatSession(
    private val user: LocalUser,
    private val onSendNetwork: (String) -> Unit = {}
) {
    val messages: SnapshotStateList<ChatMessage> = mutableStateListOf(
//        ChatMessage(
//            senderId = user.id,
//            receiverId = user.id,
//            content = "你好，我是${user.username}，你想了解什么？",
//            isMe = false,
//            timestamp = System.currentTimeMillis() - 600_000
//        ),
//        ChatMessage(
//            senderId = 0,
//            content = "你好！我想了解这个聊天应用的使用方式。",
//            isMe = true,
//            timestamp = System.currentTimeMillis() - 500_000
//        ),
//        ChatMessage(
//            senderId = user.id,
//            content = "好的，我可以帮你解答。",
//            isMe = false,
//            timestamp = System.currentTimeMillis() - 420_000
//        )
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

@OptIn(FlowPreview::class)
@Composable
fun ChatScreen(user: LocalUser, appState: ChatAppState, onBack: () -> Unit, onSend: (String) -> Unit) {
    val context = LocalContext.current
    val chatSession = remember(user, onSend) { ChatSession(user, onSend) }

    LaunchedEffect(user.id, appState.currentUserId) {
        val saved = withContext(Dispatchers.IO) {
            AndroidLocalChatHistoryStore.loadChatHistory(context, appState.currentUserId, user.id)
        }
        if (saved.isNotEmpty()) {
            chatSession.messages.clear()
            chatSession.messages.addAll(saved)
        }
    }

    LaunchedEffect(chatSession.messages) {
        snapshotFlow { chatSession.messages.toList() }
            .debounce(500)
            .collect { snapshot ->
                withContext(Dispatchers.IO) {
                    AndroidLocalChatHistoryStore.saveChatHistory(context, appState.currentUserId, user.id, snapshot)
                }
            }
    }

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

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        ChatUi.ScreenTopBar(title = user.username, navigationIcon = { ChatUi.BackButton(onBack) })
        ChatBody(userId = user.id, messages = chatSession.messages, onSend = { chatSession.sendMessage(it) })
    }
}

@Composable
fun ChatBody(userId: Int, messages: SnapshotStateList<ChatMessage>, onSend: (String) -> Unit) {
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var showEmojiPanel by remember { mutableStateOf(false) }

    fun onEmojiSelected(emoji: String) {
        input += emoji
        showEmojiPanel = false
    }

    fun pickFile() {
        FilePicker.pickFile { bytes, fileName, fileSize ->
            onSend("[文件] $fileName")
        }
    }

    fun pickImage() {
        FilePicker.pickImage { bytes, fileName ->
            onSend("[图片] $fileName")
        }
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
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
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp),
                placeholder = { Text("发送一条消息...") },
                maxLines = 4
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    onSend(input)
                    input = ""
                },
                enabled = input.isNotBlank(),
                modifier = Modifier.height(44.dp)
            ) {
                Text("发送")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { showEmojiPanel = !showEmojiPanel },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEmotions,
                    contentDescription = "表情",
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = { pickFile() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "发送文件",
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }

        if (showEmojiPanel) {
            EmojiPickerPanel(
                onEmojiSelected = ::onEmojiSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(horizontal = 8.dp)
            )
        }
    }

    LaunchedEffect(userId, messages.size) {
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
