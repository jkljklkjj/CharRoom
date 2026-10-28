package core

import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryWriteQueueTest {
    @Test fun flushRetriesFailuresWithoutRequiringAnotherMessage() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var attempts = 0
        val queue = HistoryWriteQueue<Int>(scope, 0, { _, newer -> newer }, retryDelayMillis = 1) {
            if (++attempts < 3) throw java.io.IOException("transient")
        }
        try { queue.request(1); withTimeout(3000) { queue.flush() }; assertEquals(3, attempts) }
        finally { queue.close(); scope.cancel() }
    }
    @Test fun changeDuringWriteIsPersistedWithoutAnotherMessage() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); val finished = CompletableDeferred<Unit>()
        val writes = mutableListOf<Int>()
        val queue = HistoryWriteQueue<Int>(scope, 0, { _, newer -> newer }) { value ->
            if (value == 1) { started.complete(Unit); release.await() }
            synchronized(writes) { writes.add(value) }
            if (value == 2) finished.complete(Unit)
        }
        try {
            queue.request(1); withTimeout(3000) { started.await() }
            queue.request(2); release.complete(Unit); withTimeout(3000) { finished.await() }
            assertEquals(listOf(1, 2), synchronized(writes) { writes.toList() })
        } finally { queue.close(); scope.cancel() }
    }
}
