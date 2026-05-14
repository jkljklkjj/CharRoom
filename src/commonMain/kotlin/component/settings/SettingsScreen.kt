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
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import core.AppConfig
import core.GlobalAppUpdateManager
import core.UpdateState
import core.LocalChatHistoryStore
import core.model.AppVersionInfo
import core.state.GlobalAppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import presentation.viewmodel.ChatViewModel

/**
 * 获取当前平台
 */
expect fun getPlatform(): String

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
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showUpdateProgressDialog by remember { mutableStateOf(false) }
    var latestVersionInfo: AppVersionInfo? by remember { mutableStateOf(null) }
    var updateError: String? by remember { mutableStateOf(null) }

    // 监听更新状态
    val updateState by GlobalAppUpdateManager.updateState.collectAsState()

    LaunchedEffect(updateState) {
        when (val state = updateState) {
            is UpdateState.Available -> {
                latestVersionInfo = state.versionInfo
                showUpdateDialog = true
            }
            is UpdateState.Downloading -> {
                showUpdateProgressDialog = true
            }
            is UpdateState.Downloaded -> {
                showUpdateProgressDialog = false
                // 询问是否安装
                if (state.versionInfo.forceUpdate) {
                    // 强制更新直接安装
                    launch(Dispatchers.IO) {
                        GlobalAppUpdateManager.installUpdate(state.filePath)
                    }
                } else {
                    // 弹出确认安装对话框
                    showUpdateDialog = true
                }
            }
            is UpdateState.Failed -> {
                showUpdateProgressDialog = false
                updateError = state.error
                showUpdateDialog = true
            }
            is UpdateState.NoUpdate -> {
                updateError = "当前已是最新版本"
                showUpdateDialog = true
            }
            else -> {}
        }
    }

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

            // 检查更新
            SettingItem(
                icon = Icons.Default.SystemUpdate,
                title = "检查更新",
                subtitle = "当前版本 v${AppConfig.VERSION_NAME} (${AppConfig.VERSION_CODE})",
                onClick = {
                    // 启动协程检查更新
                    androidx.compose.runtime.rememberCoroutineScope().launch(Dispatchers.IO) {
                        GlobalAppUpdateManager.checkForUpdates(
                            platform = getPlatform(),
                            autoDownload = false
                        )
                    }
                }
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

    // 更新提示对话框
    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = {
                showUpdateDialog = false
                updateError = null
                if (latestVersionInfo?.forceUpdate == true) {
                    // 强制更新不能关闭
                    return@AlertDialog
                }
            },
            title = {
                Text(
                    when {
                        updateError != null -> "更新提示"
                        latestVersionInfo != null -> "发现新版本 v${latestVersionInfo?.versionName}"
                        else -> "检查更新"
                    }
                )
            },
            text = {
                when {
                    updateError != null -> {
                        Text(updateError!!)
                    }
                    latestVersionInfo != null -> {
                        val version = latestVersionInfo!!
                        Column {
                            Text("新版本大小: ${formatFileSize(version.fileSize)}")
                            if (version.releaseTime != null) {
                                Text("发布时间: ${version.releaseTime}")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("更新内容:\n${version.updateContent}")
                            if (version.forceUpdate) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("此版本为强制更新，必须安装后才能继续使用", color = Color(0xFFF44336))
                            }
                        }
                    }
                    else -> {
                        Text("检查更新中...")
                    }
                }
            },
            confirmButton = {
                when {
                    updateError != null -> {
                        TextButton(onClick = {
                            showUpdateDialog = false
                            updateError = null
                        }) {
                            Text("确定")
                        }
                    }
                    latestVersionInfo != null -> {
                        val version = latestVersionInfo!!
                        when (updateState) {
                            is UpdateState.Downloaded -> {
                                Button(
                                    onClick = {
                                        showUpdateDialog = false
                                        androidx.compose.runtime.rememberCoroutineScope().launch(Dispatchers.IO) {
                                            val filePath = (updateState as UpdateState.Downloaded).filePath
                                            GlobalAppUpdateManager.installUpdate(filePath)
                                        }
                                    }
                                ) {
                                    Text("立即安装")
                                }
                            }
                            else -> {
                                Button(
                                    onClick = {
                                        showUpdateDialog = false
                                        androidx.compose.runtime.rememberCoroutineScope().launch(Dispatchers.IO) {
                                            GlobalAppUpdateManager.downloadUpdate(version)
                                        }
                                    }
                                ) {
                                    Text(if (version.forceUpdate) "强制更新" else "立即更新")
                                }
                            }
                        }
                    }
                    else -> {}
                }
            },
            dismissButton = {
                if (latestVersionInfo?.forceUpdate != true && updateError == null && latestVersionInfo != null) {
                    TextButton(
                        onClick = {
                            showUpdateDialog = false
                        },
                        enabled = latestVersionInfo?.forceUpdate != true
                    ) {
                        Text("稍后再说")
                    }
                }
            }
        )
    }

    // 更新进度对话框
    if (showUpdateProgressDialog) {
        val downloadState = updateState as? UpdateState.Downloading
        AlertDialog(
            onDismissRequest = {
                if (latestVersionInfo?.forceUpdate != true) {
                    showUpdateProgressDialog = false
                    GlobalAppUpdateManager.cancelDownload()
                }
            },
            title = { Text("下载更新中") },
            text = {
                Column {
                    val progress = downloadState?.progress ?: 0
                    val total = downloadState?.total ?: 0
                    Text("已下载: $progress% (${formatFileSize(progress.toLong() * total / 100)}/${formatFileSize(total)})")
                    Spacer(modifier = Modifier.height(8.dp))
                    // 简单进度条
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(Color.LightGray, RoundedCornerShape(4.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress / 100f)
                                .height(8.dp)
                                .background(MaterialTheme.colors.primary, RoundedCornerShape(4.dp))
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                if (latestVersionInfo?.forceUpdate != true) {
                    TextButton(onClick = {
                        showUpdateProgressDialog = false
                        GlobalAppUpdateManager.cancelDownload()
                    }) {
                        Text("取消")
                    }
                }
            }
        )
    }
}

/**
 * 格式化文件大小
 */
private fun formatFileSize(size: Long): String {
    return when {
        size <= 0 -> "未知"
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0)
        size < 1024 * 1024 * 1024 -> "%.1f MB".format(size / (1024.0 * 1024))
        else -> "%.1f GB".format(size / (1024.0 * 1024 * 1024))
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
