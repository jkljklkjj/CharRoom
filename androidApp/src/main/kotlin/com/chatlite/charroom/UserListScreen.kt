package com.chatlite.charroom

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun UserListScreen(
    users: List<LocalUser>,
    loading: Boolean,
    error: String,
    onUserClick: (LocalUser) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ChatUi.ScreenTopBar(title = "好友与群聊")

        when {
            loading -> ChatUi.LoadingState()
            error.isNotBlank() -> ChatUi.ErrorState(error)
            users.isEmpty() -> ChatUi.EmptyState("暂无好友或群聊")
            else -> UserListContent(users = users, onUserClick = onUserClick)
        }
    }
}

@Composable
fun UserListContent(users: List<LocalUser>, onUserClick: (LocalUser) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        items(users) { user ->
            UserListItem(user = user, onClick = { onUserClick(user) })
        }
    }
}

@Composable
fun UserListItem(user: LocalUser, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        elevation = 4.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = user.username, style = MaterialTheme.typography.subtitle1)
                Text(text = if (user.online) "在线" else "离线", style = MaterialTheme.typography.caption)
            }
            Text(text = "进入", color = MaterialTheme.colors.secondary)
        }
    }
}
