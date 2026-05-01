package core

import timber.log.Timber

/**
 * Android platform logging implementation
 */
object PlatformLogger {
    fun debug(tag: String, message: String) {
        Timber.tag(tag).d(message)
    }

    fun info(tag: String, message: String) {
        Timber.tag(tag).i(message)
    }

    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        Timber.tag(tag).w(throwable, message)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        Timber.tag(tag).e(throwable, message)
    }
}