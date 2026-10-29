package core

import com.chatlite.proto.MessageProtos
import kotlin.test.*

class DurableReceiveTest {
    @Test fun overloadIsRetriableAndDoesNotInvalidateCredentials() {
        assertTrue(isTransientGatewayResponse("SERVER_BUSY"))
        assertTrue(isTransientGatewayResponse("OWNER_UNAVAILABLE"))
        assertTrue(isTransientGatewayResponse("HANDSHAKE_RATE_LIMITED:60000"))
        assertEquals(60000L, handshakeRetryAfterMillis("HANDSHAKE_RATE_LIMITED:60000"))
        assertEquals(1L, handshakeRetryAfterMillis("HANDSHAKE_RATE_LIMITED:0"))
        assertNull(handshakeRetryAfterMillis("HANDSHAKE_RATE_LIMITED:bad"))
        assertFalse(isTransientGatewayResponse("token invalid"))
    }

    @Test fun canonicalAckCannotCompleteSendBeforeAliasPersistenceSucceeds() {
        var failDisk = true
        var completed = 0
        val dispatcher = ProtobufMessageDispatcher(listeners = { emptyList() }, persistIncoming = {
            if (failDisk) throw java.io.IOException("disk full")
        }, onAcknowledgement = { _, _ -> completed++ })
        val ack = MessageProtos.AckMessage.newBuilder().setMessageId("local_fixture").setSuccess(true)
            .setServerMessageId("9007199254740999").setSeqId(17).setConversationId("1:42")
        val data = MessageProtos.MessageWrapper.newBuilder().setType(MsgType.ACK).setAck(ack).build().toByteArray()
        dispatcher.dispatch(data) { _, _ -> }
        assertEquals(0, completed)
        failDisk = false
        dispatcher.dispatch(data) { _, _ -> }
        assertEquals(1, completed)
    }

    @Test fun failedDiskWriteIsNotAcknowledgedOrRememberedAndRetryKeepsServerId() {
        var failing = true
        var received = ""
        var acknowledgements = 0
        val listener = object : MessageReceiveListener {
            override fun onPrivateMessageReceived(senderId: Int, message: String, timestamp: Long, seqId: Long, conversationId: String) {}
            override fun onGroupMessageReceived(groupId: Int, senderId: Int, senderName: String, message: String, timestamp: Long, seqId: Long, conversationId: String) {}
            override fun onPrivateEnvelope(message: model.Message) { received = message.messageId }
        }
        val dispatcher = ProtobufMessageDispatcher(listeners = { listOf(listener) },
            persistIncoming = { if (failing) throw java.io.IOException("disk full") }, accountId = { 42 })
        val data = MessageProtos.MessageWrapper.newBuilder().setType(MsgType.CHAT)
            .setChat(MessageProtos.ChatMessage.newBuilder().setMessageId("server-id").setUserId(1).setTargetClientId(42)).build().toByteArray()
        dispatcher.dispatch(data) { _, _ -> acknowledgements++ }
        assertEquals(0, acknowledgements); assertEquals("", received)
        failing = false
        dispatcher.dispatch(data) { _, _ -> acknowledgements++ }
        assertEquals(1, acknowledgements); assertEquals("server-id", received)
    }

    @Test fun gatewayAddressesAreValidated() {
        assertEquals("example.test" to 8080, parseGatewayEndpoint("example.test:8080"))
        assertEquals("::1" to 8080, parseGatewayEndpoint("quic://[::1]:8080"))
        assertFailsWith<IllegalArgumentException> { parseGatewayEndpoint("https://user:pass@example.test:8080") }
    }
}
