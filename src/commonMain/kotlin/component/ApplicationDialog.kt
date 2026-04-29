package component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import core.ApiService
import core.loadImageBitmapFromUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import model.User
import model.updateList
import model.sidebarHeaderBrush

/**
 * 申请类型
 */
enum class ApplicationType {
    GROUP, // 群聊申请
    FRIEND // 好友申请
}

/**
 * 统一申请管理对话框，整合好友申请和群聊申请
 */
@Composable
fun ApplicationDialog(
    onDismiss: () -> Unit,
    initialType: ApplicationType = ApplicationType.FRIEND
) {
    var selectedTab by remember { mutableStateOf(initialType) }
    var friendApplications by remember { mutableStateOf<List<User>>(emptyList()) }
    var groupApplications by remember { mutableStateOf<List<User>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var processingUserId by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    // 加载所有申请
    suspend fun loadAllApplications() {
        isLoading = true
        val friends = withContext(Dispatchers.IO) { ApiService.fetchFriendRequests() }
        val groups = withContext(Dispatchers.IO) { ApiService.fetchGroupRequests() }
        friendApplications = friends
        groupApplications = groups
        isLoading = false
    }

    // 初始化加载数据
    LaunchedEffect(Unit) {
        loadAllApplications()
    }

    // 获取当前选中的申请列表
    val currentApplications = when (selectedTab) {
        ApplicationType.FRIEND -> friendApplications
        ApplicationType.GROUP -> groupApplications
    }

    val emptyText = when (selectedTab) {
        ApplicationType.GROUP -> "暂无待处理的群聊申请"
        ApplicationType.FRIEND -> "暂无待处理的好友申请"
    }
    val applyDescription = when (selectedTab) {
        ApplicationType.GROUP -> "申请加入群聊"
        ApplicationType.FRIEND -> "申请添加你为好友"
    }

    fun handleAction(userId: Int, accept: Boolean) {
        scope.launch {
            processingUserId = userId
            val success = withContext(Dispatchers.IO) {
                if (accept) {
                    when (selectedTab) {
                        ApplicationType.GROUP -> ApiService.acceptGroupApplication("0", userId.toString())
                        ApplicationType.FRIEND -> ApiService.acceptFriend(userId.toString())
                    }
                } else {
                    when (selectedTab) {
                        ApplicationType.GROUP -> ApiService.rejectGroupApplication("0", userId.toString())
                        ApplicationType.FRIEND -> ApiService.rejectFriend(userId.toString())
                    }
                }
            }
            if (success) {
                // 移除已处理的申请
                when (selectedTab) {
                    ApplicationType.FRIEND -> friendApplications = friendApplications.filter { it.id != userId }
                    ApplicationType.GROUP -> groupApplications = groupApplications.filter { it.id != userId }
                }
                // 刷新用户列表
                updateList()
            }
            processingUserId = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("申请管理") },
        text = {
            Column(modifier = Modifier.widthIn(max = 350.dp)) {
                // Tab切换栏
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == ApplicationType.FRIEND,
                        onClick = { selectedTab = ApplicationType.FRIEND },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("好友申请")
                                if (friendApplications.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Badge { Text(friendApplications.size.toString()) }
                                }
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == ApplicationType.GROUP,
                        onClick = { selectedTab = ApplicationType.GROUP },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("群聊申请")
                                if (groupApplications.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Badge { Text(groupApplications.size.toString()) }
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (currentApplications.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = emptyText,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(currentApplications) { user ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colors.surface,
                                elevation = 1.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 头像显示
                                    val avatarUrl = user.avatarUrl
                                    var avatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
                                    LaunchedEffect(avatarUrl, user.avatarKey) {
                                        if (!avatarUrl.isNullOrBlank()) {
                                            avatarBitmap = loadImageBitmapFromUrl(avatarUrl, user.avatarKey)
                                        } else {
                                            avatarBitmap = null
                                        }
                                    }

                                    if (avatarBitmap != null) {
                                        Image(
                                            bitmap = avatarBitmap!!,
                                            contentDescription = "avatar",
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(
                                                    brush = sidebarHeaderBrush(!MaterialTheme.colors.isLight),
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = user.username.firstOrNull()?.toString() ?: "U",
                                                color = Color.White
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = user.username,
                                            style = MaterialTheme.typography.subtitle1
                                        )
                                        Text(
                                            text = applyDescription,
                                            style = MaterialTheme.typography.caption,
                                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { handleAction(user.id, accept = false) },
                                            enabled = processingUserId == null,
                                            colors = ButtonDefaults.buttonColors(
                                                backgroundColor = MaterialTheme.colors.error
                                            )
                                        ) {
                                            Text("拒绝")
                                        }

                                        Button(
                                            onClick = { handleAction(user.id, accept = true) },
                                            enabled = processingUserId == null
                                        ) {
                                            if (processingUserId == user.id) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colors.onPrimary
                                                )
                                            } else {
                                                Text("同意")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}