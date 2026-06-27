package component.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import component.ChatScreen
import component.dialog.AddUserOrGroupDialog
import component.dialog.ApplicationDialog
import component.user.UserDetailScreen
import component.user.UserList
import component.user.ProfileScreen
import core.Chat
import core.MessageReceiveListener
import core.state.GlobalAppState
import core.state.GlobalChatState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import model.Message
import model.User
import presentation.viewmodel.ChatViewModel
import com.chatlite.i18n.LocalStrings
import component.settings.SettingsScreen
import component.TokenQuotaScreen

/**
 * 聊天主界面
 */
@Composable
fun ChatApp(
    windowSize: DpSize,
    token: String,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onLogout: () -> Unit,
    onBackPressed: ((() -> Boolean) -> Unit)? = null
) {
    val scaffoldState = rememberScaffoldState(snackbarHostState = SnackbarHostState())
    val scope = rememberCoroutineScope()
    val s = LocalStrings.current
    // 方案B：每个屏幕使用独立ViewModel实例，但共享全局ChatState数据源
    val chatViewModel = remember { ChatViewModel(chatState = GlobalChatState) }

    // 观察状态
    val selectedChatTarget by chatViewModel.selectedChatTargetFlow.collectAsState()
    val users by chatViewModel.usersFlow.collectAsState()
    val refreshTrigger by remember { mutableLongStateOf(0L) }

    // 对话框状态
    var showAddUserDialog by remember { mutableStateOf(false) }
    var showApplicationDialog by remember { mutableStateOf(false) }
    var showProfileScreen by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var showQuotaScreen by remember { mutableStateOf(false) }
    var pendingRefreshTrigger by remember { mutableStateOf(0L) }

    // 用户详情页状态
    var showUserDetailScreen by remember { mutableStateOf(false) }
    var selectedDetailUserId by remember { mutableStateOf<Int?>(null) }

    // 首次加载：确保token存在后才启动WebSocket连接并加载联系人
    LaunchedEffect(Unit) {
        val currentToken = GlobalAppState.currentToken
        if (currentToken.isNullOrBlank()) {
            println("[ChatApp] 错误：启动WebSocket前token为空，请先登录")
            return@LaunchedEffect
        }
        println("[ChatApp] 准备启动WebSocket连接，token长度: ${currentToken.length}, userId: ${GlobalAppState.currentUserId}")
        if (!Chat.isConnected()) {
            Chat.start() // 启动WebSocket连接，触发握手流程，此时header会携带token
        }

        // 注册全局消息接收监听器
        Chat.addMessageReceiveListener(object : MessageReceiveListener {
            override fun onPrivateMessageReceived(senderId: Int, message: String, timestamp: Long) {
                println("[ChatApp] 收到私聊消息：senderId=$senderId, content=$message, timestamp=$timestamp")
                // 构建消息对象并添加到聊天状态
                val chatMessage = Message(
                    senderId = senderId,
                    message = message,
                    sender = false, // 对方发送的消息
                    receiverId = GlobalAppState.currentUserId ?: 0,
                    timestamp = timestamp,
                    isSent = true,
                    messageId = "recv_${senderId}_$timestamp"
                )
                chatViewModel.addMessage(chatMessage)
            }

            override fun onGroupMessageReceived(
                groupId: Int,
                senderId: Int,
                senderName: String,
                message: String,
                timestamp: Long
            ) {
                println("[ChatApp] 收到群聊消息：groupId=$groupId, senderId=$senderId, senderName=$senderName, content=$message, timestamp=$timestamp")
                // 构建群聊消息对象并添加到聊天状态
                val groupMessage = model.GroupMessage(
                    groupId = groupId,
                    senderName = senderName,
                    text = message,
                    senderId = senderId,
                    timestamp = timestamp,
                    isSent = true,
                    messageId = "recv_group_${groupId}_${senderId}_$timestamp"
                )
                chatViewModel.addGroupMessage(groupMessage)
            }

            override fun onAgentStreamChunk(messageId: String, fullContent: String, done: Boolean, error: Boolean) {
                chatViewModel.upsertAgentStreamMessage(messageId, fullContent)
            }
        })

        chatViewModel.loadContacts()

        // 加载本地聊天历史
        chatViewModel.loadLocalChatHistory()

        // 拉取离线消息（后台异步执行）
        scope.launch(Dispatchers.IO) {
            chatViewModel.fetchOfflineMessages()
        }

        // 拉取好友和群聊请求
        chatViewModel.fetchRequests()
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
        snackbarHost = { SnackbarHost(hostState = scaffoldState.snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colors.background)
        ) {
            // 调试日志：观察选中用户变化
            LaunchedEffect(selectedChatTarget) {
                println("[ChatApp DEBUG] selectedChatTarget changed: ${selectedChatTarget?.id} - ${selectedChatTarget?.username}")
            }

            // 注册系统返回键处理逻辑
            LaunchedEffect(selectedChatTarget, isSmallScreen, onBackPressed, showSettingsScreen, showProfileScreen, showAddUserDialog, showApplicationDialog, showUserDetailScreen) {
                if (isSmallScreen && onBackPressed != null) {
                    when {
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
                        selectedChatTarget != null -> {
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
                                slideInHorizontally(tween(250)) { it } + fadeIn(tween(200)) togetherWith
                                    slideOutHorizontally(tween(250)) { -it / 3 } + fadeOut(tween(150))
                            } else {
                                // 返回列表：从左侧滑入
                                slideInHorizontally(tween(250)) { -it / 3 } + fadeIn(tween(200)) togetherWith
                                    slideOutHorizontally(tween(250)) { it } + fadeOut(tween(150))
                            }
                        },
                        contentKey = { it?.id ?: -1 }
                    ) { chatTarget ->
                        if (chatTarget == null) {
                            UserList(
                                selectedUserId = null,
                                onOpenSearch = { showAddUserDialog = true },
                                onOpenSettings = { showSettingsScreen = true },
                                onOpenApplications = { showApplicationDialog = true },
                                onOpenProfile = { showProfileScreen = true },
                                onUserClick = { user -> chatViewModel.selectedUser = user },
                                onUserLongClick = { user ->
                                    selectedDetailUserId = user.id
                                    showUserDetailScreen = true
                                },
                                refreshTrigger = pendingRefreshTrigger,
                                chatViewModel = chatViewModel
                            )
                        } else {
                            key(chatTarget.id) {
                                ChatScreen(
                                    chatViewModel = chatViewModel,
                                    user = chatTarget,
                                    onAvatarClick = { user ->
                                        selectedDetailUserId = user.id
                                        showUserDetailScreen = true
                                    },
                                    onMyAvatarClick = { showProfileScreen = true },
                                    onBackClick = { chatViewModel.selectedUser = null }
                                )
                            }
                        }
                    }
                }
                // 桌面端/平板布局：左右分栏
                else -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.width(320.dp).fillMaxSize()) {
                            UserList(
                                selectedUserId = selectedChatTarget?.id,
                                onOpenSearch = { showAddUserDialog = true },
                                onOpenSettings = { showSettingsScreen = true },
                                onOpenApplications = { showApplicationDialog = true },
                                onOpenProfile = { showProfileScreen = true },
                                onUserClick = { user -> chatViewModel.selectedUser = user },
                                onUserLongClick = { user ->
                                    selectedDetailUserId = user.id
                                    showUserDetailScreen = true
                                },
                                refreshTrigger = pendingRefreshTrigger,
                                chatViewModel = chatViewModel
                            )
                        }

                        Divider(
                            modifier = Modifier.width(1.dp).fillMaxSize(),
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.1f)
                        )

                        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                            if (selectedChatTarget != null) {
                                key(selectedChatTarget!!.id) {
                                    ChatScreen(
                                        chatViewModel = chatViewModel,
                                        user = selectedChatTarget!!,
                                        onAvatarClick = { user ->
                                            selectedDetailUserId = user.id
                                            showUserDetailScreen = true
                                        },
                                        onMyAvatarClick = { showProfileScreen = true }
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.material.Text(
                                        text = s["app.select.contact"],
                                        style = MaterialTheme.typography.h6,
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
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
                    }
                )
            }

            // 申请管理对话框
            if (showApplicationDialog) {
                ApplicationDialog(
                    chatViewModel = chatViewModel,
                    onDismiss = { showApplicationDialog = false }
                )
            }

            // 全屏覆盖页面：从底部滑入
            val slideUpSpec = tween<IntOffset>(280)
            val fadeSpec = tween<Float>(200)

            // 我的资料页面
            AnimatedVisibility(
                visible = showProfileScreen,
                enter = slideInVertically(slideUpSpec) { it } + fadeIn(fadeSpec),
                exit = slideOutVertically(slideUpSpec) { it } + fadeOut(fadeSpec)
            ) {
                ProfileScreen(
                    onBack = { showProfileScreen = false },
                    onProfileUpdated = {
                        chatViewModel.loadContacts()
                        showProfileScreen = false
                    }
                )
            }

            // 设置界面
            AnimatedVisibility(
                visible = showSettingsScreen,
                enter = slideInVertically(slideUpSpec) { it } + fadeIn(fadeSpec),
                exit = slideOutVertically(slideUpSpec) { it } + fadeOut(fadeSpec)
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
                    }
                )
            }

            // Token 配额页
            AnimatedVisibility(
                visible = showQuotaScreen,
                enter = slideInVertically(slideUpSpec) { it } + fadeIn(fadeSpec),
                exit = slideOutVertically(slideUpSpec) { it } + fadeOut(fadeSpec)
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
                exit = slideOutVertically(slideUpSpec) { it } + fadeOut(fadeSpec)
            ) {
                if (selectedDetailUserId != null) {
                    UserDetailScreen(
                        chatViewModel = chatViewModel,
                        userId = selectedDetailUserId!!,
                        onBack = {
                            showUserDetailScreen = false
                            selectedDetailUserId = null
                        },
                        onAddFriend = {},
                        onStartChat = {
                            showUserDetailScreen = false
                            val targetUser = chatViewModel.usersFlow.value.find { it.id == selectedDetailUserId }
                            targetUser?.let { chatViewModel.selectedUser = it }
                            selectedDetailUserId = null
                        }
                    )
                }
            }
        }
    }
}
