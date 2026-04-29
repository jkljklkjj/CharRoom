package component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import core.ApiService
import core.loadImageBitmapFromUrl
import model.User
import model.users
import kotlinx.coroutines.launch

/**
 * 用户详情页面（查看他人信息）
 */
@Composable
fun UserDetailScreen(
    userId: Int,
    onBack: () -> Unit,
    onAddFriend: (() -> Unit)? = null
) {
    var user by remember { mutableStateOf<User?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var avatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isFriend by remember { mutableStateOf(false) }
    var isAddingFriend by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // 加载用户信息
    LaunchedEffect(userId) {
        scope.launch {
            user = ApiService.getUserDetail(userId.toString())
            user?.let {
                // 加载头像
                it.avatarUrl?.let { url ->
                    if (url.isNotBlank()) {
                        avatarBitmap = loadImageBitmapFromUrl(url, it.avatarKey)
                    }
                }
                // 检查是否已经是好友
                isFriend = users.any { u -> u.id == userId }
            }
            isLoading = false
        }
    }

    // 添加好友
    fun addFriend() {
        scope.launch {
            isAddingFriend = true
            errorMessage = ""
            successMessage = ""
            val success = ApiService.addFriend(user?.id.toString())
            if (success) {
                successMessage = "好友申请已发送，等待对方确认"
            } else {
                errorMessage = "好友申请发送失败，请稍后重试"
            }
            isAddingFriend = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("用户详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                backgroundColor = MaterialTheme.colors.primary
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            user?.let { u ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 头像区域
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colors.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarBitmap != null) {
                            Image(
                                bitmap = avatarBitmap!!,
                                contentDescription = "头像",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = u.username.firstOrNull()?.toString() ?: "U",
                                style = MaterialTheme.typography.h3,
                                color = MaterialTheme.colors.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 用户名
                    Text(
                        text = u.username,
                        style = MaterialTheme.typography.h4,
                        color = MaterialTheme.colors.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 个性签名
                    u.signature?.takeIf { it.isNotBlank() }?.let { signature ->
                        Text(
                            text = signature,
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // 用户信息卡片
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = 4.dp,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // 用户ID
                            InfoRow(label = "用户ID", value = u.id.toString())

                            Divider(modifier = Modifier.padding(vertical = 12.dp))

                            // 邮箱（如果是好友才显示）
                            if (isFriend) {
                                u.email?.takeIf { it.isNotBlank() }?.let { email ->
                                    InfoRow(label = "邮箱", value = email)
                                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                                }
                            }

                            // 电话（如果是好友才显示）
                            if (isFriend) {
                                u.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                                    InfoRow(label = "电话", value = phone)
                                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                                }
                            }

                            // 在线状态
                            InfoRow(
                                label = "在线状态",
                                value = if (u.online == true) "在线" else "离线",
                                valueColor = if (u.online == true) MaterialTheme.colors.secondary else MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // 操作按钮
                    if (!isFriend) {
                        Button(
                            onClick = { addFriend() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isAddingFriend
                        ) {
                            if (isAddingFriend) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colors.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("发送中...")
                            } else {
                                Text("添加好友")
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                // 发起聊天
                                onBack()
                                // 这里可以回调打开聊天界面
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("发消息")
                        }
                    }

                    // 提示信息
                    if (successMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = successMessage,
                            color = MaterialTheme.colors.secondary,
                            style = MaterialTheme.typography.body2
                        )
                    }
                    if (errorMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colors.error,
                            style = MaterialTheme.typography.body2
                        )
                    }
                }
            } ?: run {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("用户不存在")
                }
            }
        }
    }
}

/**
 * 信息行组件
 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colors.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.body1,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.body1,
            color = valueColor
        )
    }
}
