package core

import com.chatlite.proto.MessageProtos
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.*

class NativeQuicChatTransportTest {
    private class Driver : QuicDriver {
        val incoming = LinkedBlockingQueue<QuicEvent>()
        val sent = CopyOnWriteArrayList<Pair<Long, MessageProtos.MessageWrapper>>()
        @Volatile var closed = false
        @Volatile var failChatWrite = false
        val polls = java.util.concurrent.atomic.AtomicInteger()
        override fun connect(host: String, port: Int) { assertEquals("fixture.invalid", host) }
        override fun poll(timeoutMillis: Int): QuicEvent? {
            polls.incrementAndGet()
            return incoming.poll(timeoutMillis.toLong(), TimeUnit.MILLISECONDS)
        }
        override fun send(streamId: Long, bytes: ByteArray) {
            val frame = QuicStreamProtocol.decodeFrame(bytes)!!
            val wrapper = MessageProtos.MessageWrapper.parseFrom(frame.first)
            if (failChatWrite && wrapper.type == MsgType.CHAT) throw java.io.IOException("fixture write failure")
            sent += streamId to wrapper
        }
        override fun close() { closed = true }
        fun receive(streamId: Long, wrapper: MessageProtos.MessageWrapper) {
            incoming.put(QuicEvent.Data(streamId, QuicStreamProtocol.encodeFrame(wrapper.toByteArray())))
        }
    }
    private fun client(driver: Driver, timing: QuicTransportTiming = QuicTransportTiming()) = NativeQuicChatTransport(
        driverFactory = { driver }, identity = { QuicIdentity("fixture-token", 42, "fixture-device") }, timing = timing
    )
    private fun until(predicate: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (!predicate() && System.nanoTime() < end) Thread.sleep(5)
        assertTrue(predicate(), "transport event did not complete")
    }
    private fun authenticate(client: NativeQuicChatTransport, driver: Driver) {
        client.start("fixture.invalid", 443)
        driver.incoming.put(QuicEvent.Connected)
        until { driver.sent.any { it.second.type == MsgType.LOGIN } }
        driver.receive(0, response(true, "42"))
        until { client.isConnected() }
    }
    private fun response(success: Boolean, clientId: String = "") = MessageProtos.MessageWrapper.newBuilder()
        .setType(MsgType.RESPONSE).setResponse(MessageProtos.ResponseMessage.newBuilder()
            .setSuccess(success).setClientId(clientId)).build()
    private fun initialize(driver: Driver): Long {
        until { driver.sent.any { it.second.type == MsgType.CHECK } }
        val stream = driver.sent.first { it.second.type == MsgType.CHECK }.first
        driver.receive(stream, MessageProtos.MessageWrapper.newBuilder().setType(MsgType.STREAM_INIT_ACK)
            .setResponse(MessageProtos.ResponseMessage.newBuilder().setSuccess(true)).build())
        return stream
    }

    @Test fun quicHandshakeAloneDoesNotExposeAnAuthenticatedConnection() {
        val driver = Driver(); val client = client(driver)
        try {
            client.start("fixture.invalid", 443)
            driver.incoming.put(QuicEvent.Connected)
            until { driver.sent.isNotEmpty() }
            assertFalse(client.isConnected())
            val login = driver.sent.single()
            assertEquals(0L, login.first)
            assertEquals(MessageProtos.DeviceType.MOBILE, login.second.login.deviceType)
            driver.receive(0, response(true, "42"))
            until { client.isConnected() }
        } finally { client.stop() }
    }

    @Test fun handshakeCooldownDoesNotInvalidateAuthOrImmediatelyReconnect() {
        val driver = Driver()
        val created = java.util.concurrent.atomic.AtomicInteger()
        val failures = CopyOnWriteArrayList<String>()
        val client = NativeQuicChatTransport(driverFactory = { created.incrementAndGet(); driver },
            identity = { QuicIdentity("fixture-token", 42, "fixture-device") }, timing = QuicTransportTiming(reconnectMillis = 1))
        client.addAuthStateListener { failures += it }
        try {
            client.start("fixture.invalid", 443)
            driver.incoming.put(QuicEvent.Connected)
            until { driver.sent.isNotEmpty() }
            driver.receive(0, response(false).toBuilder().setResponse(MessageProtos.ResponseMessage.newBuilder()
                .setSuccess(false).setMessage("HANDSHAKE_RATE_LIMITED:60000")).build())
            until { driver.closed }
            // Would reconnect in 1ms without the server cooldown; stop must interrupt that wait.
            Thread.sleep(30)
            assertEquals(1, created.get())
            assertTrue(failures.isEmpty())
        } finally { client.stop() }
    }

