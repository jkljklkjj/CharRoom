package core

import com.chatlite.charroom.core.AndroidAppUpdateManager
import android.content.Context

/**
 * Android端全局应用更新管理器实例
 */
lateinit var GlobalAppUpdateManager: AppUpdateManager
    private set

/**
 * 初始化Android应用更新管理器
 */
fun initAppUpdateManager(context: Context) {
    GlobalAppUpdateManager = AndroidAppUpdateManager(context)
}