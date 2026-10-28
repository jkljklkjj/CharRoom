package cli

import core.ChatTransport
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy
import java.nio.file.Files
import kotlin.test.*

class CliDeliveryQueueTest {
    @Test fun reconnectionAndProcessRestartReplayTheSameBytesOnlyAfterAuthentication() = runBlocking {
        val root = Files.createTempDirectory("cli-delivery-").toFile()
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
            CliDeliveryQueue(initial, sender, transport, {}).drainOnce()
            assertTrue(sent.isEmpty())
            connected = true
            CliDeliveryQueue(initial, sender, transport, {}).drainOnce()
            assertEquals(1, initial.entries().size)
            now += 60_000; acknowledge = true
            val restarted = CliOutbox(root, 42) { now }
            CliDeliveryQueue(restarted, sender, transport, {}).drainOnce()
            assertEquals(2, sent.size)
            assertContentEquals(sent[0], sent[1])
            assertTrue(restarted.entries().isEmpty())
        } finally { root.deleteRecursively() }
    }
}
