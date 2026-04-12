package component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import core.ServerConfig
import model.User
import model.groupMessages
import model.messages
import model.updateList
import model.users
import core.Action
import core.ActionType
import core.ActionLogger

/**
 * 左侧用户和群组列表边栏
 */
@Composable
fun UserList(
    selectedUserId: Int? = null,
    onOpenSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onUserClick: (User) -> Unit
) {
    val userListState = users

    // 首次进入时拉取列表，写回 users（在 updateList 内部）
    LaunchedEffect(Unit) {
        updateList()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "会话",
                style = MaterialTheme.typography.subtitle1,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onOpenSearch) {
                Icon(Icons.Default.Search, contentDescription = "搜索")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "设置")
            }
        }

        Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f))

        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 4.dp)) {
            items(userListState.size) { index ->
                val user = userListState[index]
                val displayName = buildDisplayName(user)
                val subtitle = buildSubtitle(user)
                val selected = selectedUserId == user.id

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clickable {
                            // log action
                            try {
                                ActionLogger.log(Action(type = ActionType.OPEN_CHAT, targetId = user.id.toString(), metadata = mapOf("username" to user.username)))
                            } catch (_: Exception) {
                            }
                            onUserClick(user)
                        },
                    color = if (selected) MaterialTheme.colors.primary.copy(alpha = 0.12f) else MaterialTheme.colors.surface,
                    shape = MaterialTheme.shapes.medium,
                    elevation = if (selected) 2.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
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
        return "智能问答助手，支持上下文对话"
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
