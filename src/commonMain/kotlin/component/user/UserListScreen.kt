package component.user

import component.*

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import core.ApiService
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

@Composable
fun UserList(
    chatViewModel: ChatViewModel,
    selectedTab: SidebarTab,
    onTabSelected: (SidebarTab) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedConversation: model.ConversationKey? = null,
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
    val s = LocalStrings.current
    val userListState by chatViewModel.usersFlow.collectAsState()
    val conversationStates by chatViewModel.conversationStatesFlow.collectAsState()
    val isContacts = selectedTab == SidebarTab.CONTACTS
    val sortedUsers = remember(userListState, conversationStates, isContacts) {
        if (isContacts) return@remember userListState
        sortConversationUsers(userListState) { key ->
            val summary = conversationStates[key]
            (summary?.lastIncomingMessageTime ?: 0L) to (summary?.unreadCount ?: 0)
        }
    }
    val filteredUsers = remember(searchQuery, sortedUsers, isContacts) {
        if (isContacts) return@remember sortedUsers
        val keyword = searchQuery.trim().lowercase()
        if (keyword.isBlank()) return@remember sortedUsers
        sortedUsers.filter { user ->
            val displayName = user.username.lowercase()
            val accountId = user.id.toString()
            val extra = user.username + " " + user.email
            extra.lowercase().contains(keyword) || displayName.contains(keyword) || accountId.contains(keyword)
        }
    }
    val directoryLocale = java.util.Locale.getDefault()
    val sortedDirectory = remember(userListState, currentUser?.id, directoryLocale) {
        buildContactDirectory(userListState, currentUser?.id ?: core.state.GlobalAppState.currentUserId, directoryLocale)
    }
    val directory = remember(sortedDirectory, searchQuery, isContacts) {
        if (isContacts) sortedDirectory.filtered(searchQuery) else sortedDirectory
    }
    val visibleCount = if (isContacts) directory.size else filteredUsers.size

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

    val onlineCount = remember(userListState) {
        userListState.count { !it.isGroup && it.id > 0 && !ServerConfig.isAgentAssistant(it.id) && it.online == true }
    }

    // On first entry, only load current user info and avatar; contact list loaded by ChatApp
    LaunchedEffect(Unit) {
        scope.launch {
            currentUser = GlobalApiService.getCurrentUserProfile()
        }
    }

    val palette = QingliaoColors
    Column(Modifier.fillMaxSize().background(palette.paper).padding(horizontal = 12.dp).statusBarsPadding().navigationBarsPadding()) {
        PaperConversationHeader(
            currentUser = currentUser,
            onlineSummary = s["contact.list.online.count"].format(onlineCount, userListState.size),
            hasPendingApplications = hasPendingApplications,
            onOpenProfile = onOpenProfile,
            onOpenSearch = onOpenSearch,
            onOpenApplications = ::handleOpenApplications,
            onOpenSettings = onOpenSettings,
            sectionLabel = s[if (isContacts) "contacts.directory" else "paper.conversations"]
        )
        PaperSidebarTabs(selectedTab, onTabSelected)
        Surface(color = palette.surface, shape = RoundedCornerShape(14.dp)) {
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(s[if (isContacts) "contacts.search" else "contact.search"], style = MaterialTheme.typography.body2, color = palette.muted) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.body2,
                colors = TextFieldDefaults.textFieldColors(
                    backgroundColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = palette.action
                ),
                leadingIcon = { Icon(Icons.Default.Search, null, tint = palette.muted, modifier = Modifier.size(20.dp)) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    { IconButton(onClick = { onSearchQueryChange("") }) { Icon(Icons.Default.Close, s["polish.clearSearch"], tint = palette.muted) } }
                } else null,
                singleLine = true
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
            if (visibleCount == 0) item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(s[if (searchQuery.isBlank()) "polish.emptyContacts" else "polish.noContacts"], color = palette.muted)
                    TextButton(onClick = { if (searchQuery.isBlank()) onOpenDiscovery() else onSearchQueryChange("") }) {
                        Text(s[if (searchQuery.isBlank()) "social.discover" else "polish.clearSearch"])
                    }
                }
            }
            val sections = if (isContacts) listOf("contacts.friends" to directory.friends, "contacts.groups" to directory.groups)
                else listOf("" to filteredUsers)
            sections.forEach { (label, contacts) ->
                if (label.isNotEmpty() && contacts.isNotEmpty()) item(key = label) { PaperContactSection(s[label], contacts.size) }
                items(contacts, key = { it.conversationKey.value }) { user ->
                    val summary=conversationStates[user.conversationKey]
                    val text=when {
                        !user.isGroup&&ServerConfig.isAgentAssistant(user.id) -> s["contact.ai.assistant"]
                        summary==null||summary.previewMessageId.isEmpty() -> if(user.isGroup)s["contact.no.group.messages"] else if(user.online==true)s["chat.status.online"] else s["contact.no.messages"]
                        user.isGroup -> "${summary.previewSender}: ${summary.previewText.take(24)}"
                        summary.previewOwn -> s["contact.me.prefix"].format(summary.previewText.take(24))
                        else -> "${user.username}: ${summary.previewText.take(24)}"
                    }
                    val preview=UserPreview(text,summary?.previewTimestamp?.takeIf { it>0 }?.let { component.chat.formatTime(it) }.orEmpty())
                    PaperConversationRow(
                        user = user,
                        subtitle = if (isContacts) user.signature?.takeIf { it.isNotBlank() }
                            ?: s[if (user.isGroup) "contacts.groupId" else "contacts.accountId"].format(user.id)
                            else preview.subtitle,
                        time = if (isContacts) "" else preview.lastMessageTime,
                        unreadCount = if (isContacts) 0 else conversationStates[user.conversationKey]?.unreadCount ?: 0,
                        selected = selectedConversation == user.conversationKey,
                        onAvatarClick = if (!user.isGroup && user.id > 0) onUserLongClick?.let { callback -> { callback(user) } } else null,
                        onClick = {
                            try {
                                ActionLogger.log(Action(type = ActionType.OPEN_CHAT, targetId = user.id.toString(), metadata = mapOf("username" to user.username)))
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                            }
                            if (Chat.isServerConnected && !user.isGroup && user.id > 0 && !ServerConfig.isAgentAssistant(user.id)) {
                                Chat.send(buildCheckPayload(user.id.toString()), MsgType.CHECK, user.id.toString(), 1) { _, _ -> }
                            }
                            onUserClick(user)
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        PaperDiscoveryEntry(onOpenDiscovery)
        Spacer(Modifier.height(12.dp))
    }
}
