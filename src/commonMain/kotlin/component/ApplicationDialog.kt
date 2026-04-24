package component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import core.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import model.User
import model.updateList

/**
 * 申请类型
 */
enum class ApplicationType {
    GROUP, // 群聊申请
    FRIEND // 好友申请
}

/**
 * 申请管理对话框，支持群聊申请和好友申请
 */
@Composable
fun ApplicationDialog(
    onDismiss: () -> Unit,
    type: ApplicationType = ApplicationType.GROUP
) {
    var applications by remember { mutableStateOf<List<User>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var processingUserId by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    // 对话框标题和描述
    val title = when (type) {
        ApplicationType.GROUP -> "群聊申请管理"
        ApplicationType.FRIEND -> "好友申请管理"
    }
    val emptyText = when (type) {
        ApplicationType.GROUP -> "暂无待处理的群聊申请"
        ApplicationType.FRIEND -> "暂无待处理的好友申请"
    }
    val applyDescription = when (type) {
        ApplicationType.GROUP -> "申请加入群聊"
        ApplicationType.FRIEND -> "申请添加你为好友"
    }

    // 加载申请列表
    LaunchedEffect(type) {
        loadApplications()
    }

    suspend fun loadApplications() {
        isLoading = true
        applications = withContext(Dispatchers.IO) {
            when (type) {
                ApplicationType.GROUP -> ApiService.fetchGroupRequests()
                ApplicationType.FRIEND -> ApiService.fetchFriendRequests()
            }
        }
        isLoading = false
    }

    fun handleAction(userId: Int, accept: Boolean) {
        scope.launch {
            processingUserId = userId
            val success = withContext(Dispatchers.IO) {
                if (accept) {
                    when (type) {
                        ApplicationType.GROUP -> ApiService.acceptGroupApplication("0", userId.toString())
                        ApplicationType.FRIEND -> ApiService.acceptFriend(userId.toString())
                    }
                } else {
                    when (type) {
                        ApplicationType.GROUP -> ApiService.rejectGroupApplication("0", userId.toString())
                        ApplicationType.FRIEND -> ApiService.rejectFriend(userId.toString())
                    }
                }
            }
            if (success) {
                // 移除已处理的申请
                applications = applications.filter { it.id != userId }
                // 刷新用户列表
                updateList()
            }
            processingUserId = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (applications.isEmpty()) {
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
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(applications) { user ->
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