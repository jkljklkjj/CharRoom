package core

import com.chatlite.proto.AgentStreamProtos.AgentStreamType
import com.chatlite.proto.MessageProtos

/**
 * Decode and deduplicate a packet once, then deliver it to every listener. No transport/UI state.
 */
class ProtobufMessageDispatcher(
    private val listeners: () -> List<MessageReceiveListener>,
    private val agentHandler: () -> AgentStreamHandler? = { null },
    private val onResponse: (MessageProtos.ResponseMessage) -> Unit = {},
    private val onAcknowledgement: (MessageProtos.AckMessage, ByteArray) -> Unit = { _, _ -> },
    private val onFailure: (Exception) -> Unit = {},
    private val persistIncoming: (MessageProtos.MessageWrapper) -> Unit = {},
    private val accountId: () -> Int = { 0 },
) {
    private val recent =
        object : LinkedHashMap<String, Boolean>(16, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, Boolean>?
            ): Boolean = size > MessageProtocol.RECENT_MESSAGE_LIMIT
        }

    private val agentText = linkedMapOf<Long, String>()

    @Synchronized
    fun clear() {
        synchronized(recent) { recent.clear() }
        agentText.clear()
    }

    private fun seen(key: String): Boolean = synchronized(recent) { recent[key] == true }

    private fun remember(key: String) = synchronized(recent) { recent[key] = true }

    private fun safely(action: () -> Unit) {
        core.recoverOperation(
            onFailure = { error ->
                onFailure(error)
            }
        ) {
            action()
        }
    }

    private fun notifyListeners(action: (MessageReceiveListener) -> Unit): Boolean {
        var delivered = false
        listeners().forEach { listener ->
            safely {
                action(listener)
                delivered = true
            }
        }
        return delivered
    }

    @Synchronized
    fun dispatch(data: ByteArray, acknowledge: (String, String) -> Unit) = safely {
        val wrapper = MessageProtos.MessageWrapper.parseFrom(data)
        when (wrapper.type) {
            MsgType.RESPONSE -> receiveResponse(wrapper)
            MsgType.ACK -> receiveAck(wrapper, data)
            MsgType.CHAT,
            MsgType.AGENT_CHAT -> receivePrivate(wrapper, acknowledge)
            MsgType.GROUP_CHAT -> receiveGroup(wrapper, acknowledge)
            MsgType.AGENT_CHAT_STREAM -> receiveAgent(wrapper, acknowledge)
            MsgType.SYNC_HINT -> receiveSyncHint(wrapper)
            else -> Unit
        }
    }

    private fun receiveResponse(wrapper: MessageProtos.MessageWrapper) {
        if (wrapper.hasResponse()) onResponse(wrapper.response)
    }

    private fun receiveAck(wrapper: MessageProtos.MessageWrapper, data: ByteArray) {
        if (!wrapper.hasAck()) return
        val ack = wrapper.ack
        val hasIdentity = ack.serverMessageId.isNotBlank() && ack.seqId > 0
        if (ack.success && hasIdentity) {
            persistIncoming(wrapper)
            notifyListeners {
                it.onMessageIdConfirmed(
                    ack.messageId,
                    ack.serverMessageId,
                    ack.seqId,
                    ack.conversationId,
                )
            }
        }
        onAcknowledgement(ack, data)
        if (ack.success)
            notifyListeners { it.onAckReceived(ack.messageId, ack.seqId, ack.conversationId) }
        else notifyListeners { it.onAckRejected(ack.messageId, ack.message) }
    }

    private fun receivePrivate(
        wrapper: MessageProtos.MessageWrapper,
        acknowledge: (String, String) -> Unit,
    ) {
        if (!wrapper.hasChat()) return
        val chat = wrapper.chat
        require(chat.messageId.isNotBlank()) { "Missing private message ID" }
        deliverMessage(
            "chat:${chat.messageId}",
            chat.messageId,
            chat.conversationId,
            wrapper,
            acknowledge,
        ) {
            it.onPrivateEnvelope(chat.localMessage(accountId()))
        }
    }

    private fun receiveGroup(
        wrapper: MessageProtos.MessageWrapper,
        acknowledge: (String, String) -> Unit,
    ) {
        if (!wrapper.hasGroupChat()) return
        val chat = wrapper.groupChat
        require(chat.messageId.isNotBlank()) { "Missing group message ID" }
        deliverMessage(
            "group:${chat.messageId}",
            chat.messageId,
            chat.conversationId,
            wrapper,
            acknowledge,
        ) {
            it.onGroupEnvelope(chat.localMessage())
        }
    }

    private fun deliverMessage(
        key: String,
        id: String,
        conversation: String,
        wrapper: MessageProtos.MessageWrapper,
        acknowledge: (String, String) -> Unit,
        delivery: (MessageReceiveListener) -> Unit,
    ) {
        if (seen(key)) {
            safely { acknowledge(id, conversation) }
            return
        }
        persistIncoming(wrapper)
        if (notifyListeners(delivery)) {
            remember(key)
            safely { acknowledge(id, conversation) }
        }
    }

    private fun receiveAgent(
        wrapper: MessageProtos.MessageWrapper,
        acknowledge: (String, String) -> Unit,
    ) {
        if (!wrapper.hasAgentStream()) return
        val stream = wrapper.agentStream
        val terminal =
            stream.type == AgentStreamType.STREAM_DONE ||
                stream.type == AgentStreamType.STREAM_ERROR
        val terminalKey = "agent-end:${stream.requestId}"
        val key =
            if (terminal) terminalKey
            else "agent:${stream.requestId}:${stream.typeValue}:${stream.seq}"
        if (skipAgent(stream.requestId, key, terminalKey, terminal, acknowledge)) return
        persistIncoming(wrapper)
        val text = agentText[stream.requestId].orEmpty() + streamText(stream)
        if (deliverAgent(stream, text)) {
            agentText[stream.requestId] = text
            while (agentText.size > 200) agentText.remove(agentText.keys.first())
            remember(key)
            if (terminal) safely { acknowledge(stream.requestId.toString(), "agent") }
        }
    }

    private fun skipAgent(
        id: Long,
        key: String,
        terminalKey: String,
        terminal: Boolean,
        acknowledge: (String, String) -> Unit,
    ): Boolean {
        if (terminal && seen(terminalKey)) {
            safely { acknowledge(id.toString(), "agent") }
            return true
        }
        return !terminal && (seen(terminalKey) || seen(key))
    }

    private fun streamText(stream: com.chatlite.proto.AgentStreamProtos.AgentStreamChunk): String =
        when (stream.type) {
            AgentStreamType.STREAM_TEXT -> stream.text
            AgentStreamType.STREAM_ERROR -> "\n" + stream.error.message
            else -> ""
        }

    private fun deliverAgent(
        stream: com.chatlite.proto.AgentStreamProtos.AgentStreamChunk,
        text: String,
    ): Boolean {
        val done = stream.type == AgentStreamType.STREAM_DONE
        val error = stream.type == AgentStreamType.STREAM_ERROR
        val handler = agentHandler()
        return if (handler != null) {
            handler.onAgentStreamChunk(stream.requestId.toString(), text, done, error)
            true
        } else
            notifyListeners {
                it.onAgentStreamChunk(
                    stream.requestId.toString(),
                    text,
                    done,
                    error,
                    stream.typeValue,
                    if (stream.hasToolCall()) stream.toolCall.name else null,
                    if (stream.hasToolResult()) stream.toolResult.result else null,
                    if (stream.hasUsage()) stream.usage.inputTokens else 0,
                    if (stream.hasUsage()) stream.usage.outputTokens else 0,
                )
            }
    }

    private fun receiveSyncHint(wrapper: MessageProtos.MessageWrapper) {
        if (!wrapper.hasAck()) return
        val ack = wrapper.ack
        val seq = ack.message.toLongOrNull() ?: 0L
        if (ack.clientId.isNotBlank() && seq > 0)
            notifyListeners { it.onSyncHint(ack.clientId, seq) }
    }
}
