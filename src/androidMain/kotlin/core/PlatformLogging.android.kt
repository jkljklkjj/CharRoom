package core

import co.touchlab.kermit.Kermit
import co.touchlab.kermit.logcat.LogcatLogger

actual fun initKermit() {
    val k = Kermit(LogcatLogger())
    AppLog.setLogger(object : LoggerAdapter {
        override fun withTag(tag: String): LoggerAdapter = this
        override fun log(level: Level, tag: String?, message: String, throwable: Throwable?) {
            when (level) {
                Level.DEBUG -> if (throwable == null) k.d(tag ?: "App") { message } else k.d(tag ?: "App") { message }
                Level.INFO -> if (throwable == null) k.i(tag ?: "App") { message } else k.i(tag ?: "App") { message }
                Level.WARN -> if (throwable == null) k.w(tag ?: "App") { message } else k.w(tag ?: "App") { message }
                Level.ERROR -> if (throwable == null) k.e(tag ?: "App") { message } else k.e(tag ?: "App") { message }
            }
            // Kermit doesn't take throwable directly in these calls here; advanced usage could include metadata
        }
    })
    AppLog.i { "Kermit Logcat logging initialized (android)" }
}