    @Test fun conversationWaitsForInitializationAndMatchingBusinessAck() {
        val driver = Driver(); val client = client(driver)
        val results = CopyOnWriteArrayList<Boolean>()
        try {
            authenticate(client, driver)
            client.send(buildChatPayload("7", "fixture", timestamp = 1, messageId = "stable-id"), MsgType.CHAT, "7", 1) { ok, _ -> results += ok }
            until { driver.sent.any { it.second.type == MsgType.CHECK } }
            assertFalse(driver.sent.any { it.second.type == MsgType.CHAT })
            assertTrue(results.isEmpty())
            val stream = driver.sent.first { it.second.type == MsgType.CHECK }.first
            driver.receive(stream, MessageProtos.MessageWrapper.newBuilder().setType(MsgType.STREAM_INIT_ACK)
                .setResponse(MessageProtos.ResponseMessage.newBuilder().setSuccess(true)).build())
            until { driver.sent.any { it.second.type == MsgType.CHAT } }
            assertTrue(results.isEmpty(), "writing bytes is not a server ACK")
            driver.receive(stream, MessageProtos.MessageWrapper.newBuilder().setType(MsgType.ACK)
                .setAck(MessageProtos.AckMessage.newBuilder().setMessageId("other").setSuccess(true)).build())
            driver.receive(stream, MessageProtos.MessageWrapper.newBuilder().setType(MsgType.ACK)
                .setAck(MessageProtos.AckMessage.newBuilder().setMessageId("stable-id").setSuccess(false)).build())
            until { results.isNotEmpty() }
            assertEquals(listOf(false), results.toList())
        } finally { client.stop() }
    }

    @Test fun privateAndGroupWithTheSameTargetGetDifferentStreams() {
        val driver = Driver(); val client = client(driver)
        try {
            authenticate(client, driver)
            client.send(buildChatPayload("7", "fixture", timestamp = 1), MsgType.CHAT, "7", 1) { _, _ -> }
            client.send(buildGroupChatPayload("7", "fixture"), MsgType.GROUP_CHAT, "7", 1) { _, _ -> }
            until { driver.sent.count { it.second.type == MsgType.CHECK } == 2 }
            val checks = driver.sent.filter { it.second.type == MsgType.CHECK }
            assertNotEquals(checks[0].first, checks[1].first)
            assertEquals(setOf(MessageProtos.ConversationType.PRIVATE, MessageProtos.ConversationType.GROUP), checks.map { it.second.check.conversationType }.toSet())
        } finally { client.stop() }
    }

    @Test fun stoppingDuringStreamInitializationFailsTheQueuedSendExactlyOnce() {
        val driver = Driver(); val client = client(driver)
        val results = CopyOnWriteArrayList<Boolean>()
        authenticate(client, driver)
        client.send(buildChatPayload("7", "fixture", timestamp = 1), MsgType.CHAT, "7", 1) { ok, _ -> results += ok }
        until { driver.sent.any { it.second.type == MsgType.CHECK } }
        client.stop()
        until { driver.closed && results.isNotEmpty() }
        assertEquals(listOf(false), results.toList())
        assertFalse(client.isConnected())
    }

    @Test fun rejectedLoginStopsWithoutAcceptingChatMessages() {
        val driver = Driver(); val client = client(driver)
        val failures = CopyOnWriteArrayList<String>()
        client.addAuthStateListener { failures += it }
        try {
            client.start("fixture.invalid", 443)
            driver.incoming.put(QuicEvent.Connected)
            until { driver.sent.isNotEmpty() }
            driver.receive(0, response(false))
            until { failures.isNotEmpty() && driver.closed }
            assertFalse(client.isConnected())
        } finally { client.stop() }
    }

    @Test fun streamInitializationTimesOutWithoutReportingSuccess() {
        val driver = Driver(); val client = client(driver, QuicTransportTiming(streamInitMillis = 40))
        val results = CopyOnWriteArrayList<Boolean>()
        try {
            authenticate(client, driver)
            client.send(buildChatPayload("7", "fixture", timestamp = 1), MsgType.CHAT, "7", 1) { ok, _ -> results += ok }
            until { results.isNotEmpty() }
            assertEquals(listOf(false), results.toList())
            assertFalse(driver.sent.any { it.second.type == MsgType.CHAT })
        } finally { client.stop() }
    }

    @Test fun fragmentedBusinessAckCompletesSuccessfullyOnlyOnce() {
        val driver = Driver(); val client = client(driver)
        val results = CopyOnWriteArrayList<Boolean>()
        try {
            authenticate(client, driver)
            client.send(buildChatPayload("7", "fixture", timestamp = 1, messageId = "success-id"), MsgType.CHAT, "7", 1) { ok, _ -> results += ok }
            val stream = initialize(driver)
            until { driver.sent.any { it.second.type == MsgType.CHAT } }
            val bytes = QuicStreamProtocol.encodeFrame(MessageProtos.MessageWrapper.newBuilder().setType(MsgType.ACK)
                .setAck(MessageProtos.AckMessage.newBuilder().setMessageId("success-id").setSuccess(true)).build().toByteArray())
            driver.incoming.put(QuicEvent.Data(stream, bytes.copyOfRange(0, 2)))
            driver.incoming.put(QuicEvent.Data(stream, bytes.copyOfRange(2, bytes.size)))
            driver.incoming.put(QuicEvent.Data(stream, bytes))
            until { results.isNotEmpty() }
            client.stop(); until { driver.closed }
            assertEquals(listOf(true), results.toList())
        } finally { client.stop() }
    }

