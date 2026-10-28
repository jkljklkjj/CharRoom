package cli

import core.ChatTransport
import kotlinx.coroutines.*

class CliDeliveryQueue(private val outbox: CliOutbox, private val sender: CliMessageSender,
    private val transport: ChatTransport, private val notify: (String) -> Unit) {
    private val wake = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    fun request() { wake.trySend(Unit) }
    suspend fun run() {
        while (currentCoroutineContext().isActive) {
            drainOnce()
            withTimeoutOrNull(1000) { wake.receive() }
        }
    }
    suspend fun drainOnce() {
        if (transport.isConnected()) {
            coroutineScope {
                outbox.due().take(4).map { pending -> launch {
                    try {
                        when (sender.attempt(pending.wrapper())) {
                            CliSendResult.ACCEPTED -> outbox.accepted(pending.id)
                            CliSendResult.REJECTED -> { outbox.failed(pending.id, rejected = true); notify("! 消息被拒绝，使用 /pending 查看") }
                            CliSendResult.UNCONFIRMED -> {
                                outbox.failed(pending.id)
                                if (pending.attempts == 0) notify("! 未收到接收确认，结果暂不确定；已保留原 ID 重试，/pending 查看。")
                            }
                        }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        outbox.failed(pending.id)
                        notify("待发消息暂未完成，原 ID 已保留。")
                    }
                } }.joinAll()
            }
        }
    }
}
