package com.chatlite.charroom

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.content.Context

class ChatForegroundService : Service() {
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
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
            .setContentTitle("CharRoom")
            .setContentText("聊天服务正在运行")
            .setSmallIcon(android.R.drawable.ic_dialog_email) // 临时使用系统图标，后续替换为自定义图标
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()

        // 启动前台服务
        startForeground(ChatApplication.NOTIFICATION_ID, notification)

        // 保持WebSocket连接（功能待实现，需要关联全局WebSocket客户端）
        // ChatClient.ensureConnected()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        // 断开WebSocket连接（功能待实现）
        // ChatClient.disconnect()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, ChatForegroundService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ChatForegroundService::class.java)
            context.stopService(intent)
        }
    }
}
