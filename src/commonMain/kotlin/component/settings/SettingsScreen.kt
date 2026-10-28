package component.settings

import com.chatlite.i18n.LocalStrings
import com.chatlite.i18n.Strings
import component.AppTopBar
import core.AppLog
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.RadioButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
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
import core.AppSettings
import core.GlobalAppUpdateManager
import core.LocalChatHistoryStore
import core.ThemeMode
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
    onProfileClick: () -> Unit = {},
    onQuotaClick: () -> Unit = {}
) {
    val s = LocalStrings.current
    val coroutineScope = rememberCoroutineScope()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
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
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LocalPlatformSettings.current.invoke()
            // Profile
            SettingItem(
                icon = Icons.Default.Person,
                title = s["settings.profile"],
                subtitle = s["settings.profile.subtitle"],
                onClick = onProfileClick
            )

            // Token Quota
            SettingItem(
                icon = Icons.Default.Star,
                title = s["settings.token.quota"],
                subtitle = s["settings.token.quota.subtitle"],
                onClick = onQuotaClick
            )

            // Theme
            val themeMode by AppSettings.themeMode.collectAsState()
            SettingItem(
                icon = Icons.Default.Palette,
                title = s["settings.theme"],
                subtitle = s["settings.theme.subtitle"].format(
                    when (themeMode) {
                        ThemeMode.SYSTEM -> s["settings.theme.follow.system"]
                        ThemeMode.LIGHT -> s["settings.theme.light"]
                        ThemeMode.DARK -> s["settings.theme.dark"]
                    }
                ),
                onClick = { showThemeDialog = true }
            )

            // Language
            val language by AppSettings.language.collectAsState()
            val effectiveLanguage = AppSettings.getEffectiveLanguage()
            SettingItem(
                icon = Icons.Default.Language,
                title = s["settings.language"],
                subtitle = s["settings.language.subtitle"].format(
                    when (effectiveLanguage) {
                        "zh" -> s["settings.language.zh"]
                        "en" -> s["settings.language.en"]
                        "ja" -> s["settings.language.ja"]
                        else -> s["settings.language.en"]
                    }
                ),
                onClick = { showLanguageDialog = true }
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
                icon = Icons.AutoMirrored.Filled.Logout,
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

    // ── Theme selection dialog ──
    if (showThemeDialog) {
        val currentTheme by AppSettings.themeMode.collectAsState()
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(s["settings.theme"]) },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        val label = when (mode) {
                            ThemeMode.SYSTEM -> s["settings.theme.follow.system"]
                            ThemeMode.LIGHT -> s["settings.theme.light"]
                            ThemeMode.DARK -> s["settings.theme.dark"]
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    AppSettings.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentTheme == mode,
                                onClick = {
                                    AppSettings.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.body1
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(s["settings.confirm"])
                }
            }
        )
    }

    // ── Language selection dialog ──
    if (showLanguageDialog) {
        val currentLang by AppSettings.language.collectAsState()
        val langOptions = listOf(
            "zh" to s["settings.language.zh"],
            "en" to s["settings.language.en"],
            "ja" to s["settings.language.ja"]
        )
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(s["settings.language"]) },
            text = {
                Column {
                    langOptions.forEach { (code, displayName) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    AppSettings.setLanguage(code)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentLang == code,
                                onClick = {
                                    AppSettings.setLanguage(code)
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.body1
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(s["settings.confirm"])
                }
            }
        )
    }
}

/**
 * Format file size to human-readable string
 */


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
