package cli

import com.chatlite.proto.MessageProtos
import core.ChatTransport
import core.MsgType
import core.buildChatPayload
import core.buildGroupChatPayload
import kotlinx.coroutines.*
import model.MessageIdGenerator

enum class CliSendResult { ACCEPTED, REJECTED, UNCONFIRMED }

/** One attempt, one request ID. An absent ACK is ambiguous and never causes a blind resend. */
class CliMessageSender(
    private val transport: ChatTransport,
    private val persist: (MessageProtos.MessageWrapper, failed: Boolean) -> Unit,
    private val showSlow: () -> Unit,
    private val slowAfter: Long = 4_000,
    private val ackTimeout: Long = 10_000
) {
    suspend fun send(account: Int, target: Int, text: String, group: Boolean): CliSendResult = coroutineScope {
        check(transport.isConnected()) { "实时连接尚未认证，请连接恢复后再发送" }
        require(account > 0 && target > 0)
        val id = MessageIdGenerator.nextId()
        val now = System.currentTimeMillis()
        val bytes = if (group) buildGroupChatPayload(target.toString(), text, account, messageId = id, timestamp = now)
            else buildChatPayload(target.toString(), text, account, timestamp = now, messageId = id)
        val wrapper = MessageProtos.MessageWrapper.parseFrom(bytes)
        require(bytes.size <= core.MessageProtocol.QUIC_SERVER_MAX_PAYLOAD_BYTES) { "消息超过协议大小限制" }
        persist(wrapper, false)
        val completion = CompletableDeferred<CliSendResult>()
        val notice = launch { delay(slowAfter); if (!completion.isCompleted) showSlow() }
        try {
            try {
                transport.send(bytes, if (group) MsgType.GROUP_CHAT else MsgType.CHAT, target.toString(), 1) { success, responses ->
                    completion.complete(if (success) CliSendResult.ACCEPTED else if (responses.isNotEmpty()) CliSendResult.REJECTED else CliSendResult.UNCONFIRMED)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                persist(wrapper, true)
                throw error
            }
            val result = withTimeoutOrNull(ackTimeout) { completion.await() } ?: CliSendResult.UNCONFIRMED
            if (result != CliSendResult.ACCEPTED) persist(wrapper, true)
            result
        } finally {
            completion.cancel()
            notice.cancelAndJoin()
        }
    }
}
