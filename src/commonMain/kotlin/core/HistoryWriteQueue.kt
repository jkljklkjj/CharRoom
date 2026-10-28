package core

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Serial, mergeable writes with bounded retries and an awaitable durability barrier. */
class HistoryWriteQueue<T : Any>(scope: CoroutineScope, private val debounceMillis: Long = 1000,
    private val merge: (T, T) -> T, private val failed: (Throwable) -> Unit = {},
    private val retryDelayMillis: Long = 100, private val persist: suspend (T) -> Unit) {
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val lock = Any()
    private var pending: T? = null
    private var requested = 0L
    private var committed = 0L
    private var closed = false
    private val barriers = mutableListOf<Pair<Long, CompletableDeferred<Unit>>>()
    private val worker = scope.launch {
        for (signal in wake) {
            delay(debounceMillis)
            while (isActive) {
                val batch = synchronized(lock) { pending?.let { it to requested }.also { pending = null } } ?: break
                var failure: Exception? = null
                for (attempt in 0..2) {
                    try { withContext(Dispatchers.IO) { persist(batch.first) }; failure = null; break }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { failure = error; if (attempt < 2) delay(retryDelayMillis * (1L shl attempt)) }
                }
                synchronized(lock) {
                    if (failure == null) committed = batch.second
                    else pending = pending?.let { merge(batch.first, it) } ?: batch.first
                    val finished = barriers.filter { it.first <= batch.second }
                    barriers.removeAll(finished.toSet())
                    finished.forEach { if (failure == null) it.second.complete(Unit) else it.second.completeExceptionally(failure!!) }
                }
                if (failure != null) { failed(failure!!); break }
            }
        }
    }
    fun request(value: T) {
        synchronized(lock) {
            check(!closed) { "History writer closed" }
            pending = pending?.let { merge(it, value) } ?: value
            requested++
        }
        wake.trySend(Unit)
    }
    suspend fun flush() {
        val barrier = synchronized(lock) {
            if (committed >= requested) return
            check(!closed) { "History writer closed" }
            CompletableDeferred<Unit>().also { barriers.add(requested to it) }
        }
        wake.trySend(Unit)
        barrier.await()
    }
    suspend fun flushAndClose() { try { flush() } finally { close() } }
    fun close() {
        synchronized(lock) {
            closed = true
            barriers.forEach { it.second.cancel() }
            barriers.clear()
            pending = null
        }
        wake.close(); worker.cancel()
    }
}
