package cli

import core.IndexedHistoryJournal
import com.chatlite.proto.MessageProtos
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Base64

@Serializable
data class CliPending(val id: String, val account: Int, val payload: String, val createdAt: Long,
    val attempts: Int = 0, val nextAttemptAt: Long = createdAt, val status: String = "QUEUED") {
    fun wrapper(): MessageProtos.MessageWrapper = MessageProtos.MessageWrapper.parseFrom(Base64.getDecoder().decode(payload))
}

/** Bounded, account/server-scoped durable queue. Completing an entry writes a journal tombstone. */
class CliOutbox(directory: File, private val account: Int, private val clock: () -> Long = System::currentTimeMillis) {
    private val journal = IndexedHistoryJournal.open(File(directory, "outbox/$account.history"))
    private val json = Json
    private var cached: MutableMap<String, CliPending>? = null
    @Synchronized fun entries(): List<CliPending> {
        check(journal.size() <= 128) { "待发队列超出上限" }
        cached?.takeIf { it.size == journal.size() }?.let { return it.values.toList() }
        return journal.page(0, 128).map { json.decodeFromString<CliPending>(checkNotNull(it.body)).also { row ->
            check(row.account == account && row.id == it.id) { "待发队列账号或 ID 不匹配" }
            check(row.payload.length <= 90_000) { "待发载荷过大" }
            val wrapper = row.wrapper()
            val id = if (wrapper.hasGroupChat()) wrapper.groupChat.messageId else wrapper.chat.messageId
            check(id == row.id) { "待发载荷 ID 不匹配" }
            validate(wrapper)
        } }.also { rows -> cached = rows.associateBy { it.id }.toMutableMap() }
    }
    @Synchronized fun enqueue(wrapper: MessageProtos.MessageWrapper): CliPending {
        validate(wrapper)
        check(journal.size() < 128) { "待发队列已满，请先查看 /pending" }
        val id = if (wrapper.hasGroupChat()) wrapper.groupChat.messageId else wrapper.chat.messageId
        check(id.isNotBlank() && journal.read(id) == null)
        return CliPending(id, account, Base64.getEncoder().encodeToString(wrapper.toByteArray()), clock()).also(::save)
    }
    @Synchronized fun accepted(id: String) {
        entries()
        if (cached!!.containsKey(id)) {
            journal.write(listOf(IndexedHistoryJournal.Row(id, 0, null)))
            cached!!.remove(id)
        }
    }
    @Synchronized fun failed(id: String, rejected: Boolean = false) {
        val current = entries().firstOrNull { it.id == id } ?: return
        if (current.status != "QUEUED") return
        val attempts = current.attempts + 1
        val expired = clock() - current.createdAt >= 30 * 60_000
        save(current.copy(attempts = attempts, status = if (rejected) "REJECTED" else if (expired) "REVIEW" else "QUEUED",
            nextAttemptAt = clock() + (1_000L shl attempts.coerceAtMost(5)).coerceAtMost(30_000)))
    }
    @Synchronized fun due(): List<CliPending> = entries().filter { entry ->
        if (entry.status == "QUEUED" && clock() - entry.createdAt >= 30 * 60_000) {
            save(entry.copy(status = "REVIEW")); false
        } else entry.status == "QUEUED" && entry.nextAttemptAt <= clock()
    }.sortedBy { it.createdAt }
    private fun save(entry: CliPending) {
        journal.write(listOf(IndexedHistoryJournal.Row(entry.id, entry.createdAt, json.encodeToString(entry))))
        cached?.set(entry.id, entry)
    }
    private fun validate(wrapper: MessageProtos.MessageWrapper) {
        check(wrapper.type == core.MsgType.CHAT || wrapper.type == core.MsgType.GROUP_CHAT)
        val sender = if (wrapper.hasGroupChat()) wrapper.groupChat.userId else wrapper.chat.userId
        check(sender == account.toLong()) { "待发载荷不属于当前账号" }
        check(wrapper.serializedSize <= core.MessageProtocol.QUIC_SERVER_MAX_PAYLOAD_BYTES)
    }
}
