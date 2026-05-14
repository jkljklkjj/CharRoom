package component.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import core.LocalChatHistoryStore
import core.state.GlobalAppState
import presentation.viewmodel.ChatViewModel

/**
 * 设置界面
 */
@Composable
fun SettingsScreen(
    chatViewModel: ChatViewModel,
    onBackClick: () -> Unit,
    onLogout: () -> Unit,
    onProfileClick: () -> Unit = {}
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background) // 完全不透明背景，作为独立窗口
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // 顶部标题栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colors.surface.copy(alpha = 0.18f),
            shape = MaterialTheme.shapes.large,
            elevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colors.onBackground,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(onClick = onBackClick)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.h6,
                    color = MaterialTheme.colors.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 设置选项列表
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 个人资料
            SettingItem(
                icon = Icons.Default.Person,
                title = "个人资料",
                subtitle = "查看和编辑个人信息",
                onClick = onProfileClick
            )

            // 清空聊天记录
            SettingItem(
                icon = Icons.Default.Delete,
                title = "清空聊天记录",
                subtitle = "删除所有本地聊天消息",
                onClick = {
                    showClearHistoryDialog = true
                },
                tint = Color(0xFFF44336)
            )

            // 清空缓存
            SettingItem(
                icon = Icons.Default.Delete,
                title = "清空缓存",
                subtitle = "删除头像、图片等缓存文件",
                onClick = {
                    showClearCacheDialog = true
                },
                tint = Color(0xFFF44336)
            )

            // 退出登录
            SettingItem(
                icon = Icons.Default.Logout,
                title = "退出登录",
                subtitle = "退出当前账号",
                onClick = {
                    showLogoutDialog = true
                },
                tint = Color(0xFFF44336)
            )
        }
    }

    // 退出登录确认对话框
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出登录") },
            text = { Text("确定要退出当前账号吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        // 清除本地数据
                        chatViewModel.clear()
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFFF44336),
                        contentColor = Color.White
                    )
                ) {
                    Text("退出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 清空聊天记录确认对话框
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("清空聊天记录") },
            text = { Text("确定要删除所有本地聊天记录吗？此操作不可恢复。") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearHistoryDialog = false
                        // 清除本地聊天记录
                        GlobalAppState.currentUserId?.toString()?.let { accountId ->
                            LocalChatHistoryStore.clear(accountId)
                        }
                        chatViewModel.clearMessages()
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFFF44336),
                        contentColor = Color.White
                    )
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 清空缓存确认对话框
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("清空缓存") },
            text = { Text("确定要删除所有缓存文件吗？头像、图片等会重新下载。") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCacheDialog = false
                        // 后续实现清空缓存逻辑
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFFF44336),
                        contentColor = Color.White
                    )
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 设置项组件
 */
@Composable
private fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colors.onBackground
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colors.surface.copy(alpha = 0.7f),
        shape = RoundedCornerShape(12.dp),
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = tint.copy(alpha = 0.1f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.subtitle1,
                    color = MaterialTheme.colors.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
