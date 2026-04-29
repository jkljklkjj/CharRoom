package com.chatlite.charroom

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import core.initKermit

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 权限处理
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
            if (notificationGranted) {
                // 通知权限已授予
            }
        }
    }

    // 全局返回键处理状态
    private var canGoBack = false
    private var onBackPressedCallback: (() -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 沉浸式状态栏，内容延伸到状态栏下
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        initKermit()

        // 暂时禁用前台服务，避免权限问题
        // try {
        //     ChatForegroundService.start(this)
        // } catch (e: Exception) {
        //     e.printStackTrace()
        // }

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
            MaterialTheme {
                // 使用systemBarsPadding自动适配状态栏和导航栏边距
                androidx.compose.foundation.Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                ) {
                    App(
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleNotificationIntent(it) }
    }

    override fun onResume() {
        super.onResume()
        // 应用回到前台，清除所有通知
        val app = application as ChatApplication
        app.notificationManager.cancelAllNotifications()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            ChatForegroundService.stop(this)
        } catch (e: Exception) {
            e.printStackTrace()
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
                    app.notificationManager.cancelPrivateNotification(userId)
                }
            }
            "group" -> {
                if (groupId != -1) {
                    app.notificationManager.cancelGroupNotification(groupId)
                }
            }
        }
    }
}

