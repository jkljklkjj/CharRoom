package core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
enum class ActionType {
    SEARCH, OPEN_CHAT, SEND_MESSAGE, RECEIVE_MESSAGE, ADD_FRIEND, ADD_GROUP, HEARTBEAT, NAVIGATE, OTHER
}

@Serializable
data class Action(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: ActionType,
    val targetId: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Simple thread-safe fixed-capacity sliding window (ring-buffer semantics: keep newest N)
 */
class SlidingWindow<T>(private val capacity: Int) {
    private val buffer = ArrayList<T>(capacity)
    @Synchronized
    fun push(item: T) {
        if (buffer.size >= capacity) {
            // drop oldest
            buffer.removeAt(0)
        }
        buffer.add(item)
    }

    @Synchronized
    fun snapshot(): List<T> = ArrayList(buffer)

    @Synchronized
    fun clear() = buffer.clear()
}

object ActionLogger {
    private val window = SlidingWindow<Action>(20)
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun log(action: Action) {
        try {
            window.push(action)
        } catch (_: Exception) {
            // swallow
        }
    }

    fun getSnapshot(): List<Action> = window.snapshot()

    fun clear() = window.clear()

    fun snapshotAsJson(): String = try {
        json.encodeToString(getSnapshot())
    } catch (_: Exception) {
        "[]"
    }
}

