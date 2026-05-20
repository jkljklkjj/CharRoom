package component.user

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import core.ApiService
import core.getCachedImage
import core.loadImageBitmapFromUrl
import model.User
import kotlinx.coroutines.launch
import presentation.viewmodel.ChatViewModel

/**
 * 用户详情页面（查看他人信息）
 */
@Composable
fun UserDetailScreen(
    chatViewModel: ChatViewModel,
    userId: Int,
    onBack: () -> Unit,
    onAddFriend: (() -> Unit)? = null,
    onStartChat: (() -> Unit)? = null
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
            user?.let { it ->
                // 优先从缓存同步读取头像，避免闪动
                it.avatarUrl?.let { url ->
                    if (url.isNotBlank()) {
                        val cacheKey = it.avatarKey
                        avatarBitmap = cacheKey?.let { key -> getCachedImage(key) } ?: loadImageBitmapFromUrl(url, cacheKey)
                    }
                }
                // 检查是否已经是好友
                isFriend = chatViewModel.usersFlow.value.any { u -> u.id == userId }
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
                user?.let { targetUser ->
                    val currentUsers = chatViewModel.usersFlow.value
                    if (currentUsers.none { it.id == targetUser.id }) {
                        chatViewModel.updateUsers(currentUsers + targetUser)
                    }
                }
                successMessage = "好友申请已发送，可以先发个招呼消息"
            } else {
                errorMessage = "好友申请发送失败，请稍后重试"
            }
            isAddingFriend = false
        }
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colors.surface.copy(alpha = 0.18f),
                shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
                elevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = "用户详情", style = MaterialTheme.typography.h6)
                        Text(
                            text = "查看资料、状态和联系信息",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colors.background)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                user?.let { u ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colors.surface.copy(alpha = 0.24f),
                            shape = RoundedCornerShape(26.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.08f)),
                            elevation = 0.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(128.dp)
                                        .shadow(10.dp, CircleShape, clip = true)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colors.primary.copy(alpha = 0.10f)),
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
                                            style = MaterialTheme.typography.h2,
                                            color = MaterialTheme.colors.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                Text(
                                    text = u.username,
                                    style = MaterialTheme.typography.h5,
                                    color = MaterialTheme.colors.onSurface
                                )

                                // 个性签名 - 移到用户名下方更明显的位置
                                u.signature?.takeIf { it.isNotBlank() }?.let { signature ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = signature,
                                        style = MaterialTheme.typography.body1,
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.70f),
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DetailBadge(
                                        text = if (u.online == true) "在线" else "离线",
                                        backgroundColor = if (u.online == true) MaterialTheme.colors.secondary.copy(alpha = 0.14f) else MaterialTheme.colors.onSurface.copy(alpha = 0.08f),
                                        contentColor = if (u.online == true) MaterialTheme.colors.secondary else MaterialTheme.colors.onSurface.copy(alpha = 0.72f)
                                    )
                                    DetailBadge(
                                        text = if (isFriend) "已是好友" else "非好友",
                                        backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.12f),
                                        contentColor = MaterialTheme.colors.primary
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        DetailCard(title = "资料") {
                            InfoRow(label = "用户ID", value = u.id.toString())

                            if (isFriend) {
                                u.email?.takeIf { it.isNotBlank() }?.let { email ->
                                    DetailDivider()
                                    InfoRow(label = "邮箱", value = email)
                                }
                                u.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                                    DetailDivider()
                                    InfoRow(label = "电话", value = phone)
                                }
                            }

                            DetailDivider()
                            InfoRow(
                                label = "在线状态",
                                value = if (u.online == true) "在线" else "离线",
                                valueColor = if (u.online == true) MaterialTheme.colors.secondary else MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )

                            u.createdAt?.let { timestamp ->
                                DetailDivider()
                                InfoRow(
                                    label = "注册时间",
                                    value = java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.getDefault())
                                        .format(java.util.Date(timestamp))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        DetailCard(title = "操作") {
                            if (!isFriend && successMessage.isBlank()) {
                                Button(
                                    onClick = { addFriend() },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    enabled = !isAddingFriend,
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    if (isAddingFriend) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
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
                                        onStartChat?.invoke() ?: onBack()
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(if (isFriend) "发消息" else "发招呼消息")
                                }
                            }
                        }

                        if (successMessage.isNotBlank() || errorMessage.isNotBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colors.surface.copy(alpha = 0.20f),
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.08f)),
                                elevation = 0.dp
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    if (successMessage.isNotBlank()) {
                                        Text(
                                            text = successMessage,
                                            color = MaterialTheme.colors.secondary,
                                            style = MaterialTheme.typography.body2
                                        )
                                    }
                                    if (errorMessage.isNotBlank()) {
                                        Text(
                                            text = errorMessage,
                                            color = MaterialTheme.colors.error,
                                            style = MaterialTheme.typography.body2
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                } ?: run {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("用户不存在")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colors.surface.copy(alpha = 0.24f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.08f)),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.subtitle1,
                color = MaterialTheme.colors.onSurface
            )
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun DetailBadge(
    text: String,
    backgroundColor: Color,
    contentColor: Color
) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.10f))
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.caption,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun DetailDivider() {
    Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colors.onSurface.copy(alpha = 0.06f))
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
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.58f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.body2,
            color = valueColor
        )
    }
}
