package component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import core.loadImageBitmapFromUrl
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import core.Action
import core.ActionLogger
import core.ActionType
import core.ServerConfig
import model.User
import model.groupMessages
import model.messages
import model.updateList
import model.users

/**
 * 左侧用户和群组列表边栏
 */
@Composable
fun UserList(
    selectedUserId: Int? = null,
    onOpenSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenApplications: () -> Unit = {}, // 打开申请管理界面
    onUserClick: (User) -> Unit
) {
    // 群聊申请状态
    var hasPendingGroupApplications by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 定时检查是否有未处理的群聊申请
    LaunchedEffect(Unit) {
        scope.launch {
            while (true) {
                val requests = ApiService.fetchGroupRequests()
                hasPendingGroupApplications = requests.isNotEmpty()
                kotlinx.coroutines.delay(30000) // 每30秒检查一次
            }
        }
    }
    val userListState = users
    val onlineCount = userListState.count { it.id > 0 && !ServerConfig.isAgentAssistant(it.id) && it.online == true }

    // 首次进入时拉取列表，写回 users（在 updateList 内部）
    LaunchedEffect(Unit) {
        updateList()
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colors.surface.copy(alpha = 0.18f),
            shape = MaterialTheme.shapes.large,
            elevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(sidebarHeaderBrush(!MaterialTheme.colors.isLight))
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "聊天室",
                            style = MaterialTheme.typography.h6,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "在线 $onlineCount 人 · 共 ${userListState.size} 个会话",
                            style = MaterialTheme.typography.caption,
                            color = Color.White.copy(alpha = 0.86f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ElasticHeaderAction(
                            icon = Icons.Default.Search,
                            contentDescription = "搜索",
                            onClick = onOpenSearch
                        )
                        // 群聊申请入口，有未处理申请时显示红点
                        Box {
                            ElasticHeaderAction(
                                icon = Icons.Default.PersonAdd,
                                contentDescription = "申请管理",
                                onClick = onOpenApplications
                            )
                            if (hasPendingGroupApplications) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .align(Alignment.TopEnd)
                                        .background(Color(0xFFF44336), CircleShape)
                                )
                            }
                        }
                        ElasticHeaderAction(
                            icon = Icons.Default.Settings,
                            contentDescription = "设置",
                            onClick = onOpenSettings
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(
                items = userListState,
                key = { it.id }
            ) { user ->
                val displayName = buildDisplayName(user)
                val subtitle = buildSubtitle(user)
                val selected = selectedUserId == user.id
                val cardColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colors.primary.copy(alpha = if (MaterialTheme.colors.isLight) 0.16f else 0.34f)
                    } else {
                        MaterialTheme.colors.surface.copy(alpha = if (MaterialTheme.colors.isLight) 0.78f else 0.6f)
                    }
                )
                val borderColor = if (selected) {
                    MaterialTheme.colors.secondary.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colors.onSurface.copy(alpha = 0.08f)
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 4.dp)
                        .clickable {
                            try {
                                ActionLogger.log(
                                    Action(
                                        type = ActionType.OPEN_CHAT,
                                        targetId = user.id.toString(),
                                        metadata = mapOf("username" to user.username)
                                    )
                                )
                            } catch (_: Exception) {
                            }
                            onUserClick(user)
                        },
                    color = cardColor,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, borderColor),
                    elevation = if (selected) 4.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showOnlineDot(user)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (user.online == true) Color(0xFF2E7D32) else Color(0xFF9E9E9E),
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        // Avatar image (lazy load)
                        val avatarUrl = user.avatarUrl
                        var avatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
                        LaunchedEffect(avatarUrl, user.avatarKey) {
                            if (!avatarUrl.isNullOrBlank()) {
                                avatarBitmap = loadImageBitmapFromUrl(avatarUrl, user.avatarKey)
                            } else {
                                avatarBitmap = null
                            }
                        }

                        if (avatarBitmap != null) {
                            Image(
                                bitmap = avatarBitmap!!,
                                contentDescription = "avatar",
                                modifier = Modifier.size(40.dp).clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(40.dp).background(
                                    brush = sidebarHeaderBrush(!MaterialTheme.colors.isLight),
                                    shape = CircleShape
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = user.username.firstOrNull()?.toString() ?: "U", color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.subtitle1,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        val timeText = buildLastMessageTime(user)
                        if (timeText.isNotBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ElasticHeaderAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberElasticScale(interactionSource, pressedScale = 0.86f)

    Surface(
        modifier = Modifier
            .size(34.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        color = Color.White.copy(alpha = 0.2f),
        shape = CircleShape,
        elevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = Color.White)
        }
    }
}

private fun buildDisplayName(user: User): String {
    if (user.id < 0 || ServerConfig.isAgentAssistant(user.id)) {
        return user.username
    }
    return when (user.online) {
        true -> "${user.username} · 在线"
        false -> "${user.username} · 离线"
        null -> user.username
    }
}

private fun showOnlineDot(user: User): Boolean {
    return user.id > 0 && !ServerConfig.isAgentAssistant(user.id)
}

private fun buildSubtitle(user: User): String {
    if (ServerConfig.isAgentAssistant(user.id)) {
        return "智能灵感助手，来点有趣的话题"
    }

    if (user.id < 0) {
        val groupId = -user.id
        val last = groupMessages.lastOrNull { it.groupId == groupId }
        return if (last == null) {
            "暂无群消息"
        } else {
            "${last.senderName}: ${last.text.take(24)}"
        }
    }

    val last = messages.lastOrNull { it.senderId == user.id }
    return if (last == null) {
        if (user.online == true) "在线" else "暂无消息"
    } else {
        if (last.sender) "我: ${last.message.take(24)}" else "${user.username}: ${last.message.take(24)}"
    }
}

private fun buildLastMessageTime(user: User): String {
    val timestamp = if (user.id < 0) {
        val groupId = -user.id
        groupMessages.lastOrNull { it.groupId == groupId }?.timestamp
    } else {
        messages.lastOrNull { it.senderId == user.id }?.timestamp
    }

    return timestamp?.let { formatTime(it) } ?: ""
}

private fun formatTime(timestamp: Long): String {
    return try {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        sdf.format(java.util.Date(timestamp))
    } catch (_: Exception) {
        ""
    }
}
