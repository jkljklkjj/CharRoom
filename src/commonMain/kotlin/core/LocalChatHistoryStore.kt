package core

import androidx.compose.runtime.mutableStateOf
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import model.GroupMessage
import model.Message
import java.io.File

private const val MAX_PRIVATE_HISTORY = 5000
private const val MAX_GROUP_HISTORY = 5000

data class LocalPrivateMessageRecord(
    val senderId: Int,
    val message: String,
    val sender: Boolean,
    val receiverId: Int,
    val timestamp: Long,
    val isSent: Boolean,
    val messageId: String
)

data class LocalGroupMessageRecord(
    val groupId: Int,
    val senderName: String,
    val text: String,
    val senderId: Int,
    val timestamp: Long,
    val isSent: Boolean,
    val messageId: String
)

data class LocalChatHistorySnapshot(
    val privateMessages: List<LocalPrivateMessageRecord> = emptyList(),
    val groupMessages: List<LocalGroupMessageRecord> = emptyList(),
    val savedAt: Long = System.currentTimeMillis()
)

data class RestoredChatHistory(
    val privateMessages: List<Message> = emptyList(),
    val groupMessages: List<GroupMessage> = emptyList()
)

object LocalChatHistoryStore {
    private val mapper = jacksonObjectMapper()

    fun capture(privateMessages: List<Message>, groupMessages: List<GroupMessage>): LocalChatHistorySnapshot {
        val normalizedPrivate = privateMessages
            .map { it.toLocalRecord() }
            .normalizedBy({ it.privateKey() }, MAX_PRIVATE_HISTORY)

        val normalizedGroup = groupMessages
            .map { it.toLocalRecord() }
            .normalizedBy({ it.groupKey() }, MAX_GROUP_HISTORY)

        return LocalChatHistorySnapshot(
            privateMessages = normalizedPrivate,
            groupMessages = normalizedGroup,
            savedAt = System.currentTimeMillis()
        )
    }

    fun save(accountId: String, snapshot: LocalChatHistorySnapshot) {
        if (accountId.isBlank()) {
            return
        }

        val historyFile = accountHistoryFile(accountId)
        runCatching {
            mapper.writerWithDefaultPrettyPrinter().writeValue(historyFile, snapshot)
        }.onFailure {
            println("Local history save failed: ${it.message}")
        }
    }

    fun restore(accountId: String): RestoredChatHistory {
        if (accountId.isBlank()) {
            return RestoredChatHistory()
        }

        val historyFile = accountHistoryFile(accountId)
        if (!historyFile.exists() || historyFile.length() <= 0L) {
            return RestoredChatHistory()
        }

        return runCatching {
            val snapshot: LocalChatHistorySnapshot = mapper.readValue(historyFile)
            val privateMessages = snapshot.privateMessages
                .normalizedBy({ it.privateKey() }, MAX_PRIVATE_HISTORY)
                .map { it.toModel() }
            val groupMessages = snapshot.groupMessages
                .normalizedBy({ it.groupKey() }, MAX_GROUP_HISTORY)
                .map { it.toModel() }

            RestoredChatHistory(
                privateMessages = privateMessages,
                groupMessages = groupMessages
            )
        }.getOrElse {
            println("Local history restore failed: ${it.message}")
            RestoredChatHistory()
        }
    }

    fun clear(accountId: String): Boolean {
        if (accountId.isBlank()) {
            return false
        }

        val historyFile = accountHistoryFile(accountId)
        if (!historyFile.exists()) {
            return true
        }

        return runCatching {
            if (historyFile.delete()) {
                true
            } else {
                mapper.writerWithDefaultPrettyPrinter().writeValue(
                    historyFile,
                    LocalChatHistorySnapshot(privateMessages = emptyList(), groupMessages = emptyList())
                )
                true
            }
        }.getOrElse {
            println("Local history clear failed: ${it.message}")
            false
        }
    }

    private fun accountHistoryFile(accountId: String): File {
        val folder = historyFolder()
        if (!folder.exists()) {
            folder.mkdirs()
        }
        return File(folder, "history-${safeAccountId(accountId)}.json")
    }

    private fun historyFolder(): File {
        val home = runCatching { System.getProperty("user.home") }.getOrNull().orEmpty()
        return if (home.isBlank()) {
            File("chat-history")
        } else {
            File(File(home, ".qingliao"), "chat-history")
        }
    }

    private fun safeAccountId(accountId: String): String {
        val cleaned = accountId.trim().replace(Regex("[^A-Za-z0-9_-]"), "_")
        return if (cleaned.isBlank()) "default" else cleaned
    }

    private fun Message.toLocalRecord(): LocalPrivateMessageRecord {
        return LocalPrivateMessageRecord(
            senderId = senderId,
            message = message,
            sender = sender,
            receiverId = receiverId,
            timestamp = timestamp,
            isSent = isSent.value,
            messageId = messageId
        )
    }

    private fun GroupMessage.toLocalRecord(): LocalGroupMessageRecord {
        return LocalGroupMessageRecord(
            groupId = groupId,
            senderName = senderName,
            text = text,
            senderId = senderId,
            timestamp = timestamp,
            isSent = isSent.value,
            messageId = messageId
        )
    }

    private fun LocalPrivateMessageRecord.toModel(): Message {
        return Message(
            senderId = senderId,
            message = message,
            sender = sender,
            receiverId = receiverId,
            timestamp = timestamp,
            isSent = mutableStateOf(isSent),
            messageId = messageId
        )
    }

    private fun LocalGroupMessageRecord.toModel(): GroupMessage {
        return GroupMessage(
            groupId = groupId,
            senderName = senderName,
            text = text,
            senderId = senderId,
            timestamp = timestamp,
            isSent = mutableStateOf(isSent),
            messageId = messageId
        )
    }

    private fun LocalPrivateMessageRecord.privateKey(): String {
        if (messageId.isNotBlank()) {
            return messageId
        }
        return "${senderId}_${receiverId}_${sender}_${timestamp}_${message.hashCode()}"
    }

    private fun LocalGroupMessageRecord.groupKey(): String {
        if (messageId.isNotBlank()) {
            return messageId
        }
        return "${groupId}_${senderId}_${timestamp}_${text.hashCode()}"
    }

    private fun <T> List<T>.normalizedBy(key: (T) -> String, limit: Int): List<T> {
        if (isEmpty()) {
            return emptyList()
        }

        val grouped = this.groupBy(key)
        val flattened = grouped.values.mapNotNull { items ->
            items.maxByOrNull { item ->
                when (item) {
                    is LocalPrivateMessageRecord -> item.timestamp
                    is LocalGroupMessageRecord -> item.timestamp
                    else -> 0L
                }
            }
        }

        val ordered = flattened.sortedBy {
            when (it) {
                is LocalPrivateMessageRecord -> it.timestamp
                is LocalGroupMessageRecord -> it.timestamp
                else -> 0L
            }
        }

        return if (ordered.size > limit) ordered.takeLast(limit) else ordered
    }
}
