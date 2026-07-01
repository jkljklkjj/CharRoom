package component.user

import component.ChatScreen
import component.chat.GroupChatScreen
import component.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import core.ApiService
import core.loadImageBitmapWithCache
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chatlite.i18n.LocalStrings
import com.chatlite.i18n.currentStrings
import core.Action
import core.ActionLogger
import core.ActionType
import core.Chat
import core.GlobalApiService
import core.MsgType
import core.ServerConfig
import core.buildCheckPayload
import model.User
import presentation.viewmodel.ChatViewModel
import androidx.compose.runtime.collectAsState

/**
 * Left sidebar showing user and group list
 */
@OptIn(ExperimentalFoundationApi::class)

@Composable
fun UserList(
    chatViewModel: ChatViewModel,
    selectedUserId: Int? = null,
    onOpenSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenApplications: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onUserClick: (User) -> Unit,
    onUserLongClick: ((User) -> Unit)? = null,
    refreshTrigger: Long = 0L // external trigger to refresh, value changes refresh application list
) {
    // Whether there are pending applications (friend or group)
    var hasPendingApplications by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Current user info and avatar
    var currentUser by remember { mutableStateOf<User?>(null) }
    var currentUserAvatar by remember { mutableStateOf<ImageBitmap?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val s = LocalStrings.current
    val userListState by chatViewModel.usersFlow.collectAsState()
    val conversationStates by chatViewModel.conversationStatesFlow.collectAsState()
    val allMessages by chatViewModel.messagesFlow.collectAsState()
    val allGroupMessages by chatViewModel.groupMessagesFlow.collectAsState()
    val sortedUsers by remember(searchQuery, userListState, conversationStates) {
        derivedStateOf {
            val indexById = userListState.withIndex().associate { it.value.id to it.index }
            userListState.sortedWith(
                compareByDescending<User> { conversationStates[it.id]?.lastIncomingMessageTime ?: 0L }
                    .thenByDescending { conversationStates[it.id]?.unreadCount ?: 0 }
                    .thenBy { indexById[it.id] ?: Int.MAX_VALUE }
            )
        }
    }
    val filteredUsers by remember(searchQuery, sortedUsers) {
        derivedStateOf {
            val keyword = searchQuery.trim().lowercase()
            if (keyword.isBlank()) return@derivedStateOf sortedUsers
            sortedUsers.filter { user ->
                val displayName = buildDisplayName(user).lowercase()
                val accountId = user.id.toString().lowercase()
                val extra = user.username + " " + user.email
                extra.lowercase().contains(keyword) || displayName.contains(keyword) || accountId.contains(keyword)
            }
        }
    }

    // Check for pending applications (only on explicit invocation)
    suspend fun refreshPendingApplications() {
        val groupRequests = ApiService.fetchGroupRequests()
        val friendRequests = ApiService.fetchFriendRequests()
        hasPendingApplications = groupRequests.isNotEmpty() || friendRequests.isNotEmpty()
    }

    // Check once on first launch, no auto-polling afterward
    LaunchedEffect(Unit) {
        scope.launch {
            refreshPendingApplications()
        }
    }

    // Execute when external trigger refreshes
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            scope.launch {
                refreshPendingApplications()
            }
        }
    }

    // Refresh once when application button is clicked
    fun handleOpenApplications() {
        scope.launch {
            refreshPendingApplications()
        }
        onOpenApplications()
    }

    val onlineCount = userListState.count { it.id > 0 && !ServerConfig.isAgentAssistant(it.id) && it.online == true }

    // On first entry, only load current user info and avatar; contact list loaded by ChatApp
    LaunchedEffect(Unit) {
        scope.launch {
            currentUser = GlobalApiService.getCurrentUserProfile()
            currentUser?.let { user ->
                user.avatarUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    currentUserAvatar = loadImageBitmapWithCache(url, user.avatarKey)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp, start = 8.dp, end = 8.dp).statusBarsPadding()) {
        // Sidebar header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.Transparent,
            shape = RoundedCornerShape(14.dp),
            elevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(sidebarHeaderBrush(!MaterialTheme.colors.isLight))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Current user avatar
                    Surface(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onOpenProfile),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (currentUserAvatar != null) {
                                Image(
                                    bitmap = currentUserAvatar!!,
                                    contentDescription = s["user.avatar"],
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = currentUser?.username?.firstOrNull()?.toString() ?: s["chat.me"],
                                    color = Color.White,
                                    style = MaterialTheme.typography.subtitle1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = s["contact.list.title"],
                            style = MaterialTheme.typography.subtitle1,
                            color = Color.White
                        )
                        Text(
                            text = s["contact.list.online.count"].format(onlineCount, userListState.size),
                            style = MaterialTheme.typography.caption,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ElasticHeaderAction(
                            icon = Icons.Default.Search,
                            contentDescription = s["contact.search.add"],
                            onClick = onOpenSearch
                        )
                        Box {
                            ElasticHeaderAction(
                                icon = Icons.Default.Notifications,
                                contentDescription = s["contact.applications"],
                                onClick = ::handleOpenApplications
                            )
                            if (hasPendingApplications) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .align(Alignment.TopEnd)
                                        .background(Color(0xFFFF4757), CircleShape)
                                )
                            }
                        }
                        ElasticHeaderAction(
                            icon = Icons.Default.Settings,
                            contentDescription = s["settings.title"],
                            onClick = onOpenSettings
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search field
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colors.surface.copy(alpha = if (MaterialTheme.colors.isLight) 0.6f else 0.15f),
            shape = RoundedCornerShape(12.dp),
            elevation = 0.dp
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(text = s["contact.search"], color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.textFieldColors(
                    backgroundColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colors.primary
                ),
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(
                items = filteredUsers,
                key = { it.id }
            ) { user ->
                // Staggered animation: each item has different delay
                val itemIndex = filteredUsers.indexOf(user)
                var itemVisible by remember(user.id) { mutableStateOf(false) }

                LaunchedEffect(user.id) {
                    kotlinx.coroutines.delay(itemIndex * 30L) // 30ms delay per item
                    itemVisible = true
                }

                AnimatedVisibility(
                    visible = itemVisible,
                    enter = fadeIn(
                        animationSpec = tween(200, easing = FastOutSlowInEasing)
                    ) + slideInHorizontally(
                        initialOffsetX = { -it / 4 }, // slide in from left
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                ) {
                val displayName = buildDisplayName(user)
                val subtitle = buildSubtitle(user, allMessages, allGroupMessages)
                val unreadCount = conversationStates[user.id]?.unreadCount ?: 0
                val selected = selectedUserId == user.id
                val cardColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colors.primary.copy(alpha = if (MaterialTheme.colors.isLight) 0.12f else 0.28f)
                    } else {
                        Color.Transparent
                    }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            println("[UserList DEBUG] Clicked user: ${user.id} - ${user.username}")
                            try {
                                ActionLogger.log(
                                    Action(
                                        type = ActionType.OPEN_CHAT,
                                        targetId = user.id.toString(),
                                        metadata = mapOf("username" to user.username)
                                    )
                                )
                            } catch (_: Exception) {}
                            if (Chat.isServerConnected && user.id > 0 && !ServerConfig.isAgentAssistant(user.id)) {
                                val checkPayload = buildCheckPayload(user.id.toString())
                                Chat.send(checkPayload, MsgType.CHECK, user.id.toString(), 1) { success, _ ->
                                    if (success) println("[UserList] Online status check sent: user=${user.id}")
                                }
                            }
                            onUserClick(user)
                        }
                        .background(cardColor, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Online dot
                        if (showOnlineDot(user)) {
                            val pulseScale = if (user.online == true) {
                                rememberPulseAnimation()
                            } else 1f
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                                    .background(
                                        color = if (user.online == true) Color(0xFF4CAF50) else Color(0xFF9AA5B1),
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }

                        // Avatar
                        val avatarUrl = user.avatarUrl
                        var avatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
                        LaunchedEffect(avatarUrl, user.avatarKey) {
                            if (!avatarUrl.isNullOrBlank()) {
                                avatarBitmap = loadImageBitmapWithCache(avatarUrl, user.avatarKey)
                            } else {
                                avatarBitmap = null
                            }
                        }

                        val avatarModifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .combinedClickable(
                                onClick = { onUserLongClick?.invoke(user) },
                                onLongClick = { onUserLongClick?.invoke(user) }
                            )

                        if (avatarBitmap != null) {
                            Image(bitmap = avatarBitmap!!, contentDescription = "avatar", modifier = avatarModifier)
                        } else {
                            Box(
                                modifier = avatarModifier.background(
                                    brush = sidebarHeaderBrush(!MaterialTheme.colors.isLight),
                                    shape = RoundedCornerShape(10.dp)
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = user.username.firstOrNull()?.toString() ?: "U", color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.subtitle2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        val timeText = buildLastMessageTime(user, allMessages, allGroupMessages)
                        if (timeText.isNotBlank() || unreadCount > 0) {
                            Column(horizontalAlignment = Alignment.End) {
                                if (timeText.isNotBlank()) {
                                    Text(
                                        text = timeText,
                                        style = MaterialTheme.typography.caption,
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.45f)
                                    )
                                }
                                if (unreadCount > 0) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFFFF4757), shape = RoundedCornerShape(999.dp))
                                            .padding(horizontal = 6.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                            color = Color.White,
                                            style = MaterialTheme.typography.overline,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                } // End AnimatedVisibility
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
        true -> "${user.username}${currentStrings["contact.online"]}"
        false -> "${user.username}${currentStrings["contact.offline"]}"
        null -> "${user.username}${currentStrings["contact.syncing"]}"
    }
}

private fun showOnlineDot(user: User): Boolean {
    return user.id > 0 && !ServerConfig.isAgentAssistant(user.id)
}

private fun buildSubtitle(
    user: User,
    messages: List<model.Message>,
    groupMessages: List<model.GroupMessage>
): String {
    if (ServerConfig.isAgentAssistant(user.id)) {
        return currentStrings["contact.ai.assistant"]
    }

    if (user.id < 0) {
        val groupId = -user.id
        val last = groupMessages.lastOrNull { it.groupId == groupId }
        return if (last == null) {
            currentStrings["contact.no.group.messages"]
        } else {
            "${last.senderName}: ${last.text.take(24)}"
        }
    }

    val last = messages.lastOrNull { it.senderId == user.id || it.receiverId == user.id }
    return if (last == null) {
        if (user.online == true) currentStrings["chat.status.online"] else currentStrings["contact.no.messages"]
    } else {
        if (last.sender) currentStrings["contact.me.prefix"].format(last.message.take(24)) else "${user.username}: ${last.message.take(24)}"
    }
}

private fun buildLastMessageTime(
    user: User,
    messages: List<model.Message>,
    groupMessages: List<model.GroupMessage>
): String {
    val timestamp = if (user.id < 0) {
        val groupId = -user.id
        groupMessages.lastOrNull { it.groupId == groupId }?.timestamp
    } else {
        messages.lastOrNull { it.senderId == user.id || it.receiverId == user.id }?.timestamp
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
