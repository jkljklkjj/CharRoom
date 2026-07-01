package com.chatlite.charroom

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.chatlite.charroom.component.AndroidBackHandler
import com.chatlite.charroom.component.AndroidFilePicker
import com.chatlite.charroom.core.AndroidImageLoader
import component.settings.CurrentPlatform
import component.settings.Platform
import com.chatlite.charroom.core.NetworkChangeReceiver
import com.chatlite.charroom.service.ChatForegroundService
import com.chatlite.charroom.ui.theme.ChatTheme
import com.chatlite.charroom.data.datasource.local.AndroidTokenStorage
import component.BackHandlerImpl
import component.io.FilePicker
import core.GlobalAppUpdateManager
import core.ImageLoaderImpl
import com.chatlite.charroom.core.initAppUpdateManager
import androidx.core.graphics.toColorInt
import org.koin.androidx.compose.koinViewModel
import presentation.viewmodel.AuthViewModel
import com.chatlite.charroom.presentation.viewmodel.AndroidAuthViewModel
import com.chatlite.charroom.presentation.viewmodel.AndroidChatViewModel
import core.state.AuthState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 权限处理
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
            if (!notificationGranted) {
                // 通知权限被拒绝，可以在这里显示提示引导用户去设置中开启
                Timber.w("通知权限被拒绝，新消息通知将无法显示")
            }
        }
    }

    /**
     * 检查是否有通知权限
     */
    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            // Android 13以下默认有通知权限
            true
        }
    }

    // 全局返回键处理状态
    private var onBackPressedCallback: (() -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化跨平台接口实现
        BackHandlerImpl = AndroidBackHandler
        FilePicker = AndroidFilePicker
        ImageLoaderImpl = AndroidImageLoader

        // 初始化平台信息
        CurrentPlatform = Platform.ANDROID

        // 初始化应用更新管理器
        initAppUpdateManager(this)

        // 启动时自动检查更新
        lifecycleScope.launch {
            try {
                GlobalAppUpdateManager.checkForUpdates(
                    platform = "android",
                    autoDownload = false
                )
            } catch (_: Exception) {
                // 忽略更新检查错误
            }
        }

        // 沉浸式状态栏，内容延伸到状态栏下
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        @Suppress("DEPRECATION")
        window.statusBarColor = "#D78345".toColorInt()
        @Suppress("DEPRECATION")
        window.navigationBarColor = "#D78345".toColorInt()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // 启动前台服务保活
        try {
            ChatForegroundService.start(this)
        } catch (e: Exception) {
            Timber.e(e, "启动前台服务失败")
        }

        // 注册返回键回调，优先级高于系统默认
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 先尝试让Compose层处理返回
                val handled = onBackPressedCallback?.invoke() ?: false
                if (!handled) {
                    // 没有处理，执行默认返回（退出应用）
                    finish()
                }
            }
        })

        // 请求必要权限
        requestNecessaryPermissions()

        // 注册网络状态回调
        NetworkChangeReceiver.registerNetworkCallback(this)

        // 处理通知点击意图
        handleNotificationIntent(intent)

        setContent {
            // 注册文件选择器
            FilePicker.Register()

            ChatTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                // 通过依赖注入获取ViewModel
                val androidAuthViewModel = koinViewModel<AndroidAuthViewModel>()
                val authViewModel = androidAuthViewModel.viewModel
                val chatViewModel = org.koin.core.context.GlobalContext.get().get<AndroidChatViewModel>()

                // 观察认证状态
                val authState by authViewModel.authState.collectAsState()

                LaunchedEffect(Unit) {
                    // 初始化ViewModel，尝试自动登录
                    authViewModel.init()

                    // 监听认证状态变化
                    authViewModel.authState.collect { state ->
                        when (state) {
                            is AuthState.Authenticated -> {
                                // 登录成功，保存token到本地
                                val userId = state.account.toIntOrNull() ?: 0
                                AndroidTokenStorage.save(
                                    context,
                                    state.accessToken,
                                    userId,
                                    state.refreshToken
                                )
                            }
                            is AuthState.Unauthenticated,
                            is AuthState.Error -> {
                                // 未登录或认证失败，清除本地存储
                                AndroidTokenStorage.clear(context)
                                chatViewModel.clear()
                            }
                            else -> {}
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 使用commonMain的LoginRegisterApp，统一登录注册逻辑
                    component.LoginRegisterApp(
                        isDarkMode = false, // 后续可以添加主题切换
                        onToggleDarkMode = {},
                        onBackPressed = { callback ->
                            onBackPressedCallback = callback
                        }
                    )
                }
            }
        }
    }

    private fun requestNecessaryPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // 应用回到前台，清除所有通知
        val app = application as ChatApplication
        app.chatNotificationManager.cancelAllNotifications()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            // 注销网络状态回调
            NetworkChangeReceiver.unregisterNetworkCallback(this)
            // 停止前台服务
            ChatForegroundService.stop(this)
        } catch (e: Exception) {
            Timber.e(e, "onDestroy 清理失败")
        }
    }

    /**
     * 处理通知点击意图
     */
    private fun handleNotificationIntent(intent: Intent) {
        val notificationType = intent.getStringExtra("NOTIFICATION_TYPE") ?: return
        val userId = intent.getIntExtra("USER_ID", -1)
        val groupId = intent.getIntExtra("GROUP_ID", -1)

        // 这里可以根据通知类型跳转到对应的聊天界面
        // 目前暂时只清除通知，后续可以集成到导航逻辑中
        val app = application as ChatApplication
        when (notificationType) {
            "private" -> {
                if (userId != -1) {
                    app.chatNotificationManager.cancelPrivateNotification(userId)
                }
            }
            "group" -> {
                if (groupId != -1) {
                    app.chatNotificationManager.cancelGroupNotification(groupId)
                }
            }
        }
    }
}

