package core

import com.chatlite.proto.MessageProtos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PendingMessageAcksTest {
    @Test
    fun acknowledgementWithoutExplicitSuccessDoesNotConfirmSending() {
        var result: Boolean? = null
        val pending = PendingMessageAcks({ _, _ -> {} })
        pending.await("request") { success, _ -> result = success }
        pending.acknowledge(MessageProtos.AckMessage.newBuilder().setMessageId("request").build(), byteArrayOf())
        assertEquals(false, result)
    }
    private class Clock {
        val expirations = mutableListOf<() -> Unit>()
        fun schedule(delay: Long, action: () -> Unit): () -> Unit {
            assertEquals(MessageProtocol.ACK_TIMEOUT_MILLIS, delay)
            expirations += action
            return {} // Call stale timeouts too, to exercise the correlation guard.
        }
    }

    @Test
    fun localWriteIsNotSuccessAndOnlyTheMatchingAckCompletesTheSend() {
        val clock = Clock(); val results = mutableListOf<Boolean>()
        val pending = PendingMessageAcks(clock::schedule)
        pending.await("a") { success, _ -> results += success }
        assertTrue(results.isEmpty())
        pending.acknowledge(MessageProtos.AckMessage.newBuilder().setMessageId("other").setSuccess(true).build(), byteArrayOf())
        assertTrue(results.isEmpty())
        val ack = MessageProtos.AckMessage.newBuilder().setMessageId("a").setSuccess(true).build()
        pending.acknowledge(ack, byteArrayOf(1))
        pending.acknowledge(ack, byteArrayOf(1))
        clock.expirations.forEach { it() }
        assertEquals(listOf(true), results)
    }

    @Test
    fun rejectionTimeoutAndDisconnectEachFailExactlyOnce() {
        val clock = Clock(); val results = mutableListOf<Boolean>()
        val pending = PendingMessageAcks(clock::schedule)
        for (id in listOf("rejected", "timeout", "disconnected")) pending.await(id) { success, _ -> results += success }
        pending.acknowledge(MessageProtos.AckMessage.newBuilder().setMessageId("rejected").setSuccess(false).build(), byteArrayOf())
        clock.expirations[1]()
        pending.failAll()
        clock.expirations.forEach { it() }
        assertEquals(listOf(false, false, false), results)
    }

    @Test
    fun aLateTimeoutCannotFailANewAttemptWithTheSameMessageId() {
        val clock = Clock(); val results = mutableListOf<Boolean>()
        val pending = PendingMessageAcks(clock::schedule)
        pending.await("stable") { success, _ -> results += success }
        clock.expirations[0]()
        pending.await("stable") { success, _ -> results += success }
        clock.expirations[0]()
        pending.acknowledge(MessageProtos.AckMessage.newBuilder().setMessageId("stable").setSuccess(true).build(), byteArrayOf())
        assertEquals(listOf(false, true), results)
    }
}
