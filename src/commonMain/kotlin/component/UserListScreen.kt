package component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import core.ServerConfig
import model.User
import model.updateList
import model.users
import core.Action
import core.ActionType
import core.ActionLogger

/**
 * 左侧用户和群组列表边栏
 */
@Composable
fun UserList(onUserClick: (User) -> Unit) {
    val userListState = users

    // 首次进入时拉取列表，写回 users（在 updateList 内部）
    LaunchedEffect(Unit) {
        updateList()
    }

    Column {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(userListState.size) { index ->
                val user = userListState[index]
                val displayName = buildDisplayName(user)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clickable {
                            // log action
                            try {
                                ActionLogger.log(Action(type = ActionType.OPEN_CHAT, targetId = user.id.toString(), metadata = mapOf("username" to user.username)))
                            } catch (_: Exception) {
                            }
                            onUserClick(user)
                        }
                ) {
                    Text(text = displayName, style = MaterialTheme.typography.h6)
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
