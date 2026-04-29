package com.chatlite.charroom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import core.Chat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 聊天前台服务
 * 用于保持后台WebSocket连接，适配省电模式和后台运行限制
 */
class ChatForegroundService : Service() {
    private val CHANNEL_ID = "chat_service_channel"
    private val NOTIFICATION_ID = 1001

    // 省电模式相关
    private var powerManager: PowerManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var powerSaverJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initPowerManager()
        startPowerSaverMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        // Android 14+ 明确指定前台服务类型为数据同步
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // 确保WakeLock在服务启动时获取
        acquireWakeLock()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        powerSaverJob?.cancel()
    }

    /**
     * 初始化电源管理器
     */
    private fun initPowerManager() {
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    /**
     * 获取WakeLock，在省电模式下保持部分唤醒
     */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return

        try {
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ChatApp::WakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // 持有10分钟，会自动释放
            }
        } catch (_: SecurityException) {
            // 没有WAKE_LOCK权限，忽略
        }
    }

    /**
     * 释放WakeLock
     */
    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
            // 忽略释放错误
        } finally {
            wakeLock = null
        }
    }

    /**
     * 启动省电模式监控
     * 监控系统省电模式状态，动态调整心跳和重连策略
     */
    private fun startPowerSaverMonitor() {
        powerSaverJob = serviceScope.launch {
            while (isActive) {
                val isPowerSaveMode = powerManager?.isPowerSaveMode == true

                // 根据省电模式调整心跳间隔
                if (isPowerSaveMode) {
                    // 省电模式下延长心跳间隔到2分钟
                    // 这里可以根据需要调整Chat内部的心跳参数
                    // 目前Chat的心跳间隔是30秒，省电模式下可以动态调整
                }

                // 每分钟检查一次省电模式状态
                delay(60 * 1000L)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "聊天服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持后台聊天连接"
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("轻聊")
            .setContentText("正在后台运行")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(Intent(context, ChatForegroundService::class.java))
            } else {
                context.startService(Intent(context, ChatForegroundService::class.java))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ChatForegroundService::class.java))
        }
    }
}