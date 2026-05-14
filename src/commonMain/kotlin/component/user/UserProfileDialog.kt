package component.user

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import core.GlobalApiService
import core.loadImageBitmapWithCache
import kotlinx.coroutines.launch
import model.User
import presentation.viewmodel.ChatViewModel

/**
 * 个人资料对话框
 */
@Composable
fun UserProfileDialog(
    chatViewModel: ChatViewModel,
    onDismiss: () -> Unit,
    onLogout: () -> Unit
) {
    var currentUser by remember { mutableStateOf<User?>(null) }
    var avatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var signature by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 加载当前用户信息
    LaunchedEffect(Unit) {
        scope.launch {
            currentUser = GlobalApiService.getCurrentUserProfile()
            currentUser?.let { user ->
                username = user.username
                phone = user.phone.orEmpty()
                signature = user.signature.orEmpty()

                // 加载头像
                user.avatarUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    avatarBitmap = loadImageBitmapWithCache(url, user.avatarKey)
                }
            }
        }
    }

    fun saveProfile() {
        if (username.isBlank()) {
            return
        }
        if (password.isNotBlank() && password != confirmPassword) {
            return
        }

        scope.launch {
            isLoading = true
            val success = GlobalApiService.updateUserProfile(
                username = username,
                phone = phone,
                signature = signature,
                password = password.takeIf { it.isNotBlank() }
            )
            if (success) {
                // 刷新用户列表
                chatViewModel.loadContacts()
                isEditing = false
                password = ""
                confirmPassword = ""
            }
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (isEditing) "编辑个人资料" else "个人信息")
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 头像
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colors.primary.copy(alpha = 0.1f))
                        .clickable(enabled = isEditing) {
                            // 后续实现头像上传功能
                        },
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
                            text = currentUser?.username?.firstOrNull()?.toString() ?: "我",
                            style = MaterialTheme.typography.h4,
                            color = MaterialTheme.colors.primary
                        )
                    }

                    if (isEditing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "更换头像",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isEditing) {
                    // 编辑模式
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("用户名") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        TextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("手机号") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        TextField(
                            value = signature,
                            onValueChange = { signature = it },
                            label = { Text("个性签名") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading,
                            maxLines = 3
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        TextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("新密码（留空不修改）") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        TextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("确认新密码") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading && password.isNotBlank(),
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            isError = password.isNotBlank() && password != confirmPassword
                        )

                        if (password.isNotBlank() && password != confirmPassword) {
                            Text(
                                text = "两次输入的密码不一致",
                                color = MaterialTheme.colors.error,
                                style = MaterialTheme.typography.caption,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else {
                    // 查看模式
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        // 用户名
                        Text(
                            text = currentUser?.username ?: "加载中...",
                            style = MaterialTheme.typography.h6,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // 用户ID
                        currentUser?.id?.let { id ->
                            Text(
                                text = "账号 ID: $id",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 个性签名
                        currentUser?.signature?.takeIf { it.isNotBlank() }?.let { sig ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "个性签名",
                                    style = MaterialTheme.typography.subtitle2,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = sig,
                                    style = MaterialTheme.typography.body1,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // 手机号
                        currentUser?.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "手机号",
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = phone,
                                    style = MaterialTheme.typography.body2
                                )
                            }
                        }
                    }
                }
            }
        },
        buttons = {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                if (isEditing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                isEditing = false
                                // 恢复原始值
                                currentUser?.let { user ->
                                    username = user.username
                                    phone = user.phone.orEmpty()
                                    signature = user.signature.orEmpty()
                                }
                                password = ""
                                confirmPassword = ""
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading
                        ) {
                            Text("取消")
                        }

                        Button(
                            onClick = ::saveProfile,
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading && username.isNotBlank() && (password.isBlank() || password == confirmPassword)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colors.onPrimary
                                )
                            } else {
                                Text("保存")
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = { isEditing = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading && currentUser != null
                    ) {
                        Text("编辑资料")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onLogout()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colors.error
                        )
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("退出登录")
                    }
                }
            }
        }
    )
}
