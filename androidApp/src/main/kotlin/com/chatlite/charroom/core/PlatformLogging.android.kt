package com.chatlite.charroom.core

import android.util.Log

/**
 * Android platform logging implementation
 */
object PlatformLogger {
    fun debug(tag: String, message: String) {
        Log.d(tag, message)
    }

    fun info(tag: String, message: String) {
        Log.i(tag, message)
    }

    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }
}