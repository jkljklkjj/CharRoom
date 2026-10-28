package core

import com.chatlite.proto.AgentStreamProtos.AgentStreamType
import com.chatlite.proto.MessageProtos
import java.util.concurrent.CancellationException

/** Decode and deduplicate a packet once, then deliver it to every listener. No transport/UI state. */
class ProtobufMessageDispatcher(
    private val listeners: () -> List<MessageReceiveListener>,
    private val agentHandler: () -> AgentStreamHandler? = { null },
    private val onResponse: (MessageProtos.ResponseMessage) -> Unit = {},
    private val onAcknowledgement: (MessageProtos.AckMessage, ByteArray) -> Unit = { _, _ -> },
    private val onFailure: (Exception) -> Unit = {},
    private val persistIncoming: (MessageProtos.MessageWrapper) -> Unit = {},
    private val accountId: () -> Int = { 0 }
) {
    private val recent = object : LinkedHashMap<String, Boolean>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean =
            size > MessageProtocol.RECENT_MESSAGE_LIMIT
    }

    private val agentText = linkedMapOf<Long, String>()
    @Synchronized fun clear() { synchronized(recent) { recent.clear() }; agentText.clear() }

    private fun seen(key: String): Boolean = synchronized(recent) { recent[key] == true }
    private fun remember(key: String) = synchronized(recent) { recent[key] = true }

    private fun safely(action: () -> Unit) {
        try { action() } catch (error: Exception) {
            if (error is CancellationException) throw error
            onFailure(error)
        }
    }

    private fun notifyListeners(action: (MessageReceiveListener) -> Unit): Boolean {
        var delivered = false
        listeners().forEach { listener -> safely { action(listener); delivered = true } }
        return delivered
    }

    @Synchronized
    fun dispatch(data: ByteArray, acknowledge: (String, String) -> Unit) = safely {
        val wrapper = MessageProtos.MessageWrapper.parseFrom(data)
        when (wrapper.type) {
            MsgType.RESPONSE -> if (wrapper.hasResponse()) onResponse(wrapper.response)
            MsgType.ACK -> if (wrapper.hasAck()) {
                val ack = wrapper.ack
                if ((ack.success) && ack.serverMessageId.isNotBlank() && ack.seqId > 0) {
                    persistIncoming(wrapper)
                    notifyListeners { it.onMessageIdConfirmed(ack.messageId, ack.serverMessageId, ack.seqId, ack.conversationId) }
                }
                onAcknowledgement(ack, data)
                if (!ack.success) {
                    notifyListeners { it.onAckRejected(ack.messageId, ack.message) }
                } else {
                    notifyListeners { it.onAckReceived(ack.messageId, ack.seqId, ack.conversationId) }
                }
            }
            MsgType.CHAT, MsgType.AGENT_CHAT -> if (wrapper.hasChat()) {
                val chat = wrapper.chat
                require(chat.messageId.isNotBlank()) { "Missing private message ID" }
                val key = "chat:${chat.messageId}"
                if (seen(key)) {
                    safely { acknowledge(chat.messageId, chat.conversationId) }
                    return@safely
                }
                persistIncoming(wrapper)
                val delivered = notifyListeners { it.onPrivateEnvelope(chat.localMessage(accountId())) }
                if (delivered) {
                    remember(key)
                    safely { acknowledge(chat.messageId, chat.conversationId) }
                }
            }
            MsgType.GROUP_CHAT -> if (wrapper.hasGroupChat()) {
                val chat = wrapper.groupChat
                require(chat.messageId.isNotBlank()) { "Missing group message ID" }
                val key = "group:${chat.messageId}"
                if (seen(key)) {
                    safely { acknowledge(chat.messageId, chat.conversationId) }
                    return@safely
                }
                persistIncoming(wrapper)
                val delivered = notifyListeners { it.onGroupEnvelope(chat.localMessage()) }
                if (delivered) {
                    remember(key)
                    safely { acknowledge(chat.messageId, chat.conversationId) }
                }
            }
            MsgType.AGENT_CHAT_STREAM -> if (wrapper.hasAgentStream()) {
                val stream = wrapper.agentStream
                val done = stream.type == AgentStreamType.STREAM_DONE
                val error = stream.type == AgentStreamType.STREAM_ERROR
                val terminalKey = "agent-end:${stream.requestId}"
                val key = if (done || error) terminalKey else "agent:${stream.requestId}:${stream.typeValue}:${stream.seq}"
                if (done || error) {
                    if (seen(terminalKey)) {
                        safely { acknowledge(stream.requestId.toString(), "agent") }
                        return@safely
                    }
                } else {
                    if (seen(terminalKey) || seen(key)) return@safely
                }
                persistIncoming(wrapper)
                val previous = agentText[stream.requestId].orEmpty()
                val text = previous + when (stream.type) {
                    AgentStreamType.STREAM_TEXT -> stream.text
                    AgentStreamType.STREAM_ERROR -> "\n" + stream.error.message
                    else -> ""
                }
                val handler = agentHandler()
                val delivered = if (handler != null) {
                    handler.onAgentStreamChunk(stream.requestId.toString(), text, done, error)
                    true
                } else {
                    notifyListeners { it.onAgentStreamChunk(stream.requestId.toString(), text, done, error,
                        stream.typeValue, if (stream.hasToolCall()) stream.toolCall.name else null,
                        if (stream.hasToolResult()) stream.toolResult.result else null,
                        if (stream.hasUsage()) stream.usage.inputTokens else 0,
                        if (stream.hasUsage()) stream.usage.outputTokens else 0) }
                }
                if (delivered) {
                    agentText[stream.requestId] = text
                    while (agentText.size > 200) agentText.remove(agentText.keys.first())
                    remember(key)
                    if (done || error) safely { acknowledge(stream.requestId.toString(), "agent") }
                }
            }
            MsgType.SYNC_HINT -> if (wrapper.hasAck()) {
                val ack = wrapper.ack
                val seqId = ack.message.toLongOrNull() ?: 0L
                if (ack.clientId.isNotBlank() && seqId > 0) notifyListeners { it.onSyncHint(ack.clientId, seqId) }
            }
            else -> Unit
        }
    }
}
