package core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppActivity {
    private val mutable = MutableStateFlow(true)
    val foreground = mutable.asStateFlow()

    fun foreground(value: Boolean) {
        mutable.value = value
    }
}

fun encounterPollDelay(state: String, failures: Int): Long =
    when {
        failures > 0 -> (4000L shl (failures - 1).coerceAtMost(4)).coerceAtMost(60_000)
        state in setOf("ACTIVE", "PREPARING", "MATCHING", "WAITING", "QUEUED", "READY") -> 1200
        else -> 15_000
    }
