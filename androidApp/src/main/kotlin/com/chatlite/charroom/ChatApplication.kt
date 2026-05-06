package com.chatlite.charroom

import android.app.Application
import android.util.Log
import core.LocalChatHistoryStore
import core.ServerConfig
import component.AvatarCropDialogImpl
import component.AndroidAvatarCropDialog
import timber.log.Timber

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
        // 初始化本地存储
        AndroidLocalChatHistoryStore.init(this)
        LocalChatHistoryStore = AndroidLocalChatHistoryStore
        // 初始化头像裁剪对话框
        AvatarCropDialogImpl = AndroidAvatarCropDialog
        // 初始化日志
        if (BuildConfig.DEBUG) {
            Timber.plant(object : Timber.DebugTree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    // 添加调用位置信息：类名:方法名:行号
                    val stackTrace = Throwable().stackTrace
                    // 调整栈深度以适配AppLog -> KLogging -> slf4j-timber的调用层级
                    for (element in stackTrace) {
                        val className = element.className
                        // 跳过日志框架相关的类，找到实际调用AppLog的类
                        if (!className.startsWith("core.AppLog") &&
                            !className.startsWith("io.github.oshai.kotlinlogging") &&
                            !className.startsWith("org.slf4j") &&
                            !className.startsWith("com.arcao.slf4j_timber") &&
                            !className.startsWith("timber.log.Timber")) {
                            val simpleClassName = className.substringAfterLast('.')
                            val methodName = element.methodName
                            val lineNumber = element.lineNumber
                            val newMessage = "($simpleClassName:$methodName:$lineNumber) $message"
                            super.log(priority, tag, newMessage, t)
                            return
                        }
                    }
                    // 如果找不到合适的栈帧，直接输出原消息
                    super.log(priority, tag, message, t)
                }
            })
        } else {
            //  release版本可以只记录关键日志到文件
            Timber.plant(object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    if (priority >= Log.INFO) {
                        // 生产环境只打印INFO及以上级别日志
                        super.log(priority, tag, message, t)
                    }
                }
            })
        }
        // 初始化全局配置（ServerConfig会自动从资源文件加载配置）
    }
}
