package com.chatlite.charroom

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import core.loadImageBitmapFromUrl

@Composable
fun UserListScreen(
    users: List<LocalUser>,
    loading: Boolean,
    error: String,
    onUserClick: (LocalUser) -> Unit,
    onUserDetailClick: (LocalUser) -> Unit,
    onProfileClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ChatUi.ScreenTopBar(
            title = "好友与群聊",
            actions = {
                IconButton(onClick = onProfileClick) {
                    Icon(imageVector = Icons.Default.Person, contentDescription = "个人信息")
                }
            }
        )

        when {
            loading -> ChatUi.LoadingState()
            error.isNotBlank() -> ChatUi.ErrorState(error)
            users.isEmpty() -> ChatUi.EmptyState("暂无好友或群聊")
            else -> UserListContent(users = users, onUserClick = onUserClick, onUserDetailClick = onUserDetailClick)
        }
    }
}

@Composable
fun UserListContent(
    users: List<LocalUser>,
    onUserClick: (LocalUser) -> Unit,
    onUserDetailClick: (LocalUser) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        items(users) { user ->
            UserListItem(
                user = user,
                onClick = { onUserClick(user) },
                onDetailClick = { onUserDetailClick(user) }
            )
        }
    }
}

@Composable
fun UserListItem(user: LocalUser, onClick: () -> Unit, onDetailClick: () -> Unit) {
    var avatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(user.avatarUrl) {
        val url = user.avatarUrl
        if (!url.isNullOrBlank()) {
            avatarBitmap = loadImageBitmapFromUrl(url, url)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        elevation = 4.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colors.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (avatarBitmap != null) {
                    Image(
                        bitmap = avatarBitmap!!,
                        contentDescription = "头像",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = user.username.firstOrNull()?.uppercase() ?: "U",
                        style = MaterialTheme.typography.subtitle1,
                        color = MaterialTheme.colors.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = user.username, style = MaterialTheme.typography.subtitle1)
                Text(
                    text = if (user.online) "在线" else "离线",
                    style = MaterialTheme.typography.caption
                )
                user.signature?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.body2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            IconButton(onClick = onDetailClick) {
                Icon(imageVector = Icons.Default.Info, contentDescription = "查看详情")
            }

            Text(text = if (user.isGroup) "群聊" else "进入", color = MaterialTheme.colors.secondary)
        }
    }
}
