package component.settings

import com.chatlite.i18n.LocalStrings
import com.chatlite.i18n.Strings
import component.AppTopBar
import core.AppLog
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
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
import kotlinx.coroutines.launch
import presentation.viewmodel.ChatViewModel

/**
 * Platform type
 */
enum class Platform {
    ANDROID, DESKTOP, WEB
}

/**
 * Current platform, set by each platform during initialization
 */
lateinit var CurrentPlatform: Platform

/**
 * Settings screen
 */
@Composable
fun SettingsScreen(
    chatViewModel: ChatViewModel,
    onBackClick: () -> Unit,
    onLogout: () -> Unit,
    onProfileClick: () -> Unit = {}
) {
    val s = LocalStrings.current
    val coroutineScope = rememberCoroutineScope()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showUpdateProgressDialog by remember { mutableStateOf(false) }
    var latestVersionInfo: AppVersionInfo? by remember { mutableStateOf(null) }
    var updateError: String? by remember { mutableStateOf(null) }

    // Listen for update state
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
                // Ask whether to install
                if (state.versionInfo.forceUpdate) {
                    // Force update installs directly
                    launch(Dispatchers.IO) {
                        GlobalAppUpdateManager.installUpdate(state.filePath)
                    }
                } else {
                    // Show confirm install dialog
                    showUpdateDialog = true
                }
            }
            is UpdateState.Failed -> {
                showUpdateProgressDialog = false
                // Silent failure, no popup
                updateError = state.error
                AppLog.w({ "Check for update failed: ${state.error}" })
            }
            is UpdateState.NoUpdate -> {
                updateError = s["settings.up.to.date"]
                showUpdateDialog = true
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // Top bar title
        AppTopBar(
            title = s["settings.title"],
            onBack = onBackClick
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Settings options list
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Profile
            SettingItem(
                icon = Icons.Default.Person,
                title = s["settings.profile"],
                subtitle = s["settings.profile.subtitle"],
                onClick = onProfileClick
            )

            // Check for updates
            SettingItem(
                icon = Icons.Default.SystemUpdate,
                title = s["settings.check.update"],
                subtitle = s["settings.version"].format(AppConfig.VERSION_NAME, AppConfig.VERSION_CODE),
                onClick = {
                    // Launch coroutine to check for updates
                    coroutineScope.launch(Dispatchers.IO) {
                        GlobalAppUpdateManager.checkForUpdates(
                            platform = when (CurrentPlatform) {
                                Platform.ANDROID -> "android"
                                Platform.DESKTOP -> "desktop"
                                Platform.WEB -> "web"
                            },
                            autoDownload = false
                        )
                    }
                }
            )

            // Clear chat history
            SettingItem(
                icon = Icons.Default.Delete,
                title = s["settings.clear.history"],
                subtitle = s["settings.clear.history.subtitle"],
                onClick = {
                    showClearHistoryDialog = true
                },
                tint = MaterialTheme.colors.error
            )

            // Clear cache
            SettingItem(
                icon = Icons.Default.Delete,
                title = s["settings.clear.cache"],
                subtitle = s["settings.clear.cache.subtitle"],
                onClick = {
                    showClearCacheDialog = true
                },
                tint = MaterialTheme.colors.error
            )

            // Logout
            SettingItem(
                icon = Icons.Default.Logout,
                title = s["settings.logout"],
                subtitle = s["settings.logout.subtitle"],
                onClick = {
                    showLogoutDialog = true
                },
                tint = MaterialTheme.colors.error
            )
        }
    }

    // Logout confirmation dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(s["settings.logout"]) },
            text = { Text(s["settings.logout.confirm"]) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        // Clear local data
                        chatViewModel.clear()
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.error,
                        contentColor = Color.White
                    )
                ) {
                    Text(s["settings.logout.confirm.button"])
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(s["settings.cancel"])
                }
            }
        )
    }

    // Clear chat history confirmation dialog
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(s["settings.clear.history"]) },
            text = { Text(s["settings.clear.history.confirm"]) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearHistoryDialog = false
                        // Clear local chat history
                        GlobalAppState.currentUserId?.toString()?.let { accountId ->
                            LocalChatHistoryStore.clear(accountId)
                        }
                        chatViewModel.clearMessages()
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.error,
                        contentColor = Color.White
                    )
                ) {
                    Text(s["settings.clear"])
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(s["settings.cancel"])
                }
            }
        )
    }

    // Clear cache confirmation dialog
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text(s["settings.clear.cache"]) },
            text = { Text(s["settings.clear.cache.confirm"]) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCacheDialog = false
                        // Cache clearing logic will be implemented later
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.error,
                        contentColor = Color.White
                    )
                ) {
                    Text(s["settings.clear"])
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text(s["settings.cancel"])
                }
            }
        )
    }

    // Update notification dialog
    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = {
                showUpdateDialog = false
                updateError = null
                if (latestVersionInfo?.forceUpdate == true) {
                    // Force update cannot be dismissed
                    return@AlertDialog
                }
            },
            title = {
                Text(
                    when {
                        updateError != null -> s["settings.update.title"]
                        latestVersionInfo != null -> s["settings.update.found"].format(latestVersionInfo?.versionName ?: "")
                        else -> s["settings.update.checking"]
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
                            Text(s["settings.update.size"].format(formatFileSize(s, version.fileSize)))
                            if (version.releaseTime != null) {
                                Text(s["settings.update.release.time"].format(version.releaseTime))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(s["settings.update.content"].format(version.updateContent))
                            if (version.forceUpdate) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(s["settings.update.force"], color = MaterialTheme.colors.error)
                            }
                        }
                    }
                    else -> {
                        Text(s["settings.update.checking.dots"])
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
                            Text(s["settings.confirm"])
                        }
                    }
                    latestVersionInfo != null -> {
                        val version = latestVersionInfo!!
                        when (updateState) {
                            is UpdateState.Downloaded -> {
                                Button(
                                    onClick = {
                                        showUpdateDialog = false
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val filePath = (updateState as UpdateState.Downloaded).filePath
                                            GlobalAppUpdateManager.installUpdate(filePath)
                                        }
                                    }
                                ) {
                                    Text(s["settings.update.install"])
                                }
                            }
                            else -> {
                                Button(
                                    onClick = {
                                        showUpdateDialog = false
                                        coroutineScope.launch(Dispatchers.IO) {
                                            GlobalAppUpdateManager.downloadUpdate(version)
                                        }
                                    }
                                ) {
                                    Text(if (version.forceUpdate) s["settings.update.force.update"] else s["settings.update.now"])
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
                        Text(s["settings.update.later"])
                    }
                }
            }
        )
    }

    // Update progress dialog
    if (showUpdateProgressDialog) {
        val downloadState = updateState as? UpdateState.Downloading
        AlertDialog(
            onDismissRequest = {
                if (latestVersionInfo?.forceUpdate != true) {
                    showUpdateProgressDialog = false
                    GlobalAppUpdateManager.cancelDownload()
                }
            },
            title = { Text(s["settings.update.downloading"]) },
            text = {
                Column {
                    val progress = downloadState?.progress ?: 0
                    val total = downloadState?.total ?: 0
                    Text(s["settings.update.downloaded"].format(progress.toInt(), formatFileSize(s, progress.toLong() * total / 100), formatFileSize(s, total)))
                    Spacer(modifier = Modifier.height(8.dp))
                    // Simple progress bar
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
                        Text(s["settings.cancel"])
                    }
                }
            }
        )
    }
}

/**
 * Format file size to human-readable string
 */
private fun formatFileSize(s: Strings, size: Long): String {
    return when {
        size <= 0 -> s["settings.file.size.unknown"]
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0)
        size < 1024 * 1024 * 1024 -> "%.1f MB".format(size / (1024.0 * 1024))
        else -> "%.1f GB".format(size / (1024.0 * 1024 * 1024))
    }
}

/**
 * Settings item component
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
            // Icon
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