    @Test fun nativeWriteFailureAndSubsequentShutdownFailTheSendOnlyOnce() {
        val driver = Driver(); val client = client(driver)
        val results = CopyOnWriteArrayList<Boolean>()
        try {
            authenticate(client, driver)
            driver.failChatWrite = true
            client.send(buildChatPayload("7", "fixture", timestamp = 1, messageId = "write-failure"), MsgType.CHAT, "7", 1) { ok, _ -> results += ok }
            initialize(driver)
            until { results.isNotEmpty() }
            client.stop(); until { driver.closed }
            assertEquals(listOf(false), results.toList())
        } finally { client.stop() }
    }

    @Test fun authenticatedIdentityMustMatchTheRestAccount() {
        val driver = Driver(); val client = client(driver)
        val failures = CopyOnWriteArrayList<String>()
        client.addAuthStateListener { failures += it }
        try {
            client.start("fixture.invalid", 443)
            driver.incoming.put(QuicEvent.Connected)
            until { driver.sent.isNotEmpty() }
            driver.receive(0, response(true, "7"))
            until { driver.closed }
            assertFalse(client.isConnected()); assertEquals(1, failures.size)
        } finally { client.stop() }
    }

    @Test fun reconnectStartsWithFreshLoginAndDoesNotReuseOldPendingMessages() {
        val first = Driver(); val second = Driver()
        val drivers = LinkedBlockingQueue<Driver>().apply { add(first); add(second) }
        val client = NativeQuicChatTransport(driverFactory = { drivers.poll() ?: error("unexpected reconnect") },
            identity = { QuicIdentity("fixture-token", 42, "fixture-device") }, timing = QuicTransportTiming(reconnectMillis = 10))
        val results = CopyOnWriteArrayList<Boolean>()
        try {
            authenticate(client, first)
            client.send(buildChatPayload("7", "fixture", timestamp = 1, messageId = "old-id"), MsgType.CHAT, "7", 1) { ok, _ -> results += ok }
            initialize(first)
            until { first.sent.any { it.second.type == MsgType.CHAT } }
            first.incoming.put(QuicEvent.Closed("fixture disconnect"))
            until { first.closed && results.isNotEmpty() }
            assertEquals(listOf(false), results.toList())
            second.incoming.put(QuicEvent.Connected)
            until { second.sent.isNotEmpty() }
            assertFalse(client.isConnected())
            second.receive(0, response(true, "42")); until { client.isConnected() }
            assertEquals(listOf(MsgType.LOGIN), second.sent.map { it.second.type })
            first.receive(4, MessageProtos.MessageWrapper.newBuilder().setType(MsgType.ACK)
                .setAck(MessageProtos.AckMessage.newBuilder().setMessageId("old-id").setSuccess(true)).build())
            client.stop(); until { second.closed }
            assertEquals(listOf(false), results.toList())
        } finally { client.stop() }
    }

    @Test fun offlineOrOversizeMessagesAreRejectedBeforeNativeSend() {
        val driver = Driver(); val client = client(driver)
        val results = CopyOnWriteArrayList<Boolean>()
        client.send(byteArrayOf(1), MsgType.CHAT, "7", 1) { ok, _ -> results += ok }
        client.send(ByteArray(MessageProtocol.QUIC_SERVER_MAX_PAYLOAD_BYTES + 1), MsgType.CHAT, "7", 1) { ok, _ -> results += ok }
        assertEquals(listOf(false, false), results.toList())
        assertTrue(driver.sent.isEmpty())
    }

    @Test fun authenticatedClientRejectsAValidProtobufBeyondTheServerParsingLimit() {
        val driver = Driver(); val client = client(driver)
        val results = CopyOnWriteArrayList<Boolean>()
        try {
            authenticate(client, driver)
            val payload = buildChatPayload("7", "x".repeat(65_536), timestamp = 1, messageId = "oversize")
            assertTrue(payload.size > MessageProtocol.QUIC_SERVER_MAX_PAYLOAD_BYTES)
            client.send(payload, MsgType.CHAT, "7", 1) { ok, _ -> results += ok }
            until { results.isNotEmpty() }
            assertEquals(listOf(false), results.toList())
            assertFalse(driver.sent.any { it.second.type == MsgType.CHECK || it.second.type == MsgType.CHAT })
        } finally { client.stop() }
    }

    @Test fun failedFirstWriteAfterInitializationCompletesEveryQueuedSend() {
        val driver = Driver(); val client = client(driver)
        val results = CopyOnWriteArrayList<Boolean>()
        try {
            authenticate(client, driver)
            val before = driver.polls.get()
            for (id in listOf("first", "second")) {
                client.send(buildChatPayload("7", "fixture", timestamp = 1, messageId = id), MsgType.CHAT, "7", 1) { ok, _ -> results += ok }
            }
            until { driver.polls.get() >= before + 3 }
            driver.failChatWrite = true
            initialize(driver)
            until { results.size == 2 }
            client.stop(); until { driver.closed }
            assertEquals(listOf(false, false), results.toList())
        } finally { client.stop() }
    }
}
