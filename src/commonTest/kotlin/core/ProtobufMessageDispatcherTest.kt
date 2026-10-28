package core

import com.chatlite.proto.AgentStreamProtos
import com.chatlite.proto.MessageProtos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtobufMessageDispatcherTest {
    private class Listener : MessageReceiveListener {
        val privateMessages = mutableListOf<String>()
        val groupTimestamps = mutableListOf<Long>()
        var acks = 0
        var rejections = 0
        var hints = 0
        override fun onPrivateMessageReceived(senderId: Int, message: String, timestamp: Long, seqId: Long, conversationId: String) {
            privateMessages += message
        }
        override fun onGroupMessageReceived(groupId: Int, senderId: Int, senderName: String, message: String, timestamp: Long, seqId: Long, conversationId: String) {
            groupTimestamps += timestamp
        }
        override fun onAckReceived(messageId: String, seqId: Long, conversationId: String) { acks++ }
        override fun onAckRejected(messageId: String, reason: String) { rejections++ }
        override fun onSyncHint(conversationId: String, seqId: Long) { hints++ }
    }

    @Test
    fun deduplicationDoesNotDropOtherListenersAndDuplicateDeliveryIsAcknowledgedAgain() {
        val first = Listener(); val second = Listener(); var ackCount = 0
        val dispatcher = ProtobufMessageDispatcher({ listOf(first, second) })
        val bytes = buildChatPayload("42", "fixture", timestamp = 123L, messageId = "packet")
        repeat(2) { dispatcher.dispatch(bytes) { _, _ -> ackCount++ } }
        assertEquals(listOf("fixture"), first.privateMessages)
        assertEquals(listOf("fixture"), second.privateMessages)
        assertEquals(2, ackCount)
    }

    @Test
    fun messagesWithoutIdsAreRejectedWithoutDeliveryOrAcknowledgement() {
        val listener = Listener()
        val dispatcher = ProtobufMessageDispatcher({ listOf(listener) })
        var acknowledgements = 0
        for (text in listOf("first", "second")) {
            dispatcher.dispatch(buildChatPayload("42", text, timestamp = 123L, messageId = "")) { _, _ -> acknowledgements++ }
        }
        assertTrue(listener.privateMessages.isEmpty())
        assertEquals(0, acknowledgements)
    }

    @Test
    fun aMessageWithNoConsumerIsNotAcknowledgedOrRememberedAsDelivered() {
        val consumers = mutableListOf<MessageReceiveListener>()
        val listener = Listener(); var ackCount = 0
        val dispatcher = ProtobufMessageDispatcher({ consumers.toList() })
        val bytes = buildChatPayload("42", "fixture", timestamp = 123L, messageId = "packet")
        dispatcher.dispatch(bytes) { _, _ -> ackCount++ }
        assertEquals(0, ackCount)
        consumers.add(listener)
        dispatcher.dispatch(bytes) { _, _ -> ackCount++ }
        assertEquals(listOf("fixture"), listener.privateMessages)
        assertEquals(1, ackCount)
    }

    @Test
    fun groupDispatchPreservesTheServerTimestampForEveryListener() {
        val first = Listener(); val second = Listener()
        val dispatcher = ProtobufMessageDispatcher({ listOf(first, second) })
        dispatcher.dispatch(buildGroupChatPayload("7", "fixture", timestamp = 123L)) { _, _ -> }
        assertEquals(listOf(123L), first.groupTimestamps)
        assertEquals(first.groupTimestamps, second.groupTimestamps)
    }

    @Test
    fun loginAndAgentHandlerWorkWithoutUiListenersAndLateAgentChunksAreIgnored() {
        var loginCount = 0
        val chunks = mutableListOf<String>()
        val dispatcher = ProtobufMessageDispatcher({ emptyList() },
            agentHandler = { AgentStreamHandler { _, text, _, _ -> chunks += text } },
            onResponse = { loginCount++ })
        dispatcher.dispatch(MessageProtos.MessageWrapper.newBuilder().setType(MsgType.RESPONSE)
            .setResponse(MessageProtos.ResponseMessage.newBuilder().setSuccess(true)).build().toByteArray()) { _, _ -> }
        fun chunk(seq: Int, type: AgentStreamProtos.AgentStreamType, text: String): ByteArray =
            MessageProtos.MessageWrapper.newBuilder().setType(MsgType.AGENT_CHAT_STREAM)
                .setAgentStream(AgentStreamProtos.AgentStreamChunk.newBuilder().setRequestId(99L)
                    .setSeq(seq).setType(type).setText(text)).build().toByteArray()
        for (seq in listOf(1, 1, 2)) dispatcher.dispatch(chunk(seq, AgentStreamProtos.AgentStreamType.STREAM_TEXT, "!")) { _, _ -> }
        dispatcher.dispatch(chunk(3, AgentStreamProtos.AgentStreamType.STREAM_DONE, "")) { _, _ -> }
        dispatcher.dispatch(chunk(4, AgentStreamProtos.AgentStreamType.STREAM_TEXT, "late")) { _, _ -> }
        assertEquals(1, loginCount)
        assertEquals(listOf("!", "!!", "!!"), chunks)
    }

    @Test
    fun rejectedAcksAndSyncHintsRemainDifferentFromSuccessfulDelivery() {
        val listener = Listener(); var correlations = 0
        val dispatcher = ProtobufMessageDispatcher({ listOf(listener) }, onAcknowledgement = { _, _ -> correlations++ })
        for (success in listOf(true, false)) dispatcher.dispatch(MessageProtos.MessageWrapper.newBuilder()
            .setType(MsgType.ACK).setAck(MessageProtos.AckMessage.newBuilder().setMessageId("packet").setSuccess(success))
            .build().toByteArray()) { _, _ -> }
        dispatcher.dispatch(MessageProtos.MessageWrapper.newBuilder().setType(MsgType.SYNC_HINT)
            .setAck(MessageProtos.AckMessage.newBuilder().setClientId("user:1:2").setMessage("9"))
            .build().toByteArray()) { _, _ -> }
        assertEquals(2, correlations)
        assertEquals(1, listener.acks)
        assertEquals(1, listener.rejections)
        assertEquals(1, listener.hints)
    }

    @Test
    fun oneFailingListenerDoesNotPreventAnotherListenerFromReceiving() {
        val good = Listener(); val errors = mutableListOf<Exception>()
        val bad = object : MessageReceiveListener {
            override fun onPrivateMessageReceived(senderId: Int, message: String, timestamp: Long, seqId: Long, conversationId: String) = error("fixture")
            override fun onGroupMessageReceived(groupId: Int, senderId: Int, senderName: String, message: String, timestamp: Long, seqId: Long, conversationId: String) {}
        }
        val dispatcher = ProtobufMessageDispatcher({ listOf(bad, good) }, onFailure = { errors += it })
        dispatcher.dispatch(buildChatPayload("42", "fixture", timestamp = 123L)) { _, _ -> }
        assertEquals(listOf("fixture"), good.privateMessages)
        assertTrue(errors.isNotEmpty())
    }
}
