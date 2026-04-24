package component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import model.User
import model.messages
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import core.LocalChatHistoryStore
import core.ServerConfig
import core.loadImageBitmapWithCache
import viewmodel.chatViewModel

/**
 * 好友聊天界面
 */
@Composable
fun ChatScreen(user: User) {
    var messageText by remember { mutableStateOf("") }
    // 使用derivedStateOf优化消息过滤，只有messages变化或user变化时才重新计算
    val userMessages by remember(user.id) {
        derivedStateOf {
            messages.filter { it.senderId == user.id }
        }
    }
    var isSending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isDarkMode = !MaterialTheme.colors.isLight

    // 分页加载相关状态
    var currentPage by remember { mutableStateOf(1) } // 第0页已经在启动时加载
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMoreHistory by remember { mutableStateOf(true) }

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

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colors.surface.copy(alpha = 0.2f),
            shape = RoundedCornerShape(18.dp),
            elevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(chatHeaderBrush(isDarkMode))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (user.online == true) Color(0xFF4CAF50) else Color(0xFF9AA5B1))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = user.username,
                            style = MaterialTheme.typography.h6,
                            color = MaterialTheme.colors.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (user.online == true) "实时在线会话" else if (user.online == false) "离线留言模式" else "状态同步中",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onBackground.copy(alpha = 0.72f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            color = MaterialTheme.colors.surface.copy(alpha = if (isDarkMode) 0.3f else 0.6f),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.08f)),
            elevation = 0.dp
        ) {
            // 监听滚动位置，滚动到顶部时加载更多历史
            LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
                if (!isLoadingMore && hasMoreHistory && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                    isLoadingMore = true
                    val olderMessages = LocalChatHistoryStore.getPrivateMessagesPage(
                        accountId = ServerConfig.id,
                        userId = user.id,
                        page = currentPage,
                        pageSize = 50
                    )
                    if (olderMessages.isNotEmpty()) {
                        chatViewModel.prependMessages(olderMessages)
                        currentPage++
                    } else {
                        hasMoreHistory = false
                    }
                    isLoadingMore = false
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
                state = listState,
                reverseLayout = false // 正常顺序，最新消息在底部
            ) {
                // 加载更多提示
                if (isLoadingMore) {
                    item(key = "loading_indicator") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colors.primary
                            )
                        }
                    }
                } else if (!hasMoreHistory && userMessages.isNotEmpty()) {
                    item(key = "no_more_messages") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "没有更早的消息了",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = userMessages,
                    key = { _, message -> message.messageId }
                ) { index, message ->
                    var visible by remember(message.messageId) { mutableStateOf(false) }
                    LaunchedEffect(message.messageId) {
                        visible = true
                    }

                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn() + scaleIn(
                            initialScale = 0.9f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        val bubbleBorderColor = if (message.sender) {
                            Color.Transparent
                        } else {
                            MaterialTheme.colors.primary.copy(alpha = if (isDarkMode) 0.24f else 0.14f)
                        }
                        val bubbleTextColor = if (message.sender) {
                            MaterialTheme.colors.onPrimary
                        } else {
                            MaterialTheme.colors.onSurface
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (message.sender) Arrangement.End else Arrangement.Start
                        ) {
                                    Row(verticalAlignment = Alignment.Bottom) {
                                if (message.sender && !message.isSent.value) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "重发",
                                        tint = MaterialTheme.colors.secondary,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable { resendMessage(user, message) }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }

                                // If message is from the other user, show their avatar on the left
                                if (!message.sender) {
                                    var partnerAvatar by remember { mutableStateOf<ImageBitmap?>(null) }
                                    LaunchedEffect(user.avatarUrl, user.avatarKey) {
                                        partnerAvatar = if (!user.avatarUrl.isNullOrBlank()) loadImageBitmapWithCache(user.avatarUrl!!, user.avatarKey) else null
                                    }
                                    if (partnerAvatar != null) {
                                        Image(bitmap = partnerAvatar!!, contentDescription = "avatar", modifier = Modifier.size(32.dp).clip(CircleShape))
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .widthIn(max = 430.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(messageBubbleBrush(message.sender, isDarkMode))
                                        .border(1.dp, bubbleBorderColor, RoundedCornerShape(18.dp))
                                        .padding(horizontal = 11.dp, vertical = 9.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = message.message,
                                            style = MaterialTheme.typography.body1,
                                            color = bubbleTextColor
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Text(
                                                text = formatTime(message.timestamp),
                                                style = MaterialTheme.typography.caption,
                                                color = bubbleTextColor.copy(alpha = 0.74f),
                                                textAlign = TextAlign.End
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (message.sender && !message.isSent.value) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Text(
                                text = "发送失败，点击图标重试",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.error
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        LaunchedEffect(userMessages.size) {
            if (userMessages.isNotEmpty()) {
                listState.animateScrollToItem(userMessages.size - 1)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colors.surface.copy(alpha = if (isDarkMode) 0.36f else 0.8f),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.08f)),
            elevation = 0.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier
                        .weight(1f)
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyUp && event.key == Key.Enter && !event.isShiftPressed && !isSending) {
                                submitMessage()
                                true
                            } else {
                                false
                            }
                        },
                    placeholder = { Text("说点有趣的...", color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)) },
                    colors = TextFieldDefaults.textFieldColors(
                        backgroundColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                val sendInteraction = remember { MutableInteractionSource() }
                val sendScale = rememberElasticScale(sendInteraction, pressedScale = 0.9f)
                Button(
                    onClick = { submitMessage() },
                    enabled = !isSending && messageText.isNotBlank(),
                    interactionSource = sendInteraction,
                    modifier = Modifier
                        .height(42.dp)
                        .graphicsLayer {
                            scaleX = sendScale
                            scaleY = sendScale
                        },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary,
                        contentColor = MaterialTheme.colors.onPrimary,
                        disabledBackgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.45f)
                    )
                ) {
                    Text(if (isSending) "发送中..." else "发送")
                }
            }
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
