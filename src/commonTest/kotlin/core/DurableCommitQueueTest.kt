package core

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.*

class DurableCommitQueueTest {
    @Test fun backpressureCanBeInterruptedBeforeEnqueueWithoutLeavingAnOrphan() {
        val entered=CountDownLatch(1); val release=CountDownLatch(1)
        val queue=DurableCommitQueue<Int>(weight={1}, maxBytes=1, maxRequests=1) {
            entered.countDown(); check(release.await(5,TimeUnit.SECONDS))
        }
        val first=thread { queue.commit(listOf(0)) }
        assertTrue(entered.await(2,TimeUnit.SECONDS))
        val failure=java.util.concurrent.atomic.AtomicReference<Throwable>()
        val second=thread {
            try { queue.commit(listOf(1)) } catch (caught:Throwable) { failure.set(caught) }
        }
        try {
            val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2)
            while (second.state!=Thread.State.WAITING && System.nanoTime()<deadline) Thread.yield()
            second.interrupt(); second.join(2000)
            assertIs<java.io.InterruptedIOException>(failure.get())
            assertEquals(0,queue.pendingCount())
        } finally { release.countDown(); first.join(2000); second.join(2000) }
    }

    @Test fun interruptionAfterEnqueueStillWaitsForDurabilityAndRestoresTheFlag() {
        val entered=CountDownLatch(1); val release=CountDownLatch(1)
        val durable=java.util.concurrent.atomic.AtomicBoolean()
        val returned=java.util.concurrent.atomic.AtomicBoolean()
        val queue=DurableCommitQueue<Int>(weight={1}) {
            if (it==listOf(0)) { entered.countDown(); check(release.await(5,TimeUnit.SECONDS)) }
            else durable.set(true)
        }
        val first=thread { queue.commit(listOf(0)) }
        assertTrue(entered.await(2,TimeUnit.SECONDS))
        val second=thread {
            queue.commit(listOf(1))
            returned.set(durable.get() && Thread.currentThread().isInterrupted)
        }
        try {
            val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2)
            while (queue.pendingCount()!=1 && System.nanoTime()<deadline) Thread.yield()
            assertEquals(1,queue.pendingCount()); second.interrupt()
            assertFalse(returned.get())
        } finally { release.countDown(); first.join(2000); second.join(2000) }
        assertTrue(returned.get())
    }

    @Test fun concurrentWaitersShareOneCommitAndNeverReturnBeforeItFinishes() {
        val entered=CountDownLatch(1); val release=CountDownLatch(1)
        val writes=java.util.Collections.synchronizedList(mutableListOf<List<Int>>())
        val completed=java.util.concurrent.atomic.AtomicInteger()
        val queue=DurableCommitQueue<Int>(weight={1}) { batch ->
            writes+=batch
            if (batch==listOf(0)) { entered.countDown(); check(release.await(5,TimeUnit.SECONDS)) }
        }
        val first=thread { queue.commit(listOf(0)); completed.incrementAndGet() }
        assertTrue(entered.await(2,TimeUnit.SECONDS))
        val waiters=(1..8).map { n -> thread { queue.commit(listOf(n)); completed.incrementAndGet() } }
        val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2)
        while (queue.pendingCount()<8 && System.nanoTime()<deadline) Thread.yield()
        assertEquals(8,queue.pendingCount()); assertEquals(0,completed.get())
        release.countDown(); first.join(3000); waiters.forEach { it.join(3000) }
        assertEquals(9,completed.get()); assertEquals(2,writes.size)
        assertEquals((1..8).toSet(),writes.last().toSet())
    }

    @Test fun failedCommitNeverCompletesSuccessfullyAndTheNextCallCanRetry() {
        var fail=true
        val queue=DurableCommitQueue<Int>(weight={1}) { if (fail) throw java.io.IOException("fixture disk failure") }
        assertFailsWith<java.io.IOException> { queue.commit(listOf(1)) }
        fail=false; queue.commit(listOf(1))
        assertEquals(0,queue.pendingCount())
    }
}
