package core

import com.chatlite.proto.MessageProtos
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Persist aliases before completing a send; late history snapshots must retain the canonical ID. */
object MessageIdAliases {
    @Serializable data class Entry(val serverId: String, val seq: Long, val conversation: String)
    private val json = Json { ignoreUnknownKeys = true }
    private fun journal(directory: File, target: String, group: Boolean): IndexedHistoryJournal {
        val stem = (if (group) "group_" else "private_") + target
        val folder = File(directory, "message-id-aliases")
        return IndexedHistoryJournal.open(File(folder, "$stem.history"))
    }

    fun resolve(directory: File, target: String, group: Boolean, clientId: String): Entry? =
        journal(directory, target, group).read(clientId)?.body?.let { json.decodeFromString<Entry>(it) }

    fun confirm(directory: File, account: String, ack: MessageProtos.AckMessage) {
        require(ack.messageId.isNotBlank() && ack.serverMessageId.isNotBlank() && ack.seqId > 0)
        record(directory, account, ack.conversationId, ack.messageId, ack.serverMessageId, ack.seqId)
    }

    /**
     * 记录一条客户端 ID → 服务端行 的映射。
     * <p>ACK 是首选来源；第二个来源是历史/同步行携带的 clientMessageId：ACK 丢失后，
     * 只要服务端已经落库，客户端仍能把本地乐观行按身份归并。
     */
    fun record(directory: File, account: String, conversation: String, clientId: String,
               serverId: String, seq: Long) {
        val (target, group) = target(account, conversation)
        record(directory, target, group, clientId, serverId, seq, conversation)
    }

    /** Same mapping when the caller already resolved the conversation target. */
    fun record(directory: File, target: String, group: Boolean, clientId: String, serverId: String,
               seq: Long, conversation: String) {
        require(clientId.isNotBlank() && serverId.isNotBlank() && seq > 0) { "Invalid message ID alias" }
        require(clientId != serverId) { "Alias must point at a different row" }
        val entry = Entry(serverId, seq, conversation)
        journal(directory, target, group).write(listOf(IndexedHistoryJournal.Row(clientId, seq, json.encodeToString(entry))))
    }

    fun target(account: String, conversation: String): Pair<String, Boolean> {
        if (conversation.startsWith("group:")) {
            val group = conversation.removePrefix("group:")
            require(group.toLongOrNull()?.let { it > 0 } == true) { "Invalid group confirmation" }
            return group to true
        }
        val members = conversation.removePrefix("user:").split(':')
        require(members.size == 2 && account in members && members.all { it.toLongOrNull() != null }) {
            "Confirmation conversation does not belong to this account"
        }
        return (members.firstOrNull { it != account } ?: account) to false
    }
}
