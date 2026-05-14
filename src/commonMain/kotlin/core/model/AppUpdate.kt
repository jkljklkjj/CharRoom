package core.model

import kotlinx.serialization.Serializable

/**
 * 应用版本信息
 */
@Serializable
data class AppVersionInfo(
    /**
     * 版本号（整数，用于比较）
     */
    val versionCode: Int,
    /**
     * 版本名称（显示用，如"1.0.0"）
     */
    val versionName: String,
    /**
     * 更新内容描述
     */
    val updateContent: String,
    /**
     * 下载地址
     */
    val downloadUrl: String,
    /**
     * 是否强制更新
     */
    val forceUpdate: Boolean = false,
    /**
     * 文件大小（字节）
     */
    val fileSize: Long = 0,
    /**
     * 文件MD5校验值
     */
    val md5: String? = null,
    /**
     * 发布时间
     */
    val releaseTime: String? = null
)

/**
 * 版本检查结果
 */
@Serializable
data class VersionCheckResult(
    /**
     * 是否有新版本
     */
    val hasUpdate: Boolean,
    /**
     * 最新版本信息
     */
    val latestVersion: AppVersionInfo? = null,
    /**
     * 更新提示信息
     */
    val message: String? = null
)