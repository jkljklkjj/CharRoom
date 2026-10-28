package cli

import com.chatlite.proto.MessageProtos
import core.ChatTransport
import core.MsgType
import kotlinx.coroutines.*
import java.lang.reflect.Proxy
import kotlin.test.*

class CliMessageSenderTest {
    private class Fixture {
        var connected = true
        var failSend = false
        var payload: ByteArray? = null
        val sent = CompletableDeferred<Unit>()
        lateinit var callback: (Boolean, List<ByteArray>) -> Unit
        @Suppress("UNCHECKED_CAST")
        val transport = Proxy.newProxyInstance(ChatTransport::class.java.classLoader, arrayOf(ChatTransport::class.java)) { _, method, args ->
            when (method.name) {
                "isConnected" -> connected
                "send" -> { payload = args[0] as ByteArray; callback = args[4] as (Boolean, List<ByteArray>) -> Unit; sent.complete(Unit); check(!failSend); null }
                else -> null
            }
        } as ChatTransport
    }
    @Test fun onlyMatchingTransportAckCompletesSendAndPayloadIsPersistedFirst() = runBlocking {
        val f = Fixture()
        var persisted = false
        val sender = CliMessageSender(f.transport, { _, _ -> assertNull(f.payload); persisted = true }, {}, ackTimeout = 500)
        val result = async { sender.send(7, 42, "hello", false) }
        f.sent.await()
        assertTrue(persisted)
        assertFalse(result.isCompleted)
        val message = MessageProtos.MessageWrapper.parseFrom(f.payload)
        assertEquals(MsgType.CHAT, message.type)
        assertEquals(42L, message.chat.targetClientId)
        assertTrue(message.chat.messageId.isNotBlank())
        f.callback(true, emptyList())
        assertEquals(CliSendResult.ACCEPTED, result.await())
    }
    @Test fun groupIdentityIsPositiveAndNoRetryIsInventedOnTimeout() = runBlocking {
        val f = Fixture()
        val states = mutableListOf<Boolean>()
        var notices = 0
        val sender = CliMessageSender(f.transport, { _, failed -> states.add(failed) }, { notices++ }, slowAfter = 1, ackTimeout = 30)
        assertEquals(CliSendResult.UNCONFIRMED, sender.send(7, 42, "group", true))
        val message = MessageProtos.MessageWrapper.parseFrom(f.payload)
        assertEquals(MsgType.GROUP_CHAT, message.type)
        assertEquals(42L, message.groupChat.targetClientId)
        assertEquals(listOf(false, true), states)
        assertEquals(1, notices)
        f.callback(true, emptyList()) // Late callback must not resume a completed CLI command or throw.
    }
    @Test fun disconnectionAndPersistenceFailureDoNotWriteToTransport() = runBlocking {
        val f = Fixture()
        val sender = CliMessageSender(f.transport, { _, _ -> error("disk") }, {})
        assertFailsWith<IllegalStateException> { sender.send(7, 42, "hello", false) }
        assertNull(f.payload)
        f.connected = false
        assertFailsWith<IllegalStateException> { sender.send(7, 42, "hello", false) }
        assertNull(f.payload)
    }
    @Test fun cancellationIsNotReportedAsDeliveryFailure() = runBlocking {
        val f = Fixture()
        val states = mutableListOf<Boolean>()
        val sender = CliMessageSender(f.transport, { _, failed -> states.add(failed) }, {}, ackTimeout = 500)
        val task = launch { sender.send(7, 42, "hello", false) }
        f.sent.await(); task.cancelAndJoin()
        assertEquals(listOf(false), states)
        f.callback(false, emptyList())
    }
    @Test fun transportFailureLeavesAnObservableFailedHistoryRow() = runBlocking {
        val f = Fixture().apply { failSend = true }
        val states = mutableListOf<Boolean>()
        val sender = CliMessageSender(f.transport, { _, failed -> states.add(failed) }, {})
        assertFailsWith<IllegalStateException> { sender.send(7, 42, "hello", false) }
        assertEquals(listOf(false, true), states)
    }
}
