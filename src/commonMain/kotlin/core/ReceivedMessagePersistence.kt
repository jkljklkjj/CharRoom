package core

import com.chatlite.proto.AgentStreamProtos.AgentStreamType
import com.chatlite.proto.MessageProtos
import model.GroupMessage
import model.Message
import model.MessageType

internal fun MessageProtos.ChatMessage.localMessage(account: Int) =
    Message(
        senderId = userId.toInt(),
        receiverId = targetClientId.toInt(),
        sender = userId == account.toLong(),
        message = content,
        timestamp = timestamp,
        messageId = messageId,
        seqId = seqId,
        conversationId = conversationId,
        replyToMessageId = replyToMessageId,
        replyToContent = replyToContent,
        replyToSender = replyToSender,
        messageType = MessageType.entries.getOrElse(messageTypeValue) { MessageType.TEXT },
        fileUrl = fileUrl,
        fileName = fileName,
        fileSize = fileSize,
    )

internal fun MessageProtos.GroupChatMessage.localMessage() =
    GroupMessage(
        groupId = targetClientId.toInt(),
        senderId = userId.toInt(),
        senderName = userId.toString(),
        text = content,
        timestamp = timestamp,
        messageId = messageId,
        seqId = seqId,
        conversationId = conversationId,
        replyToMessageId = replyToMessageId,
        replyToContent = replyToContent,
        replyToSender = replyToSender,
        messageType = MessageType.entries.getOrElse(messageTypeValue) { MessageType.TEXT },
        fileUrl = fileUrl,
        fileName = fileName,
        fileSize = fileSize,
    )

/**
 * Called on the transport's serial IO consumer before UI callbacks, deduplication, or receipt ACK.
 */
class ReceivedMessagePersistence(
    private val store: () -> LocalChatHistoryStoreProvider = { LocalChatHistoryStore }
) {
    private data class Stream(
        var text: String = "",
        var terminal: Boolean = false,
        val seen: MutableSet<Long> = mutableSetOf(),
    )

    private val streams = linkedMapOf<String, Stream>()

    @Synchronized
    fun persist(account: Int, wrapper: MessageProtos.MessageWrapper) {
        require(account > 0)
        when (wrapper.type) {
            MsgType.ACK -> confirm(account, wrapper.ack)
            MsgType.CHAT,
            MsgType.AGENT_CHAT ->
                store()
                    .save(
                        account.toString(),
                        listOf(wrapper.chat.localMessage(account)),
                        emptyList(),
                    )
            MsgType.GROUP_CHAT ->
                store()
                    .save(account.toString(), emptyList(), listOf(wrapper.groupChat.localMessage()))
            MsgType.AGENT_CHAT_STREAM -> persistAgent(account, wrapper.agentStream)
            else -> Unit
        }
    }

    private fun confirm(account: Int, ack: MessageProtos.AckMessage) {
        val hasIdentity = ack.serverMessageId.isNotBlank() && ack.conversationId.isNotBlank()
        val accepted = ack.success && ack.seqId > 0
        if (accepted && hasIdentity) store().confirmMessageId(account.toString(), ack)
    }

    private fun persistAgent(
        account: Int,
        chunk: com.chatlite.proto.AgentStreamProtos.AgentStreamChunk,
    ) {
        val key = "$account:${chunk.requestId}"
        val state = streams.getOrPut(key) { Stream() }
        val terminal =
            chunk.type == AgentStreamType.STREAM_DONE || chunk.type == AgentStreamType.STREAM_ERROR
        accumulate(state, chunk, terminal)
        if (terminal) saveAgentSnapshot(account, chunk.requestId, state.text)
        while (streams.size > 200) streams.remove(streams.keys.first())
    }

    private fun saveAgentSnapshot(account: Int, request: Long, text: String) {
        store()
            .save(
                account.toString(),
                listOf(
                    Message(
                        senderId = ServerConfig.AGENT_ASSISTANT_ID,
                        receiverId = account,
                        timestamp = System.currentTimeMillis(),
                        messageId = request.toString(),
                        message = text,
                    )
                ),
                emptyList(),
            )
    }

    private fun accumulate(
        state: Stream,
        chunk: com.chatlite.proto.AgentStreamProtos.AgentStreamChunk,
        terminal: Boolean,
    ) {
        if (!state.terminal && state.seen.add(chunk.seq.toLong())) {
            when (chunk.type) {
                AgentStreamType.STREAM_TEXT -> state.text += chunk.text
                AgentStreamType.STREAM_ERROR -> state.text += "\n" + chunk.error.message
                else -> Unit
            }
            if (terminal) state.terminal = true
        }
    }
}
