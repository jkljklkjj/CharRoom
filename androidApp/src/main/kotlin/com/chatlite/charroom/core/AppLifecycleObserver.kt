package com.chatlite.charroom.core

import android.app.Activity
import android.app.Application
import android.os.Bundle
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

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        activityCount++
        updateForegroundState()
    }

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {
        activityCount--
        updateForegroundState()
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