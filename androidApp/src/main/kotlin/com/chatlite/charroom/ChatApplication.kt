package com.chatlite.charroom

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import core.ServerConfig

class ChatApplication : Application() {
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "chat_service_channel"
        const val NOTIFICATION_ID = 1
    }

    lateinit var notificationManager: NotificationManager
        private set

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
        // 初始化全局配置（ServerConfig会自动从资源文件加载配置）
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Chat Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground service for chat connection"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
