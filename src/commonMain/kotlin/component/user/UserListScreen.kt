package component.user

import component.*

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import core.ApiService
import core.loadImageBitmapWithCache
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.IconButton
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
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

// 用户预览信息（subtitle + lastMessageTime），用于缓存避免重复计算
private data class UserPreview(
    val subtitle: String,
    val lastMessageTime: String
)

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
    onOpenDiscovery: () -> Unit = {},
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
    // 收集消息用于构建 subtitle 和 lastMessageTime（仅在 UI 需要时计算）
    val allMessages by chatViewModel.messagesFlow.collectAsState()
    val allGroupMessages by chatViewModel.groupMessagesFlow.collectAsState()

    // 预计算每个用户的 subtitle 和 lastMessageTime，避免每次 recomposition 重复计算
    val userPreviews by remember(userListState, allMessages, allGroupMessages) {
        derivedStateOf {
            userListState.associateWith { user ->
                val subtitle = buildSubtitle(user, allMessages, allGroupMessages)
                val lastTime = buildLastMessageTime(user, allMessages, allGroupMessages)
                UserPreview(subtitle, lastTime)
            }
        }
    }

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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White
                        )
                        Text(
                            text = s["contact.list.online.count"].format(onlineCount, userListState.size),
                            style = MaterialTheme.typography.caption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ElasticHeaderAction(
                            icon = Icons.Default.PersonAdd,
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

        Surface(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpenDiscovery),
            color = MaterialTheme.colors.surface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = .08f))
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Explore, null, tint = MaterialTheme.colors.primary)
                Column(Modifier.weight(1f)) {
                    Text(s["social.discover"], style = MaterialTheme.typography.subtitle2)
                    Text(s["polish.discoverySubtitle"], style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = .6f))
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colors.onSurface.copy(alpha = .5f))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, s["polish.clearSearch"]) } }
                } else null,
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
            if (filteredUsers.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(s[if (searchQuery.isBlank()) "polish.emptyContacts" else "polish.noContacts"], color = MaterialTheme.colors.onSurface.copy(alpha = .65f))
                    TextButton(onClick = { if (searchQuery.isBlank()) onOpenDiscovery() else searchQuery = "" }) {
                        Text(s[if (searchQuery.isBlank()) "social.discover" else "polish.clearSearch"])
                    }
                }
            }
            items(
                items = filteredUsers,
                key = { it.id }
            ) { user ->
                val displayName = buildDisplayName(user)
                val preview = userPreviews[user]
                val subtitle = preview?.subtitle ?: ""
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
                            try {
                                ActionLogger.log(
                                    Action(
                                        type = ActionType.OPEN_CHAT,
                                        targetId = user.id.toString(),
                                        metadata = mapOf("username" to user.username)
                                    )
                                )
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                            }
                            if (Chat.isServerConnected && user.id > 0 && !ServerConfig.isAgentAssistant(user.id)) {
                                val checkPayload = buildCheckPayload(user.id.toString())
                                Chat.send(checkPayload, MsgType.CHECK, user.id.toString(), 1) { success, _ ->
                                }
                            }
                            onUserClick(user)
                        }
                        .background(cardColor, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Online dot
                        if (showOnlineDot(user)) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
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

                        val timeText = userPreviews[user]?.lastMessageTime ?: ""
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
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

private fun buildDisplayName(user: User): String {
    return user.username
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

    return timestamp?.let { component.chat.formatTime(it) } ?: ""
}
