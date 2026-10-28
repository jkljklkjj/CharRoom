package cli

import core.ChatTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.lang.reflect.Proxy
import java.nio.file.Files
import kotlin.test.*

class CliDeliveryQueueTest {
    @Test fun onlyFourAttemptsRunAtOnceAndCancellationKeepsPendingRows() = runBlocking {
        val root = Files.createTempDirectory("cli-delivery-limit-").toFile()
        var worker: Job? = null
        try {
            val sent = Channel<String>(Channel.UNLIMITED)
            val callbacks = mutableMapOf<String, (Boolean, List<ByteArray>) -> Unit>()
            @Suppress("UNCHECKED_CAST")
            val transport = Proxy.newProxyInstance(ChatTransport::class.java.classLoader, arrayOf(ChatTransport::class.java)) { _, method, args ->
                when (method.name) {
                    "isConnected" -> true
                    "send" -> {
                        val id = com.chatlite.proto.MessageProtos.MessageWrapper.parseFrom(args[0] as ByteArray).chat.messageId
                        callbacks[id] = args[4] as (Boolean, List<ByteArray>) -> Unit
                        sent.trySend(id)
                        null
                    }
                    else -> null
                }
            } as ChatTransport
            val sender = CliMessageSender(transport, { _, _ -> }, {}, ackTimeout = 30_000)
            val outbox = CliOutbox(root, 42)
            repeat(6) { outbox.enqueue(sender.prepare(42, 7, "fixture $it", false)) }
            val queue = CliDeliveryQueue(outbox, sender, transport, {})
            worker = launch { queue.run() }
            val first = withTimeout(2_000) { List(4) { sent.receive() } }
            assertEquals(4, first.toSet().size)
            assertNull(withTimeoutOrNull(100) { sent.receive() })
            callbacks.getValue(first.first())(true, emptyList())
            val fifth = withTimeout(2_000) { sent.receive() }
            assertFalse(fifth in first)
            assertNull(withTimeoutOrNull(100) { sent.receive() })
            worker.cancelAndJoin()
            assertEquals(5, outbox.entries().size)
            assertTrue(outbox.entries().all { it.attempts == 0 && it.status == "QUEUED" })
            queue.request()
            assertTrue(sent.tryReceive().isFailure)
        } finally { worker?.cancelAndJoin(); root.deleteRecursively() }
    }

    @Test fun aSlowAckDoesNotBlockNewMessagesOrResendAnInflightMessage() = runBlocking {
        val root = Files.createTempDirectory("cli-delivery-fair-").toFile()
        var worker: Job? = null
        try {
            val sent = Channel<String>(Channel.UNLIMITED)
            var slowId = ""
            @Suppress("UNCHECKED_CAST")
            val transport = Proxy.newProxyInstance(ChatTransport::class.java.classLoader, arrayOf(ChatTransport::class.java)) { _, method, args ->
                when (method.name) {
                    "isConnected" -> true
                    "send" -> {
                        val id = com.chatlite.proto.MessageProtos.MessageWrapper.parseFrom(args[0] as ByteArray).chat.messageId
                        sent.trySend(id)
                        if (id != slowId) (args[4] as (Boolean, List<ByteArray>) -> Unit)(true, emptyList())
                        null
                    }
                    else -> null
                }
            } as ChatTransport
            val sender = CliMessageSender(transport, { _, _ -> }, {}, ackTimeout = 30_000)
            val outbox = CliOutbox(root, 42)
            slowId = outbox.enqueue(sender.prepare(42, 7, "slow", false)).id
            val queue = CliDeliveryQueue(outbox, sender, transport, {})
            worker = launch { queue.run() }
            assertEquals(slowId, withTimeout(2_000) { sent.receive() })
            val next = outbox.enqueue(sender.prepare(42, 7, "new", false))
            queue.request()
            assertEquals(next.id, withTimeout(2_000) { sent.receive() })
            withTimeout(2_000) { while (outbox.entries().size != 1) delay(10) }
            repeat(3) { queue.request(); delay(10) }
            assertTrue(sent.tryReceive().isFailure, "An unconfirmed in-flight ID must not be sent twice")
        } finally { worker?.cancelAndJoin(); root.deleteRecursively() }
    }

    @Test fun reconnectionAndProcessRestartReplayTheSameBytesOnlyAfterAuthentication() = runBlocking {
        val root = Files.createTempDirectory("cli-delivery-").toFile()
        var worker: Job? = null
        try {
            var connected = false
            var acknowledge = false
            var now = 1L
            val sent = mutableListOf<ByteArray>()
            @Suppress("UNCHECKED_CAST")
            val transport = Proxy.newProxyInstance(ChatTransport::class.java.classLoader, arrayOf(ChatTransport::class.java)) { _, method, args ->
                when (method.name) {
                    "isConnected" -> connected
                    "send" -> {
                        sent.add(args[0] as ByteArray)
                        if (acknowledge) (args[4] as (Boolean, List<ByteArray>) -> Unit)(true, emptyList())
                        null
                    }
                    else -> null
                }
            } as ChatTransport
            val sender = CliMessageSender(transport, { _, _ -> }, {}, ackTimeout = 10)
            val initial = CliOutbox(root, 42) { now }
            initial.enqueue(sender.prepare(42, 7, "fixture", false))
            val queue = CliDeliveryQueue(initial, sender, transport, {})
            worker = launch { queue.run() }
            yield()
            assertTrue(sent.isEmpty())
            connected = true
            queue.request()
            withTimeout(2_000) { while (initial.entries().single().attempts != 1) delay(10) }
            assertEquals(1, initial.entries().size)
            worker.cancelAndJoin()
            now += 60_000; acknowledge = true
            val restarted = CliOutbox(root, 42) { now }
            worker = launch { CliDeliveryQueue(restarted, sender, transport, {}).run() }
            withTimeout(2_000) { while (restarted.entries().isNotEmpty()) delay(10) }
            assertEquals(2, sent.size)
            assertContentEquals(sent[0], sent[1])
            assertTrue(restarted.entries().isEmpty())
        } finally { worker?.cancelAndJoin(); root.deleteRecursively() }
    }
}
