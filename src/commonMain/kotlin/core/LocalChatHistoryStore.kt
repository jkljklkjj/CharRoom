package core

import androidx.compose.runtime.mutableStateOf
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import model.GroupMessage
import model.Message
import model.MessageType
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
    val messageId: String,
    val replyToMessageId: String? = null,
    val replyToContent: String? = null,
    val replyToSender: String? = null,
    val messageType: MessageType = MessageType.TEXT,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null
)

data class LocalGroupMessageRecord(
    val groupId: Int,
    val senderName: String,
    val text: String,
    val senderId: Int,
    val timestamp: Long,
    val isSent: Boolean,
    val messageId: String,
    val replyToMessageId: String? = null,
    val replyToContent: String? = null,
    val replyToSender: String? = null,
    val messageType: MessageType = MessageType.TEXT,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null
)

data class LocalChatHistorySnapshot(
    val version: Int = CURRENT_VERSION, // 数据结构版本号
    val privateMessages: List<LocalPrivateMessageRecord> = emptyList(),
    val groupMessages: List<LocalGroupMessageRecord> = emptyList(),
    val savedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val CURRENT_VERSION = 2 // 当前最新版本
    }
}

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
        return restorePage(accountId, page = 0, pageSize = 100) // 默认只加载最近100条消息
    }

    /**
     * 分页加载历史消息
     * @param page 页码，从0开始，0表示最新的一页
     * @param pageSize 每页消息数量
     */
    fun restorePage(accountId: String, page: Int = 0, pageSize: Int = 50): RestoredChatHistory {
        if (accountId.isBlank()) {
            return RestoredChatHistory()
        }

        val historyFile = accountHistoryFile(accountId)
        if (!historyFile.exists() || historyFile.length() <= 0L) {
            return RestoredChatHistory()
        }

        return runCatching {
            val snapshot: LocalChatHistorySnapshot = mapper.readValue(historyFile)

            // 版本迁移
            val migratedSnapshot = migrateSnapshot(snapshot)

            // 分页处理：按时间倒序，取对应页的消息
            val allPrivate = migratedSnapshot.privateMessages
                .normalizedBy({ it.privateKey() }, MAX_PRIVATE_HISTORY)
                .sortedByDescending { it.timestamp }

            val allGroup = migratedSnapshot.groupMessages
                .normalizedBy({ it.groupKey() }, MAX_GROUP_HISTORY)
                .sortedByDescending { it.timestamp }

            val startIndex = page * pageSize
            val endIndex = minOf(startIndex + pageSize, allPrivate.size)

            val privateMessages = if (startIndex < allPrivate.size) {
                allPrivate.subList(startIndex, endIndex).map { it.toModel() }.reversed() // 恢复正序
            } else {
                emptyList()
            }

            val groupStartIndex = page * pageSize
            val groupEndIndex = minOf(groupStartIndex + pageSize, allGroup.size)
            val groupMessages = if (groupStartIndex < allGroup.size) {
                allGroup.subList(groupStartIndex, groupEndIndex).map { it.toModel() }.reversed() // 恢复正序
            } else {
                emptyList()
            }

            RestoredChatHistory(
                privateMessages = privateMessages,
                groupMessages = groupMessages
            )
        }.getOrElse {
            println("Local history restore failed: ${it.message}")
            RestoredChatHistory()
        }
    }

    /**
     * 版本迁移逻辑
     */
    private fun migrateSnapshot(snapshot: LocalChatHistorySnapshot): LocalChatHistorySnapshot {
        if (snapshot.version == LocalChatHistorySnapshot.CURRENT_VERSION) {
            return snapshot
        }

        println("Migrating chat history from version ${snapshot.version} to ${LocalChatHistorySnapshot.CURRENT_VERSION}")

        var migrated = snapshot

        // 版本1 -> 版本2：添加引用消息和文件相关字段
        if (migrated.version < 2) {
            migrated = migrated.copy(
                privateMessages = migrated.privateMessages.map { record ->
                    // 旧版本的记录没有新字段，设置默认值
                    record.copy(
                        replyToMessageId = null,
                        replyToContent = null,
                        replyToSender = null,
                        messageType = MessageType.TEXT,
                        fileUrl = null,
                        fileName = null,
                        fileSize = null
                    )
                },
                groupMessages = migrated.groupMessages.map { record ->
                    // 旧版本的记录没有新字段，设置默认值
                    record.copy(
                        replyToMessageId = null,
                        replyToContent = null,
                        replyToSender = null,
                        messageType = MessageType.TEXT,
                        fileUrl = null,
                        fileName = null,
                        fileSize = null
                    )
                },
                version = 2
            )
        }

        return migrated
    }

    /**
     * 按时间范围查询私聊消息
     */
    fun getPrivateMessagesByTimeRange(accountId: String, userId: Int, startTime: Long, endTime: Long): List<Message> {
        if (accountId.isBlank()) {
            return emptyList()
        }

        val historyFile = accountHistoryFile(accountId)
        if (!historyFile.exists()) {
            return emptyList()
        }

        return runCatching {
            val snapshot: LocalChatHistorySnapshot = mapper.readValue(historyFile)
            val migratedSnapshot = migrateSnapshot(snapshot)

            migratedSnapshot.privateMessages
                .filter { it.senderId == userId && it.timestamp in startTime..endTime }
                .sortedBy { it.timestamp }
                .map { it.toModel() }
        }.getOrElse {
            println("Get private messages by time range failed: ${it.message}")
            emptyList()
        }
    }

    /**
     * 按时间范围查询群聊消息
     */
    fun getGroupMessagesByTimeRange(accountId: String, groupId: Int, startTime: Long, endTime: Long): List<GroupMessage> {
        if (accountId.isBlank()) {
            return emptyList()
        }

        val historyFile = accountHistoryFile(accountId)
        if (!historyFile.exists()) {
            return emptyList()
        }

        return runCatching {
            val snapshot: LocalChatHistorySnapshot = mapper.readValue(historyFile)
            val migratedSnapshot = migrateSnapshot(snapshot)

            migratedSnapshot.groupMessages
                .filter { it.groupId == groupId && it.timestamp in startTime..endTime }
                .sortedBy { it.timestamp }
                .map { it.toModel() }
        }.getOrElse {
            println("Get group messages by time range failed: ${it.message}")
            emptyList()
        }
    }

    /**
     * 分页查询与指定用户的私聊消息
     * @param page 页码，从0开始，0表示最新的一页
     */
    fun getPrivateMessagesPage(accountId: String, userId: Int, page: Int = 0, pageSize: Int = 50): List<Message> {
        if (accountId.isBlank()) {
            return emptyList()
        }

        val historyFile = accountHistoryFile(accountId)
        if (!historyFile.exists()) {
            return emptyList()
        }

        return runCatching {
            val snapshot: LocalChatHistorySnapshot = mapper.readValue(historyFile)
            val migratedSnapshot = migrateSnapshot(snapshot)

            val allMessages = migratedSnapshot.privateMessages
                .filter { it.senderId == userId }
                .sortedByDescending { it.timestamp }

            val startIndex = page * pageSize
            val endIndex = minOf(startIndex + pageSize, allMessages.size)

            if (startIndex < allMessages.size) {
                allMessages.subList(startIndex, endIndex).map { it.toModel() }.reversed()
            } else {
                emptyList()
            }
        }.getOrElse {
            println("Get private messages page failed: ${it.message}")
            emptyList()
        }
    }

    /**
     * 分页查询指定群组的消息
     * @param page 页码，从0开始，0表示最新的一页
     */
    fun getGroupMessagesPage(accountId: String, groupId: Int, page: Int = 0, pageSize: Int = 50): List<GroupMessage> {
        if (accountId.isBlank()) {
            return emptyList()
        }

        val historyFile = accountHistoryFile(accountId)
        if (!historyFile.exists()) {
            return emptyList()
        }

        return runCatching {
            val snapshot: LocalChatHistorySnapshot = mapper.readValue(historyFile)
            val migratedSnapshot = migrateSnapshot(snapshot)

            val allMessages = migratedSnapshot.groupMessages
                .filter { it.groupId == groupId }
                .sortedByDescending { it.timestamp }

            val startIndex = page * pageSize
            val endIndex = minOf(startIndex + pageSize, allMessages.size)

            if (startIndex < allMessages.size) {
                allMessages.subList(startIndex, endIndex).map { it.toModel() }.reversed()
            } else {
                emptyList()
            }
        }.getOrElse {
            println("Get group messages page failed: ${it.message}")
            emptyList()
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
            messageId = messageId,
            replyToMessageId = replyToMessageId,
            replyToContent = replyToContent,
            replyToSender = replyToSender,
            messageType = messageType,
            fileUrl = fileUrl,
            fileName = fileName,
            fileSize = fileSize
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
            messageId = messageId,
            replyToMessageId = replyToMessageId,
            replyToContent = replyToContent,
            replyToSender = replyToSender,
            messageType = messageType,
            fileUrl = fileUrl,
            fileName = fileName,
            fileSize = fileSize
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
            messageId = messageId,
            replyToMessageId = replyToMessageId,
            replyToContent = replyToContent,
            replyToSender = replyToSender,
            messageType = messageType,
            fileUrl = fileUrl,
            fileName = fileName,
            fileSize = fileSize
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
            messageId = messageId,
            replyToMessageId = replyToMessageId,
            replyToContent = replyToContent,
            replyToSender = replyToSender,
            messageType = messageType,
            fileUrl = fileUrl,
            fileName = fileName,
            fileSize = fileSize
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
