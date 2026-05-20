package component.chat

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
import component.settings.SettingsScreen

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
                // 手机端布局：单页切换
                isSmallScreen -> {
                    if (selectedChatTarget == null) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            UserList(
                                selectedUserId = selectedChatTarget?.id,
                                onOpenSearch = { showAddUserDialog = true },
                                onOpenSettings = { showSettingsScreen = true },
                                onOpenApplications = { showApplicationDialog = true },
                                onOpenProfile = { showProfileScreen = true },
                                onUserClick = { user ->
                                    chatViewModel.selectedUser = user
                                },
                                onUserLongClick = { user ->
                                    // 点击/长按头像都打开用户详情页面
                                    selectedDetailUserId = user.id
                                    showUserDetailScreen = true
                                },
                                refreshTrigger = pendingRefreshTrigger,
                                chatViewModel = chatViewModel
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            key(selectedChatTarget!!.id) {
                                ChatScreen(
                                    chatViewModel = chatViewModel,
                                    user = selectedChatTarget!!,
                                    onAvatarClick = { user ->
                                        // 点击对方头像，打开用户详情页
                                        selectedDetailUserId = user.id
                                        showUserDetailScreen = true
                                    },
                                    onMyAvatarClick = {
                                        // 点击自己头像，打开个人资料页
                                        showProfileScreen = true
                                    },
                                    onBackClick = {
                                        // 返回用户列表
                                        chatViewModel.selectedUser = null
                                    }
                                )
                            }
                        }
                    }
                }
                // 桌面端/平板布局：左右分栏
                else -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // 左侧用户列表
                        Box(modifier = Modifier.width(320.dp).fillMaxSize()) {
                            UserList(
                                selectedUserId = selectedChatTarget?.id,
                                onOpenSearch = { showAddUserDialog = true },
                                onOpenSettings = { showSettingsScreen = true },
                                onOpenApplications = { showApplicationDialog = true },
                                onOpenProfile = { showProfileScreen = true },
                                onUserClick = { user ->
                                    chatViewModel.selectedUser = user
                                },
                                onUserLongClick = { user ->
                                    // 点击/长按头像都打开用户详情页面
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

                        // 右侧聊天区域
                        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                            if (selectedChatTarget != null) {
                                key(selectedChatTarget!!.id) {
                                    ChatScreen(
                                        chatViewModel = chatViewModel,
                                        user = selectedChatTarget!!,
                                        onAvatarClick = { user ->
                                            // 点击对方头像，打开用户详情页
                                            selectedDetailUserId = user.id
                                            showUserDetailScreen = true
                                        },
                                        onMyAvatarClick = {
                                            // 点击自己头像，打开个人资料页
                                            showProfileScreen = true
                                        }
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = androidx.compose.ui.Alignment.Center
                                ) {
                                    androidx.compose.material.Text(
                                        text = "选择一个联系人开始聊天",
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
                            scaffoldState.snackbarHostState.showSnackbar("添加成功")
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

            // 我的资料页面（独立页面，非弹窗）
            if (showProfileScreen) {
                ProfileScreen(
                    onBack = { showProfileScreen = false },
                    onProfileUpdated = {
                        // 刷新用户列表
                        chatViewModel.loadContacts()
                        showProfileScreen = false
                    }
                )
            }

            // 设置界面
            if (showSettingsScreen) {
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
                    }
                )
            }

            // 用户详情页
            if (showUserDetailScreen && selectedDetailUserId != null) {
                UserDetailScreen(
                    chatViewModel = chatViewModel,
                    userId = selectedDetailUserId!!,
                    onBack = {
                        showUserDetailScreen = false
                        selectedDetailUserId = null
                    },
                    onAddFriend = {
                        // 已由详情页把目标联系人补入本地列表，这里不再强制拉全量，避免把临时联系人刷新掉
                    },
                    onStartChat = {
                        // 关闭详情页，打开与该用户的聊天界面
                        showUserDetailScreen = false
                        val targetUser = chatViewModel.usersFlow.value.find { it.id == selectedDetailUserId }
                        targetUser?.let {
                            chatViewModel.selectedUser = it
                        }
                        selectedDetailUserId = null
                    }
                )
            }
        }
    }
}
