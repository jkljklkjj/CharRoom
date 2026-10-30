package core

import com.chatlite.proto.MessageProtos

/** Correlates send callbacks with server ACKs; the transport owns and cancels the timeout jobs. */
class PendingMessageAcks(
    private val schedule: (Long, () -> Unit) -> (() -> Unit),
    private val onCallbackFailure: (Exception) -> Unit = {},
) {
    private class Pending(
        val id: String,
        val callbacks: MutableList<(Boolean, List<ByteArray>) -> Unit>,
    ) {
        var cancel: (() -> Unit)? = null
    }

    private val pending = mutableMapOf<String, Pending>()

    fun await(messageId: String, callback: (Boolean, List<ByteArray>) -> Unit) {
        require(messageId.isNotBlank()) { "A messageId is required for delivery acknowledgement" }
        val entry =
            synchronized(pending) {
                pending[messageId]?.let {
                    it.callbacks.add(callback)
                    return
                }
                Pending(messageId, mutableListOf(callback)).also { pending[messageId] = it }
            }
        val cancel =
            schedule(MessageProtocol.ACK_TIMEOUT_MILLIS) { complete(entry, false, emptyList()) }
        synchronized(pending) {
            if (pending[messageId] === entry) entry.cancel = cancel else cancel()
        }
    }

    fun acknowledge(ack: MessageProtos.AckMessage, bytes: ByteArray) {
        val entry = synchronized(pending) { pending[ack.messageId] } ?: return
        complete(entry, ack.success, listOf(bytes))
    }

    fun fail(messageId: String) {
        val entry = synchronized(pending) { pending[messageId] } ?: return
        complete(entry, false, emptyList())
    }

    fun failAll() {
        val entries = synchronized(pending) { pending.values.toList() }
        entries.forEach { complete(it, false, emptyList()) }
    }

    private fun complete(entry: Pending, success: Boolean, responses: List<ByteArray>) {
        val callbacks =
            synchronized(pending) {
                if (pending[entry.id] !== entry) return
                pending.remove(entry.id)
                entry.cancel?.invoke()
                entry.callbacks.toList()
            }
        callbacks.forEach { callback ->
            core.recoverOperation(
                onFailure = { error ->
                    onCallbackFailure(error)
                }
            ) {
                callback(success, responses)
            }
        }
    }
}
