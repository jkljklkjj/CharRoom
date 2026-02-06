package core

import org.slf4j.LoggerFactory

actual fun initKermit() {
    val slf = LoggerFactory.getLogger("App")
    AppLog.setLogger(object : LoggerAdapter {
        override fun withTag(tag: String): LoggerAdapter = this
        override fun log(level: Level, tag: String?, message: String, throwable: Throwable?) {
            when (level) {
                Level.DEBUG -> if (throwable == null) slf.debug(message) else slf.debug(message, throwable)
                Level.INFO -> if (throwable == null) slf.info(message) else slf.info(message, throwable)
                Level.WARN -> if (throwable == null) slf.warn(message) else slf.warn(message, throwable)
                Level.ERROR -> if (throwable == null) slf.error(message) else slf.error(message, throwable)
            }
        }
    })
    AppLog.i { "SLF4J logging initialized (desktop)" }
}
