package core

import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * 日志工具类，兼容原有调用方式
 * 新代码请直接使用KotlinLogging标准用法：private val logger = KotlinLogging.logger {}
 */
import kotlin.PublishedApi

object AppLog {
    @PublishedApi
    internal val logger = KotlinLogging.logger("ChatApp")

    /**
     * 调试日志
     * @param message 消息生成函数
     */
    inline fun d(crossinline message: () -> String) {
        logger.debug { message() }
    }

    /**
     * 信息日志
     * @param message 消息生成函数
     */
    inline fun i(crossinline message: () -> String) {
        logger.info { message() }
    }

    /**
     * 警告日志
     * @param message 消息生成函数
     */
    inline fun w(crossinline message: () -> String) {
        logger.warn { message() }
    }

    /**
     * 错误日志
     * @param message 消息生成函数
     * @param throwable 异常
     */
    inline fun e(crossinline message: () -> String, throwable: Throwable? = null) {
        if (throwable != null) {
            logger.error(throwable) { message() }
        } else {
            logger.error { message() }
        }
    }
}