package core

import org.slf4j.LoggerFactory

/**
 * 日志工具类，兼容原有调用方式
 */
object AppLog {
    private val logger = LoggerFactory.getLogger("ChatApp")

    /**
     * 调试日志
     * @param message 消息生成函数
     */
    fun d(message: () -> String) {
        if (logger.isDebugEnabled) {
            logger.debug(message())
        }
    }

    /**
     * 信息日志
     * @param message 消息生成函数
     */
    fun i(message: () -> String) {
        if (logger.isInfoEnabled) {
            logger.info(message())
        }
    }

    /**
     * 警告日志
     * @param message 消息生成函数
     */
    fun w(message: () -> String) {
        if (logger.isWarnEnabled) {
            logger.warn(message())
        }
    }

    /**
     * 错误日志
     * @param message 消息生成函数
     * @param throwable 异常
     */
    fun e(message: () -> String, throwable: Throwable? = null) {
        if (logger.isErrorEnabled) {
            if (throwable != null) {
                logger.error(message(), throwable)
            } else {
                logger.error(message())
            }
        }
    }
}