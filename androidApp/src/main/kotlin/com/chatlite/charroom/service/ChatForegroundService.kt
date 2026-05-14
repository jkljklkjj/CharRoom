package com.chatlite.charroom.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.chatlite.charroom.ChatApplication
import com.chatlite.charroom.R
import com.chatlite.charroom.core.ChatNotificationManager
import core.Chat

class ChatForegroundService : Service() {
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        // 确保通知渠道已创建
        createNotificationChannel()
    }

    /**
     * 创建前台服务需要的通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ChatNotificationManager.CHANNEL_ID_SERVICE,
                "聊天服务",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持后台聊天连接"
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
            }
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            return START_NOT_STICKY
        }

        // 创建前台服务通知
        val notificationIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, ChatApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("chatlite")
            .setContentText("聊天服务正在运行")
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFD78345.toInt())
            .setColorized(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()

        // 启动前台服务，类型由 AndroidManifest 中的 foregroundServiceType 决定
        startForeground(ChatApplication.NOTIFICATION_ID, notification)

        // 连接已由UI层(ChatApp)启动，服务仅负责保活和重连
        // 无需重复调用Chat.start()，避免重复握手

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        try {
            Chat.logoutAndDisconnect()
        } catch (_: Exception) {
            // 忽略断开异常
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, ChatForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ChatForegroundService::class.java)
            context.stopService(intent)
        }
    }
}
