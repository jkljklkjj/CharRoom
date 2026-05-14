package core

import core.model.AppVersionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用更新状态
 */
sealed class UpdateState {
    /**
     * 初始状态
     */
    object Idle : UpdateState()

    /**
     * 正在检查更新
     */
    object Checking : UpdateState()

    /**
     * 有新版本可用
     */
    data class Available(val versionInfo: AppVersionInfo) : UpdateState()

    /**
     * 已经是最新版本
     */
    object NoUpdate : UpdateState()

    /**
     * 正在下载更新
     */
    data class Downloading(val progress: Int, val total: Long) : UpdateState()

    /**
     * 下载完成，等待安装
     */
    data class Downloaded(val filePath: String, val versionInfo: AppVersionInfo) : UpdateState()

    /**
     * 正在安装
     */
    object Installing : UpdateState()

    /**
     * 更新失败
     */
    data class Failed(val error: String) : UpdateState()
}

/**
 * 应用更新管理器接口
 * 各平台需要实现具体的下载和安装逻辑
 */
expect class AppUpdateManager {
    /**
     * 更新状态流
     */
    val updateState: StateFlow<UpdateState>

    /**
     * 检查更新
     * @param platform 平台：android/desktop
     * @param channel 渠道
     * @param autoDownload 是否自动下载更新
     */
    suspend fun checkForUpdates(
        platform: String,
        channel: String = "official",
        autoDownload: Boolean = false
    )

    /**
     * 下载更新
     * @param versionInfo 要下载的版本信息
     */
    suspend fun downloadUpdate(versionInfo: AppVersionInfo)

    /**
     * 安装更新
     * @param filePath 安装包路径
     */
    suspend fun installUpdate(filePath: String)

    /**
     * 取消下载
     */
    fun cancelDownload()
}

/**
 * 全局应用更新管理器实例
 */
expect val GlobalAppUpdateManager: AppUpdateManager