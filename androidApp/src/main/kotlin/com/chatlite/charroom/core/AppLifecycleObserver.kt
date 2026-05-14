package com.chatlite.charroom.core

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import com.chatlite.charroom.presentation.viewmodel.AndroidChatViewModel
import timber.log.Timber

/**
 * 应用生命周期观察者
 * 用于判断应用是否在前台运行，仅在完全退出时断开连接
 */
object AppLifecycleObserver : Application.ActivityLifecycleCallbacks {
    var isAppInForeground = false
        private set

    private lateinit var application: Application
    private var activityCount = 0
    private val APP_QUIT_DELAY = 30000L // 退出延迟30秒，避免锁屏误判
    private var quitCheckJob: kotlinx.coroutines.Job? = null

    /**
     * 检查前台服务是否正在运行
     */
    private fun isForegroundServiceRunning(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        for (service in activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (com.chatlite.charroom.service.ChatForegroundService::class.java.name == service.service.className) {
                return service.foreground
            }
        }
        return false
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        activityCount++
        updateForegroundState()
        // 取消退出检测
        quitCheckJob?.cancel()
        quitCheckJob = null
    }

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {
        activityCount--
        updateForegroundState()

        // 如果所有Activity都销毁了，启动退出检测
        if (activityCount == 0) {
            quitCheckJob = CoroutineScope(Dispatchers.IO).launch {
                delay(APP_QUIT_DELAY)
                // 延迟后仍然没有Activity，且前台服务未运行，说明用户真的退出App了
                if (activityCount == 0 && !isForegroundServiceRunning(application)) {
                    Timber.i("应用完全退出，执行清理流程")
                    try {
                        // 通过Koin获取AndroidChatViewModel执行清理
                        val chatViewModel: AndroidChatViewModel? = GlobalContext.get().getOrNull()
                        chatViewModel?.onAppQuit()
                    } catch (e: Exception) {
                        Timber.e(e, "应用退出清理失败")
                    }
                }
            }
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}

    private fun updateForegroundState() {
        isAppInForeground = activityCount > 0
    }

    /**
     * 注册生命周期观察者
     */
    fun register(application: Application) {
        this.application = application
        application.registerActivityLifecycleCallbacks(this)
    }

    /**
     * 注销生命周期观察者
     */
    fun unregister(application: Application) {
        application.unregisterActivityLifecycleCallbacks(this)
    }
}