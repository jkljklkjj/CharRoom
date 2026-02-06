package core

// Shared logging wrapper that doesn't require Kermit types at compile-time.
// Platform code should call `initKermit()` and inside it call `AppLog.setLogger(...)` with
// an implementation of LoggerAdapter that forwards to a platform logger (Kermit, SLF4J, Logcat, etc.).

// Simple, small logging level enum
enum class Level { DEBUG, INFO, WARN, ERROR }

interface LoggerAdapter {
    fun withTag(tag: String): LoggerAdapter
    fun log(level: Level, tag: String?, message: String, throwable: Throwable? = null)
}

object AppLog {
    @Volatile
    private var adapter: LoggerAdapter? = null

    fun setLogger(a: LoggerAdapter) {
        adapter = a
    }

    // Mark as published API so public inline functions can reference it safely when inlined
    @PublishedApi
    internal val loggerOrFallback: LoggerAdapter
        get() = adapter ?: fallbackAdapter

    // Keep the actual fallback private
    private val fallbackAdapter = object : LoggerAdapter {
        override fun withTag(tag: String): LoggerAdapter = this
        override fun log(level: Level, tag: String?, message: String, throwable: Throwable?) {
            val t = throwable?.let { " -> ${it.message}" } ?: ""
            println("[FALLBACK] ${level.name}/${tag.orEmpty()}: $message$t")
        }
    }

    // 调试信息
    inline fun d(message: () -> String) = loggerOrFallback.log(Level.DEBUG, "App", message())
    // 普通信息
    inline fun i(message: () -> String) = loggerOrFallback.log(Level.INFO, "App", message())
    // 警告信息
    inline fun w(message: () -> String) = loggerOrFallback.log(Level.WARN, "App", message())
    // 报错信息
    inline fun e(message: () -> String, t: Throwable? = null) = loggerOrFallback.log(Level.ERROR, "App", message(), t)
}

// Platform init function; actual implementations will live in platform source sets.
expect fun initKermit()
