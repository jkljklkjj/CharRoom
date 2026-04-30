package com.chatlite.charroom

import android.app.Application
import core.ServerConfig

class ChatApplication : Application() {
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "chat_service_channel"
        const val NOTIFICATION_ID = 1
    }

    lateinit var chatNotificationManager: ChatNotificationManager
        private set

    override fun onCreate() {
        super.onCreate()
        chatNotificationManager = ChatNotificationManager(this)
        // 注册应用生命周期观察者
        AppLifecycleObserver.register(this)
        // 初始化全局配置（ServerConfig会自动从资源文件加载配置）
    }
}
