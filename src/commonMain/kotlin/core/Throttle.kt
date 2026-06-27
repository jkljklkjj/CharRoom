package core

/**
 * 操作类型枚举 — 每种操作独立节流
 */
enum class ThrottleOp {
    PRIVATE_SEND,
    GROUP_SEND,
    LOGIN,
    REGISTER,
    SEND_VERIFY_CODE,
    LOAD_CONTACTS,
}

/**
 * 通用节流器 — 维护每个操作的最后执行时间
 * 用法:
 *   val throttle = Throttle()
 *   if (throttle.shouldThrottle(ThrottleOp.PRIVATE_SEND)) return
 */
class Throttle(private val defaultDelayMs: Long = 300L) {

    private val lastRun = mutableMapOf<ThrottleOp, Long>()

    /**
     * 检查是否需要节流。
     * @return true = 操作被节流（太频繁，跳过）；false = 可以执行
     */
    fun shouldThrottle(op: ThrottleOp, delayMs: Long = defaultDelayMs): Boolean {
        val now = System.currentTimeMillis()
        val last = lastRun[op] ?: 0L
        if (now - last < delayMs) return true
        lastRun[op] = now
        return false
    }

    /** 重置某个操作的冷却（节流锁释放前强制放行） */
    fun reset(op: ThrottleOp) {
        lastRun.remove(op)
    }

    /** 清空所有冷却 */
    fun clear() {
        lastRun.clear()
    }
}
