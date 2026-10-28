package core

/**
 * 应用全局配置
 */
object AppConfig {
    /**
     * 应用版本号（整数，用于比较）
     */
    const val VERSION_CODE = BuildReleaseVersion.CODE

    /**
     * 应用版本名称（显示用，如"1.0.0"）
     */
    const val VERSION_NAME = BuildReleaseVersion.NAME

    /**
     * 应用名称
     */
    const val APP_NAME = "轻聊"

    /**
     * 是否为调试版本（生产环境应设为 false）
     */
    val DEBUG: Boolean = System.getProperty("app.debug", "true").toBoolean()
}
