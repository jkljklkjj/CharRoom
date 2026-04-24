package com.chatlite.charroom

import android.app.Application
import core.addMessageReceiveListener

/**
 * 应用全局入口
 */
class ChatApplication : Application() {
    lateinit var notificationManager: ChatNotificationManager
        private set

    override fun onCreate() {
        super.onCreate()

        // 注册生命周期观察者
        AppLifecycleObserver.register(this)

        // 初始化通知管理器
        notificationManager = ChatNotificationManager(this)

        // 注册消息接收监听器
        addMessageReceiveListener(notificationManager)
    }

    override fun onTerminate() {
        super.onTerminate()
        AppLifecycleObserver.unregister(this)
    }
}
