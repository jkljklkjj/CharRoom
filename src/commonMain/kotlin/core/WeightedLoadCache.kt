package core

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Byte-budgeted LRU; concurrent callers share a load without owning each other's cancellation. */
internal class WeightedLoadCache<T : Any>(
    private val budget: Long,
    private val weight: (T) -> Long,
) {
    private val lock = Any()
    private val values = LinkedHashMap<String, Pair<T, Long>>(16, .75f, true)
    private val loads = HashMap<String, Deferred<T?>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val permits = Semaphore(4)
    private var bytes = 0L
    private var generation = 0L

    fun get(key: String): T? = synchronized(lock) { values[key]?.first }

    suspend fun load(key: String, loader: suspend () -> T?): T? {
        val task =
            synchronized(lock) {
                values[key]?.let {
                    return it.first
                }
                loads[key] ?: createLoad(key, loader)
            }
        return task.await()
    }

    private fun createLoad(key: String, loader: suspend () -> T?): Deferred<T?> {
        val expected = generation
        val deferred =
            scope.async(start = CoroutineStart.LAZY) {
                val value = permits.withPermit { loader() }
                if (value != null) retain(key, value, expected)
                value
            }
        loads[key] = deferred
        deferred.invokeOnCompletion {
            synchronized(lock) { if (loads[key] === deferred) loads.remove(key) }
        }
        return deferred
    }

    private fun retain(key: String, value: T, expected: Long) =
        synchronized(lock) {
            val cost = weight(value)
            if (expected == generation && cost in 0..budget) {
                values.put(key, value to cost)?.let { bytes -= it.second }
                bytes += cost
                while (bytes > budget) {
                    val iterator = values.entries.iterator()
                    bytes -= iterator.next().value.second
                    iterator.remove()
                }
            }
        }

    fun clear() {
        val pending =
            synchronized(lock) {
                generation++
                values.clear()
                bytes = 0L
                loads.values.toList().also { loads.clear() }
            }
        pending.forEach { it.cancel() }
    }
}

internal fun imageSizeBucket(size: Int?): Int? {
    if (size == null) return null
    var bucket = 32
    while (bucket < size && bucket < 2048) bucket *= 2
    return bucket
}

internal fun imageMemoryKey(url: String, key: String?, size: Int?) =
    "${key ?: url}#${imageSizeBucket(size) ?: "original"}"
