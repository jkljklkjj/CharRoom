package core

/**
 * 应用日志封装
 * 简单的 println 包装，后续可替换为 Kermit 或其他日志框架
 */
object AppLogger {
    fun d(message: String) {
        // Debug 级别，生产环境可通过构建配置移除
        println("[DEBUG] $message")
    }

    fun i(message: String) {
        println("[INFO] $message")
    }

    fun w(message: String) {
        println("[WARN] $message")
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            println("[ERROR] $message: ${throwable.message}")
        } else {
            println("[ERROR] $message")
        }
    }
}
