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
 * 群聊申请管理对话框
 */
@Composable
fun GroupApplicationDialog(onDismiss: () -> Unit) {
    var groupApplications by remember { mutableStateOf<List<User>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var processingUserId by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    // 加载申请列表
    LaunchedEffect(Unit) {
        loadApplications()
    }

    suspend fun loadApplications() {
        isLoading = true
        groupApplications = withContext(Dispatchers.IO) {
            ApiService.fetchGroupRequests()
        }
        isLoading = false
    }

    fun handleAction(userId: Int, accept: Boolean) {
        scope.launch {
            processingUserId = userId
            val success = withContext(Dispatchers.IO) {
                if (accept) {
                    // TODO: 需要知道对应的群组ID，这里需要后端接口调整
                    // 暂时使用第一个用户所属的群组ID，实际应该从申请信息中获取
                    ApiService.acceptGroupApplication("0", userId.toString())
                } else {
                    ApiService.rejectGroupApplication("0", userId.toString())
                }
            }
            if (success) {
                // 移除已处理的申请
                groupApplications = groupApplications.filter { it.id != userId }
                // 刷新用户列表
                updateList()
            }
            processingUserId = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("群聊申请管理") },
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
                } else if (groupApplications.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无待处理的群聊申请",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(groupApplications) { user ->
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
                                            text = "申请加入群聊",
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