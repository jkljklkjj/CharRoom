package com.chatlite.charroom

import android.app.Application
import android.util.Log
import com.chatlite.charroom.core.AppLifecycleObserver
import com.chatlite.charroom.core.ChatNotificationManager
import com.chatlite.charroom.data.network.AndroidGlobalWebSocketClient
import com.chatlite.charroom.data.datasource.local.AndroidLocalChatHistoryStore
import com.chatlite.charroom.data.datasource.local.AndroidLocalDataSourceImpl
import core.LocalChatHistoryStore
import data.repository.AuthRepository
import data.repository.GlobalAuthRepository
import data.datasource.remote.RemoteDataSourceImpl
import core.Chat
import com.chatlite.charroom.core.AndroidImageLoader
import component.dialog.AvatarCropDialogImpl
import com.chatlite.charroom.component.AndroidAvatarCropDialog
import core.di.KoinInitializer
import com.chatlite.charroom.di.androidModule
import org.koin.android.ext.koin.androidContext
import timber.log.Timber

class ChatApplication : Application() {
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "chat_service_channel"
        const val NOTIFICATION_ID = 1
    }

    val chatNotificationManager: ChatNotificationManager by lazy {
        ChatNotificationManager(this)
    }

    override fun onCreate() {
        super.onCreate()

        // 初始化依赖注入
        KoinInitializer.init {
            androidContext(this@ChatApplication)
            modules(androidModule)
        }

        // 提前初始化通知管理器，确保在需要时已经可用
        chatNotificationManager
        // 注册应用生命周期观察者，仅在完全关闭程序时断开WebSocket连接
        AppLifecycleObserver.register(this)
        // 初始化本地存储
        AndroidLocalChatHistoryStore.init(this)
        LocalChatHistoryStore = AndroidLocalChatHistoryStore

        // 替换为Android版LocalDataSource，实现token持久化
        val androidLocalDataSource = AndroidLocalDataSourceImpl(this)
        // 反射修改GlobalAuthRepository的localDataSource字段
        try {
            val field = AuthRepository::class.java.getDeclaredField("localDataSource")
            field.isAccessible = true
            field.set(GlobalAuthRepository, androidLocalDataSource)
            println("[ChatApplication] 已替换为Android本地数据源实现")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // 初始化图片加载器
        AndroidImageLoader.init(this)
        // 初始化头像裁剪对话框
        AvatarCropDialogImpl = AndroidAvatarCropDialog
        // 初始化 commonMain 全局 WebSocket 客户端实现
        Chat = AndroidGlobalWebSocketClient
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

    override fun onTerminate() {
        super.onTerminate()
        // 注销生命周期观察者
        AppLifecycleObserver.unregister(this)
        // 销毁Koin
        KoinInitializer.destroy()
    }
}
