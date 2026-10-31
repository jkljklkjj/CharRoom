package component.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.chatlite.i18n.LocalStrings
import component.TokenQuotaScreen
import component.dialog.AddUserOrGroupDialog
import component.dialog.ApplicationDialog
import component.settings.SettingsScreen
import component.social.SocialScreen
import component.user.ProfileScreen
import component.user.SidebarTab
import component.user.UserList
import core.AgentStreamHandler
import core.AppLogger
import core.Chat
import core.MessageReceiveListener
import core.state.GlobalAppState
import core.state.GlobalChatState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import model.Message
import org.koin.compose.koinInject
import presentation.viewmodel.ChatViewModel
import presentation.viewmodel.addGroupMessage
import presentation.viewmodel.addMessage
import presentation.viewmodel.confirmMessageId
import presentation.viewmodel.fetchRequests
import presentation.viewmodel.flushLocalHistory
import presentation.viewmodel.loadContacts
import presentation.viewmodel.updateUsers
import presentation.viewmodel.upsertAgentStreamMessage

/** 聊天主界面 */
@Composable
fun ChatApp(
    windowSize: DpSize,
    token: String,
    onLogout: () -> Unit,
    onBackPressed: ((() -> Boolean) -> Unit)? = null,
    navigationRequest: model.ChatNavigationRequest? = null,
    onNavigationHandled: () -> Unit = {},
) {
    val scaffoldState = rememberScaffoldState(snackbarHostState = SnackbarHostState())
    val scope = rememberCoroutineScope()
    val s = LocalStrings.current
    // 使用 Koin 注入 ChatViewModel
    val chatViewModel: ChatViewModel = koinInject()
    androidx.compose.runtime.DisposableEffect(chatViewModel, token) {
        val owner = Any()
        core.update.UpdatePreparation.register(owner) { chatViewModel.flushLocalHistory() }
        onDispose { core.update.UpdatePreparation.remove(owner) }
    }

    // 观察状态
    val selectedChatTarget by chatViewModel.selectedChatTargetFlow.collectAsState()

    val refreshTrigger by remember { mutableLongStateOf(0L) }
    // Keep navigation and each tab's query when the mobile list leaves composition.
    var sidebarTab by remember(token) { mutableStateOf(SidebarTab.MESSAGES) }
    var messageQuery by remember(token) { mutableStateOf("") }
    var contactQuery by remember(token) { mutableStateOf("") }
    val sidebarQuery = if (sidebarTab == SidebarTab.CONTACTS) contactQuery else messageQuery
    val updateSidebarQuery: (String) -> Unit = {
        if (sidebarTab == SidebarTab.CONTACTS) contactQuery = it else messageQuery = it
    }

    // 对话框状态
    var showAddUserDialog by remember { mutableStateOf(false) }
    var showApplicationDialog by remember { mutableStateOf(false) }
    var showProfileScreen by remember { mutableStateOf(false) }
    var showSocial by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var showQuotaScreen by remember { mutableStateOf(false) }
    var pendingRefreshTrigger by remember { mutableLongStateOf(0L) }

    // 用户详情页状态
    var showUserDetailScreen by remember { mutableStateOf(false) }
    var selectedDetailUserId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(navigationRequest?.requestId) {
        val request = navigationRequest ?: return@LaunchedEffect
        val user =
            kotlinx.coroutines.withTimeoutOrNull(15_000) {
                chatViewModel.usersFlow
                    .first { list -> list.any { it.conversationKey == request.conversation } }
                    .first { it.conversationKey == request.conversation }
            }
        if (user != null) {
            showSocial = false
            showSettingsScreen = false
            showProfileScreen = false
            showQuotaScreen = false
            showUserDetailScreen = false
            showAddUserDialog = false
            showApplicationDialog = false
            chatViewModel.selectedUser = user
        } else scaffoldState.snackbarHostState.showSnackbar(s["android.notification.unavailable"])
        onNavigationHandled()
    }

    // 首次加载：确保token存在后才启动QUIC连接并加载联系人
    LaunchedEffect(Unit) {
        val currentToken = GlobalAppState.currentToken
        if (currentToken.isNullOrBlank()) {
            return@LaunchedEffect
        }
        if (!Chat.isConnected()) {
            kotlinx.coroutines.withContext(Dispatchers.IO) { Chat.start() }
        }

        // 注册全局消息接收监听器
        val receiver =
            object : MessageReceiveListener {
                override fun onMessageIdConfirmed(
                    clientId: String,
                    serverId: String,
                    seqId: Long,
                    conversationId: String,
                ) {
                    chatViewModel.confirmMessageId(clientId, serverId, seqId, conversationId)
                }

                override fun onPrivateEnvelope(message: Message) {
                    chatViewModel.addMessage(message)
                    if (!message.sender)
                        core.PlatformMessageNotifications.privateMessage(
                            message.senderId,
                            message.message,
                        )
                }

                override fun onGroupEnvelope(message: model.GroupMessage) {
                    chatViewModel.addGroupMessage(message)
                    if (message.senderId != GlobalAppState.currentUserId)
                        core.PlatformMessageNotifications.groupMessage(
                            message.groupId,
                            message.senderId,
                            message.senderName,
                            message.text,
                        )
                }

                override fun onPrivateMessageReceived(
                    senderId: Int,
                    message: String,
                    timestamp: Long,
                    seqId: Long,
                    conversationId: String,
                ) {
                    // 构建消息对象并添加到聊天状态
                    val chatMessage =
                        Message(
                            senderId = senderId,
                            message = message,
                            sender = false, // 对方发送的消息
                            receiverId = GlobalAppState.currentUserId ?: 0,
                            timestamp = timestamp,
                            isSent = true,
                            messageId = "recv_${senderId}_$timestamp",
                            seqId = seqId,
                            conversationId = conversationId,
                        )
                    chatViewModel.addMessage(chatMessage)
                    core.PlatformMessageNotifications.privateMessage(senderId, message)
                    // 从消息 payload 更新增量同步游标
                    if (seqId > 0 && conversationId.isNotBlank()) {
                        scope.launch {
                            GlobalChatState.updateConversationSeqId(conversationId, seqId)
                        }
                    }
                }

                override fun onGroupMessageReceived(
                    groupId: Int,
                    senderId: Int,
                    senderName: String,
                    message: String,
                    timestamp: Long,
                    seqId: Long,
                    conversationId: String,
                ) {
                    // 构建群聊消息对象并添加到聊天状态
                    val groupMessage =
                        model.GroupMessage(
                            groupId = groupId,
                            senderName = senderName,
                            text = message,
                            senderId = senderId,
                            timestamp = timestamp,
                            isSent = true,
                            messageId = "recv_group_${groupId}_${senderId}_$timestamp",
                            seqId = seqId,
                            conversationId = conversationId,
                        )
                    chatViewModel.addGroupMessage(groupMessage)
                    core.PlatformMessageNotifications.groupMessage(
                        groupId,
                        senderId,
                        senderName,
                        message,
                    )
                    // 从消息 payload 更新增量同步游标
                    if (seqId > 0 && conversationId.isNotBlank()) {
                        scope.launch {
                            GlobalChatState.updateConversationSeqId(conversationId, seqId)
                        }
                    }
                }
            }

        // Agent 流式消息：独立回调，不走 listener 遍历
        val agentHandler = AgentStreamHandler { messageId, fullContent, done, error ->
            chatViewModel.upsertAgentStreamMessage(messageId, fullContent, done, error)
        }

        Chat.addMessageReceiveListener(receiver)
        Chat.agentStreamHandler = agentHandler
        try {
            chatViewModel.loadContacts()

            // 加载本地聊天历史
            chatViewModel.loadLocalChatHistory()

            // 增量同步所有会话（后台异步执行）
            scope.launch(Dispatchers.IO) {
                chatViewModel.syncAllConversations()
            }

            // 拉取好友和群聊请求
            chatViewModel.fetchRequests()
            kotlinx.coroutines.awaitCancellation()
        } finally {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                core.recoverOperation(
                    onFailure = { error ->
                        core.AppLogger.d("Local history flush failed: ${error.message}")
                    }
                ) {
                    chatViewModel.flushLocalHistory()
                }
            }
            Chat.removeMessageReceiveListener(receiver)
            if (Chat.agentStreamHandler === agentHandler) Chat.agentStreamHandler = null
        }
    }

    // 处理刷新
    LaunchedEffect(pendingRefreshTrigger) {
        if (pendingRefreshTrigger > 0) {
            chatViewModel.loadContacts()
        }
    }

    // 判断是否是小屏幕（手机端）
    val isSmallScreen = windowSize.width < 600.dp

    Scaffold(
        scaffoldState = scaffoldState,
        snackbarHost = { SnackbarHost(hostState = scaffoldState.snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colors.background)
        ) {
            // 调试日志：观察选中用户变化
            LaunchedEffect(selectedChatTarget) {
                AppLogger.d(
                    "selectedChatTarget changed: ${selectedChatTarget?.id} - ${selectedChatTarget?.username}"
                )
            }

            // 注册系统返回键处理逻辑
            LaunchedEffect(
                selectedChatTarget,
                isSmallScreen,
                onBackPressed,
                showSettingsScreen,
                showProfileScreen,
                showQuotaScreen,
                showAddUserDialog,
                showApplicationDialog,
                showUserDetailScreen,
                showSocial,
            ) {
                if (onBackPressed != null) {
                    when {
                        showQuotaScreen ->
                            onBackPressed {
                                showQuotaScreen = false
                                true
                            }
                        showProfileScreen ->
                            onBackPressed {
                                showProfileScreen = false
                                true
                            }
                        showSocial ->
                            onBackPressed {
                                showSocial = false
                                true
                            }
                        showUserDetailScreen -> {
                            // 在用户详情页时，返回键关闭详情页
                            onBackPressed {
                                showUserDetailScreen = false
                                selectedDetailUserId = null
                                true
                            }
                        }
                        showSettingsScreen -> {
                            // 在设置界面时，返回键关闭设置
                            onBackPressed {
                                showSettingsScreen = false
                                true
                            }
                        }
                        showAddUserDialog || showApplicationDialog -> {
                            // 在对话框时，返回键关闭对话框
                            onBackPressed {
                                showAddUserDialog = false
                                showApplicationDialog = false
                                true
                            }
                        }
                        selectedChatTarget != null && isSmallScreen -> {
                            // 在聊天界面时，返回键回到用户列表
                            onBackPressed {
                                chatViewModel.selectedUser = null
                                true // 返回true表示已处理返回事件
                            }
                        }
                        else -> {
                            // 在用户列表时，不处理返回，让系统默认退出应用
                            onBackPressed {
                                false // 返回false表示未处理，执行系统默认行为
                            }
                        }
                    }
                }
            }
            when {
                // 手机端布局：单页切换 + 滑动动画
                isSmallScreen -> {
                    AnimatedContent(
                        targetState = selectedChatTarget,
                        transitionSpec = {
                            if (targetState != null) {
                                // 进入聊天：从右侧滑入
                                slideInHorizontally(tween(250)) { it } +
                                    fadeIn(tween(200)) togetherWith
                                    slideOutHorizontally(tween(250)) { -it / 3 } +
                                        fadeOut(tween(150))
                            } else {
                                // 返回列表：从左侧滑入
                                slideInHorizontally(tween(250)) { -it / 3 } +
                                    fadeIn(tween(200)) togetherWith
                                    slideOutHorizontally(tween(250)) { it } + fadeOut(tween(150))
                            }
                        },
                        contentKey = { it?.conversationKey?.value ?: "list" },
                    ) { chatTarget ->
                        if (chatTarget == null) {
                            UserList(
                                selectedTab = sidebarTab,
                                onTabSelected = { sidebarTab = it },
                                searchQuery = sidebarQuery,
                                onSearchQueryChange = updateSidebarQuery,
                                selectedConversation = null,
                                actions =
                                    component.user.DirectoryActions(
                                        onOpenSearch = { showAddUserDialog = true },
                                        onOpenSettings = { showSettingsScreen = true },
                                        onOpenApplications = { showApplicationDialog = true },
                                        onOpenProfile = { showProfileScreen = true },
                                        onOpenDiscovery = { showSocial = true },
                                    ),
                                onUserClick = { user -> chatViewModel.selectedUser = user },
                                onUserLongClick = { user ->
                                    selectedDetailUserId = user.id
                                    showUserDetailScreen = true
                                },
                                refreshTrigger = pendingRefreshTrigger,
                                chatViewModel = chatViewModel,
                            )
                        } else {
                            key(chatTarget.conversationKey.value) {
                                ContactChatScreen(
                                    chatViewModel = chatViewModel,
                                    user = chatTarget,
                                    onAvatarClick = { user ->
                                        selectedDetailUserId = user.id
                                        showUserDetailScreen = true
                                    },
                                    onMyAvatarClick = { showProfileScreen = true },
                                    onBackClick = { chatViewModel.selectedUser = null },
                                )
                            }
                        }
                    }
                }
                // 桌面端/平板布局：左右分栏
                else -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier =
                                Modifier.width(if (windowSize.width >= 1000.dp) 350.dp else 300.dp)
                                    .fillMaxSize()
                        ) {
                            UserList(
                                selectedTab = sidebarTab,
                                onTabSelected = { sidebarTab = it },
                                searchQuery = sidebarQuery,
                                onSearchQueryChange = updateSidebarQuery,
                                selectedConversation = selectedChatTarget?.conversationKey,
                                actions =
                                    component.user.DirectoryActions(
                                        onOpenSearch = { showAddUserDialog = true },
                                        onOpenSettings = { showSettingsScreen = true },
                                        onOpenApplications = { showApplicationDialog = true },
                                        onOpenProfile = { showProfileScreen = true },
                                        onOpenDiscovery = { showSocial = true },
                                    ),
                                onUserClick = { user -> chatViewModel.selectedUser = user },
                                onUserLongClick = { user ->
                                    selectedDetailUserId = user.id
                                    showUserDetailScreen = true
                                },
                                refreshTrigger = pendingRefreshTrigger,
                                chatViewModel = chatViewModel,
                            )
                        }

                        Divider(
                            modifier = Modifier.width(1.dp).fillMaxSize(),
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.1f),
                        )

                        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                            if (selectedChatTarget != null) {
                                key(selectedChatTarget!!.conversationKey.value) {
                                    ContactChatScreen(
                                        chatViewModel = chatViewModel,
                                        user = selectedChatTarget!!,
                                        onAvatarClick = { user ->
                                            selectedDetailUserId = user.id
                                            showUserDetailScreen = true
                                        },
                                        onMyAvatarClick = { showProfileScreen = true },
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    androidx.compose.material.Text(
                                        text = s["app.select.contact"],
                                        style = MaterialTheme.typography.h6,
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showSocial) {
                SocialScreen(
                    token,
                    GlobalAppState.currentUserId?.toString().orEmpty(),
                    onBack = { showSocial = false },
                    onChat = { user ->
                        chatViewModel.updateUsers(
                            (chatViewModel.usersFlow.value + user).distinctBy { it.conversationKey }
                        )
                        chatViewModel.selectedUser = user
                        showSocial = false
                    },
                )
            }

            // 添加好友/群组对话框
            if (showAddUserDialog) {
                AddUserOrGroupDialog(
                    chatViewModel = chatViewModel,
                    onDismiss = {
                        showAddUserDialog = false
                        pendingRefreshTrigger++
                        scope.launch {
                            scaffoldState.snackbarHostState.showSnackbar(s["dialog.add.success"])
                        }
                    },
                )
            }

            // 申请管理对话框
            if (showApplicationDialog) {
                ApplicationDialog(
                    chatViewModel = chatViewModel,
                    onDismiss = { showApplicationDialog = false },
                )
            }

            // 全屏覆盖页面：从底部滑入
            val slideUpSpec = tween<IntOffset>(280)
            val fadeSpec = tween<Float>(200)

            // 我的资料页面
            AnimatedVisibility(
                visible = showProfileScreen,
                enter = slideInVertically(slideUpSpec) { it } + fadeIn(fadeSpec),
                exit = slideOutVertically(slideUpSpec) { it } + fadeOut(fadeSpec),
            ) {
                ProfileScreen(
                    onBack = { showProfileScreen = false },
                    onProfileUpdated = {
                        chatViewModel.loadContacts()
                        showProfileScreen = false
                    },
                )
            }

            // 设置界面
            AnimatedVisibility(
                visible = showSettingsScreen,
                enter = slideInVertically(slideUpSpec) { it } + fadeIn(fadeSpec),
                exit = slideOutVertically(slideUpSpec) { it } + fadeOut(fadeSpec),
            ) {
                SettingsScreen(
                    chatViewModel = chatViewModel,
                    onBackClick = { showSettingsScreen = false },
                    onLogout = {
                        chatViewModel.clear()
                        onLogout()
                    },
                    onProfileClick = {
                        showSettingsScreen = false
                        showProfileScreen = true
                    },
                    onQuotaClick = {
                        showSettingsScreen = false
                        showQuotaScreen = true
                    },
                )
            }

            // Token 配额页
            AnimatedVisibility(
                visible = showQuotaScreen,
                enter = slideInVertically(slideUpSpec) { it } + fadeIn(fadeSpec),
                exit = slideOutVertically(slideUpSpec) { it } + fadeOut(fadeSpec),
            ) {
                TokenQuotaScreen(
                    onBack = {
                        showQuotaScreen = false
                        showSettingsScreen = true
                    }
                )
            }

            // 用户详情页
            AnimatedVisibility(
                visible = showUserDetailScreen && selectedDetailUserId != null,
                enter = slideInVertically(slideUpSpec) { it } + fadeIn(fadeSpec),
                exit = slideOutVertically(slideUpSpec) { it } + fadeOut(fadeSpec),
            ) {
                if (selectedDetailUserId != null) {
                    SocialScreen(
                        token = token,
                        userId = GlobalAppState.currentUserId?.toString().orEmpty(),
                        initialProfileId = selectedDetailUserId!!.toString(),
                        onBack = {
                            showUserDetailScreen = false
                            selectedDetailUserId = null
                        },
                        onChat = { user ->
                            showUserDetailScreen = false
                            chatViewModel.updateUsers(
                                (chatViewModel.usersFlow.value + user).distinctBy {
                                    it.conversationKey
                                }
                            )
                            chatViewModel.selectedUser = user
                            selectedDetailUserId = null
                        },
                    )
                }
            }
        }
    }
}
