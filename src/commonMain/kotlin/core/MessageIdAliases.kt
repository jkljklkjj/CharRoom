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
        val (target, group) = target(account, ack.conversationId)
        val entry = Entry(ack.serverMessageId, ack.seqId, ack.conversationId)
        journal(directory, target, group).write(listOf(IndexedHistoryJournal.Row(ack.messageId, ack.seqId, json.encodeToString(entry))))
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
