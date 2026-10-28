package com.chatlite.charroom

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.chatlite.charroom.component.AndroidBackHandler
import com.chatlite.charroom.component.AndroidConnectivityBanner
import com.chatlite.charroom.component.AndroidFilePicker
import com.chatlite.charroom.component.rememberNotificationPreferences
import com.chatlite.charroom.core.AndroidImageLoader
import com.chatlite.charroom.core.NotificationDestination
import com.chatlite.charroom.core.initAppUpdateManager
import com.chatlite.charroom.data.datasource.local.AndroidTokenStorage
import com.chatlite.charroom.presentation.viewmodel.AndroidAuthViewModel
import com.chatlite.charroom.presentation.viewmodel.AndroidChatViewModel
import com.chatlite.charroom.service.ChatForegroundService
import com.chatlite.charroom.ui.theme.ChatTheme
import com.chatlite.i18n.LocalStrings
import com.chatlite.i18n.currentStrings
import com.chatlite.i18n.stringsForLocale
import component.BackHandlerImpl
import component.io.FilePicker
import component.settings.CurrentPlatform
import component.settings.LocalPlatformSettings
import component.settings.Platform
import core.AppSettings
import core.Chat
import core.ImageLoaderImpl
import core.ThemeMode
import core.state.AuthState
import core.state.GlobalAppState
import kotlinx.coroutines.flow.MutableStateFlow
import model.ChatNavigationRequest
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber

class MainActivity : ComponentActivity() {
    private var uiBack: (() -> Boolean)? = null
    private data class OpenNotification(val destination: NotificationDestination, val requestId: Long)
    private val pendingNotification = MutableStateFlow<OpenNotification?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BackHandlerImpl = AndroidBackHandler
        FilePicker = AndroidFilePicker
        ImageLoaderImpl = AndroidImageLoader
        CurrentPlatform = Platform.ANDROID
        enableEdgeToEdge()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (ViewCompat.getRootWindowInsets(window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true) {
                    WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
                    return
                }
                if (uiBack?.invoke() != true) moveTaskToBack(true)
            }
        })
        val restored = savedInstanceState?.let {
            NotificationDestination.parse(it.getString("pending.type"), it.getInt("pending.target"), it.getInt("pending.account"))
        }
        if (restored != null) pendingNotification.value = OpenNotification(restored, savedInstanceState!!.getLong("pending.request"))
        else handleNotificationIntent(intent)

        setContent {
            val theme by AppSettings.themeMode.collectAsState()
            val language by AppSettings.language.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val dark = when (theme) { ThemeMode.SYSTEM -> systemDark; ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
            // Configuration changes also update the system locale when following the device.
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val strings = remember(language, configuration) { stringsForLocale(AppSettings.getEffectiveLanguage()) }
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(0xE6FFFFFF.toInt(), 0x801B1B1B.toInt()) { dark }
                )
                com.chatlite.i18n.updateCurrentStrings(language)
            }
            CompositionLocalProvider(LocalStrings provides strings) {
                ChatTheme(darkTheme = dark) {
                    FilePicker.Register()
                    val auth = koinViewModel<AndroidAuthViewModel>().viewModel
                    val chat = org.koin.core.context.GlobalContext.get().get<AndroidChatViewModel>()
                    val state by auth.authState.collectAsState()
                    val selected by chat.selectedChatTargetFlow.collectAsState()
                    val pending by pendingNotification.collectAsState()
                    val account = (state as? AuthState.Authenticated)?.userProfile?.id ?: GlobalAppState.currentUserId
                    var hadSession by remember { mutableStateOf(false) }
                    val platformSettings = rememberNotificationPreferences(state is AuthState.Authenticated)
                    LaunchedEffect(state) {
                        when (val value = state) {
                            is AuthState.Authenticated -> {
                                hadSession = true
                                val id = value.userProfile?.id ?: GlobalAppState.currentUserId ?: value.account.toIntOrNull()
                                if (id != null && id > 0) AndroidTokenStorage.save(this@MainActivity, value.accessToken, id, value.refreshToken)
                                startChatService()
                            }
                            is AuthState.Unauthenticated, is AuthState.Error -> {
                                uiBack = null
                                if (hadSession) {
                                    hadSession = false
                                    pendingNotification.value = null
                                    ChatForegroundService.stop(this@MainActivity)
                                    Chat.stop()
                                    AndroidTokenStorage.clear(this@MainActivity)
                                    chat.clear()
                                    (application as ChatApplication).chatNotificationManager.cancelAllNotifications()
                                }
                            }
                            else -> Unit
                        }
                    }
                    LaunchedEffect(pending?.requestId, account, state is AuthState.Authenticated) {
                        val request = pending ?: return@LaunchedEffect
                        if (state is AuthState.Authenticated && account != null && !request.destination.belongsTo(account)) {
                            pendingNotification.value = null
                            android.widget.Toast.makeText(this@MainActivity, strings["android.notification.wrongAccount"], android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                    LaunchedEffect(selected?.id, account) { cancelVisibleConversation() }
                    CompositionLocalProvider(LocalPlatformSettings provides platformSettings) {
                        Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background).safeDrawingPadding().imePadding()) {
                            if (state is AuthState.Authenticated) AndroidConnectivityBanner()
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                component.LoginRegisterApp(
                                    isDarkMode = dark,
                                    onToggleDarkMode = { AppSettings.setThemeMode(if (dark) ThemeMode.LIGHT else ThemeMode.DARK) },
                                    onBackPressed = { uiBack = it },
                                    authViewModel = auth,
                                    navigationRequest = pending?.takeIf { state is AuthState.Authenticated && it.destination.belongsTo(account) }?.let { ChatNavigationRequest(it.requestId, it.destination.conversation) },
                                    onNavigationHandled = { if (pendingNotification.value?.requestId == pending?.requestId) pendingNotification.value = null }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingNotification.value?.let {
            outState.putString("pending.type", it.destination.type)
            outState.putInt("pending.target", it.destination.targetId)
            outState.putInt("pending.account", it.destination.accountId)
            outState.putLong("pending.request", it.requestId)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (!GlobalAppState.currentToken.isNullOrBlank()) startChatService()
        cancelVisibleConversation()
    }

    private fun startChatService() {
        try { ChatForegroundService.start(this) }
        catch (failure: RuntimeException) { Timber.w(failure, "Background chat service could not start") }
    }

    private fun cancelVisibleConversation() {
        if (GlobalAppState.currentToken.isNullOrBlank()) return
        val selected = core.state.GlobalChatState.selectedChatTarget.value ?: return
        val manager = (application as ChatApplication).chatNotificationManager
        if (selected.isGroup) manager.cancelGroupNotification(selected.id) else manager.cancelPrivateNotification(selected.id)
    }

    private fun handleNotificationIntent(intent: Intent) {
        val type = intent.getStringExtra("NOTIFICATION_TYPE") ?: return
        val id = intent.getIntExtra(if (type == "group") "GROUP_ID" else "USER_ID", 0)
        val destination = NotificationDestination.parse(type, id, intent.getIntExtra("ACCOUNT_ID", 0))
        intent.removeExtra("NOTIFICATION_TYPE")
        if (destination != null) pendingNotification.value = OpenNotification(destination, System.nanoTime())
    }
}
