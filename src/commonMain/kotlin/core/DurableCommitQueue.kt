package core

import java.io.InterruptedIOException
import java.util.ArrayDeque

/** Callers share a writer, but each waits for the actual durable commit containing its rows. */
internal class DurableCommitQueue<T>(
    private val weight: (T) -> Long,
    private val maxBytes: Long = 32L * 1024 * 1024,
    private val maxRequests: Int = 256,
    private val persist: (List<T>) -> Unit,
) {
    private class Ticket<T>(val rows: List<T>, val bytes: Long) {
        var done = false
        var failure: Throwable? = null
    }

    @Suppress(
        "PLATFORM_CLASS_MAPPED_TO_KOTLIN"
    ) // JVM wait/notify is shared by desktop, CLI and Android.
    private val lock = java.lang.Object()
    private val pending = ArrayDeque<Ticket<T>>()
    private var writing = false
    private var retainedBytes = 0L
    private var requests = 0

    fun pendingCount(): Int = synchronized(lock) { pending.size }

    fun commit(rows: List<T>) {
        if (rows.isEmpty()) return
        val ticket = enqueue(rows)
        val wait = WaitState()
        try {
            while (true) {
                val batch = awaitBatch(ticket, wait) ?: return
                // Every ticket must observe the same failure, including a JVM error; each
                // owner rethrows it after the queue releases its writer and byte reservations.
                val failure = runCatching { persist(batch.flatMap { it.rows }) }.exceptionOrNull()
                completeBatch(batch, failure)
            }
        } finally {
            if (wait.interrupted) Thread.currentThread().interrupt()
        }
    }

    private class WaitState {
        var interrupted = false
    }

    private fun enqueue(rows: List<T>): Ticket<T> =
        synchronized(lock) {
            val ticket = Ticket(rows.toList(), rows.sumOf { weight(it).coerceAtLeast(0) })
            // One oversized request may proceed alone, preserving the batch API.
            while (!hasCapacity(ticket.bytes)) {
                try {
                    lock.wait()
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw InterruptedIOException("Interrupted before history enqueue").apply {
                        initCause(interrupted)
                    }
                }
            }
            pending.addLast(ticket)
            retainedBytes += ticket.bytes
            requests++
            ticket
        }

    private fun hasCapacity(bytes: Long): Boolean =
        requests == 0 || (requests < maxRequests && retainedBytes + bytes <= maxBytes)

    private fun awaitBatch(ticket: Ticket<T>, wait: WaitState): List<Ticket<T>>? =
        synchronized(lock) {
            while (!ticket.done && writing) {
                // Once enqueued, finish the durability barrier before restoring interruption.
                try {
                    lock.wait()
                } catch (_: InterruptedException) {
                    wait.interrupted = true
                }
            }
            if (ticket.done) {
                ticket.failure?.let { throw it }
                null
            } else {
                writing = true
                pending.toList().also { pending.clear() }
            }
        }

    private fun completeBatch(batch: List<Ticket<T>>, failure: Throwable?) =
        synchronized(lock) {
            batch.forEach {
                it.failure = failure
                it.done = true
                retainedBytes -= it.bytes
                requests--
            }
            writing = false
            lock.notifyAll()
        }
}

/** Weights may grow after insertion; callers trim again after updating their index. */
internal class WeightedJournalCache<K, V>(
    private val maxEntries: Int,
    private val maxBytes: Long,
    private val weight: (V) -> Long,
) {
    private val entries = LinkedHashMap<K, V>(16, .75f, true)

    @Synchronized
    fun getOrPut(key: K, create: () -> V): V {
        val value = entries.getOrPut(key, create)
        trim()
        return value
    }

    @Synchronized fun contains(key: K): Boolean = entries.containsKey(key)

    @Synchronized fun retainedBytes(): Long = entries.values.sumOf { weight(it).coerceAtLeast(0) }

    @Synchronized
    fun trim() {
        var bytes = retainedBytes()
        val iterator = entries.entries.iterator()
        while (iterator.hasNext() && overBudget(bytes)) {
            bytes -= weight(iterator.next().value).coerceAtLeast(0)
            iterator.remove()
        }
    }

    private fun overBudget(bytes: Long) = entries.size > maxEntries || bytes > maxBytes
}
