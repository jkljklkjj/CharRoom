package core

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import model.ChatMessage
import model.GroupMessage
import model.Message
import model.MessageType
import java.io.File

/** Same journal implementation, with an explicit root for server-isolated CLI sessions. */
open class FileLocalChatHistoryStore(private val directory: () -> File, private val unknownSender: () -> String = { "Unknown" }) : LocalChatHistoryStoreProvider {
    constructor(root: File) : this({ root })
    private val historyRoot: File get() = directory()
    private val logger = io.github.oshai.kotlinlogging.KotlinLogging.logger {}
    private val objectMapper = jacksonObjectMapper()

    override fun save(accountId: String, privateMessages: List<Message>, groupMessages: List<GroupMessage>) {
        // 保存私聊消息
        privateMessages
            .groupBy { if (it.sender) it.receiverId else it.senderId }
            .forEach { (receiverId, messages) ->
                saveMessages(accountId, receiverId.toString(), false, messages)
            }

        // 保存群聊消息
        groupMessages
            .groupBy { it.groupId }
            .forEach { (groupId, messages) ->
                saveMessages(accountId, groupId.toString(), true, messages.map { groupMsg ->
                    Message(
                        senderId = groupMsg.senderId,
                        message = groupMsg.text,
                        sender = false,
                        receiverId = groupId,
                        timestamp = groupMsg.timestamp,
                        isSent = groupMsg.isSent,
                        messageId = groupMsg.messageId,
                        replyToMessageId = groupMsg.replyToMessageId,
                        replyToContent = groupMsg.replyToContent,
                        replyToSender = groupMsg.replyToSender,
                        messageType = groupMsg.messageType,
                        fileUrl = groupMsg.fileUrl,
                        fileName = groupMsg.fileName,
                        fileSize = groupMsg.fileSize,
                        seqId = groupMsg.seqId,
                        conversationId = groupMsg.conversationId,
                        deliveryState = groupMsg.deliveryState,
                        clientMessageId = groupMsg.clientMessageId
                    )
                })
            }
    }

    override fun confirmMessageId(accountId: String, ack: com.chatlite.proto.MessageProtos.AckMessage) {
        core.MessageIdAliases.confirm(getAccountDir(accountId), accountId, ack)
        val (target, group) = core.MessageIdAliases.target(accountId, ack.conversationId)
        val history = journal(accountId, target, group)
        history.read(ack.messageId)?.let { row ->
            val node = objectMapper.readTree(row.body) as ObjectNode
            node.put("messageId", ack.serverMessageId); node.put("seqId", ack.seqId); node.put("conversationId", ack.conversationId); node.put("isSent", true); node.put("deliveryState", "ACCEPTED")
            history.write(listOf(core.IndexedHistoryJournal.Row(ack.serverMessageId, row.timestamp, objectMapper.writeValueAsString(node)),
                core.IndexedHistoryJournal.Row(ack.messageId, 0, null)).filterIndexed { index, _ -> index == 0 || ack.messageId != ack.serverMessageId })
        }
    }

    override fun deleteMessage(accountId: String, messageId: String, isGroup: Boolean) {
        java.io.File(getAccountDir(accountId), "indexed").listFiles { file ->
            file.name.startsWith(if (isGroup) "group_" else "private_") && file.extension == "history"
        }?.forEach { file -> core.IndexedHistoryJournal.open(file).write(listOf(core.IndexedHistoryJournal.Row(messageId, 0, null))) }
    }

    override fun restore(accountId: String): RestoredChatHistory {
        return restorePage(accountId, page = 0, pageSize = 100)
    }

    override fun restorePage(accountId: String, page: Int, pageSize: Int): RestoredChatHistory {
        if (accountId.isBlank()) return RestoredChatHistory()
        val privateRows = distinctById(core.IndexedHistoryJournal.page(getAccountDir(accountId), false, page, pageSize)
            .asReversed().map { row -> decodeMessage(accountId, false, row.body!!) as Message })
        val groupRows = distinctById(core.IndexedHistoryJournal.page(getAccountDir(accountId), true, page, pageSize)
            .asReversed().map { row -> decodeMessage(accountId, true, row.body!!) as GroupMessage })
        return RestoredChatHistory(privateRows, groupRows)
    }

    override fun getPrivateMessagesByTimeRange(accountId: String, userId: Int, startTime: Long, endTime: Long): List<Message> {
        if (accountId.isBlank()) {
            return emptyList()
        }

        return loadMessages(accountId, userId.toString(), false, startTime, endTime)
            .filterIsInstance<Message>()
            .filter { it.timestamp in startTime..endTime }
            .let { distinctById(it) }
            .sortedBy { it.timestamp }
    }

    override fun getGroupMessagesByTimeRange(accountId: String, groupId: Int, startTime: Long, endTime: Long): List<GroupMessage> {
        if (accountId.isBlank()) {
            return emptyList()
        }

        return loadMessages(accountId, groupId.toString(), true, startTime, endTime)
            .filterIsInstance<GroupMessage>()
            .filter { it.timestamp in startTime..endTime }
            .let { distinctById(it) }
            .sortedBy { it.timestamp }
    }

    override fun getPrivateMessagesPage(accountId: String, userId: Int, page: Int, pageSize: Int): List<Message> {
        if (accountId.isBlank()) return emptyList()
        require(page >= 0 && pageSize in 1..10000)
        return distinctById(journal(accountId, userId.toString(), false).page(Math.multiplyExact(page, pageSize), pageSize)
            .asReversed().map { row -> decodeMessage(accountId, false, row.body!!) as Message })
    }

    override fun getGroupMessagesPage(accountId: String, groupId: Int, page: Int, pageSize: Int): List<GroupMessage> {
        if (accountId.isBlank()) return emptyList()
        require(page >= 0 && pageSize in 1..10000)
        return distinctById(journal(accountId, groupId.toString(), true).page(Math.multiplyExact(page, pageSize), pageSize)
            .asReversed().map { row -> decodeMessage(accountId, true, row.body!!) as GroupMessage })
    }

    override fun clear(accountId: String): Boolean {
        if (accountId.isBlank()) {
            return false
        }

        return runCatching {
            val accountDir = getAccountDir(accountId)
            if (accountDir.exists()) {
                accountDir.deleteRecursively()
            }
            true
        }.getOrDefault(false)
    }

    override fun clearConversation(accountId: String, targetId: String, isGroup: Boolean): Boolean {
        if (accountId.isBlank()) return false
        return runCatching {
            journal(accountId, targetId, isGroup).delete()
            true
        }.getOrDefault(false)
    }

    override fun clearAll(): Boolean {
        return runCatching {
            val folder = historyRoot
            if (folder.exists()) {
                folder.deleteRecursively()
            }
            true
        }.getOrDefault(false)
    }

    override fun saveConversationSeqIds(accountId: String, ids: Map<String, Long>) {
        runCatching {
            val userHome = System.getProperty("user.home")
            val folder = getAccountDir(accountId)
            if (!folder.exists()) folder.mkdirs()
            val file = java.io.File(folder, "seq_ids.json")
            AtomicHistoryFile.write(file, objectMapper.writeValueAsString(ids))
        }.onFailure {
            logger.warn(it) { "LocalChatHistoryStore operation failed" }
        }.getOrThrow()
    }

    override fun restoreConversationSeqIds(accountId: String): Map<String, Long> {
        return runCatching<Map<String, Long>> {
            val userHome = System.getProperty("user.home")
            val folder = getAccountDir(accountId)
            val file = java.io.File(folder, "seq_ids.json")
            if (!file.exists()) return@runCatching emptyMap<String, Long>()
            val text = file.readText()
            if (text.isBlank()) return@runCatching emptyMap<String, Long>()
            objectMapper.readValue(text, objectMapper.typeFactory.constructMapType(
                Map::class.java, String::class.java, Long::class.javaObjectType
            ))
        }.getOrDefault(emptyMap())
    }

    private fun decodeMessage(accountId: String, isGroup: Boolean, body: String): Any {
        val decoded = jsonToMessage(objectMapper.readTree(body) as ObjectNode, isGroup)
        // The ACK records the alias before it renames the row, so a crash in between leaves a
        // row stored under its client ID. Resolve it on read: identity, not content, decides.
        val (targetId, group) = when (decoded) {
            is Message -> (if (decoded.senderId.toString() == accountId) decoded.receiverId
                else decoded.senderId).toString() to false
            is GroupMessage -> decoded.groupId.toString() to true
            else -> return decoded
        }
        val clientId = (decoded as ChatMessage).chatMessageId
        val alias = core.MessageIdAliases.resolve(getAccountDir(accountId), targetId, group, clientId) ?: return decoded
        val conversation = if (alias.conversation.isBlank()) (if (group) "group:$targetId" else "") else alias.conversation
        return when (decoded) {
            is Message -> decoded.copy(messageId = alias.serverId, seqId = alias.seq, conversationId = conversation,
                isSent = true, deliveryState = model.MessageDeliveryState.ACCEPTED)
            is GroupMessage -> decoded.copy(messageId = alias.serverId, seqId = alias.seq, conversationId = conversation,
                isSent = true, deliveryState = model.MessageDeliveryState.ACCEPTED)
            else -> decoded
        }
    }

    /** Canonical rows and a stale client-ID copy describe one send, so keep one bubble. */
    private fun <T : ChatMessage> distinctById(messages: List<T>): List<T> {
        val seen = HashSet<String>(messages.size)
        return messages.filter { seen.add(it.chatMessageId) }
    }

    private fun saveMessages(accountId: String, targetId: String, isGroup: Boolean, messages: List<Message>) {
        val directory = getAccountDir(accountId)
        val superseded = mutableListOf<core.IndexedHistoryJournal.Row>()
        val rows = messages.map { message ->
            // A server row that still names its client ID proves the mapping even without an ACK.
            // Record it, then tombstone the placeholder it supersedes so one send keeps one row.
            val clientId = message.clientMessageId
            if (!clientId.isNullOrBlank() && clientId != message.messageId && message.seqId > 0) {
                val conversation = message.conversationId.ifBlank {
                    if (isGroup) "group:$targetId" else ""
                }
                core.MessageIdAliases.record(directory, targetId, isGroup, clientId, message.messageId, message.seqId, conversation)
                superseded += core.IndexedHistoryJournal.Row(clientId, 0, null)
            }
            val alias = core.MessageIdAliases.resolve(directory, targetId, isGroup, message.messageId)
            val canonical = if (alias == null) message else message.copy(messageId = alias.serverId,
                seqId = alias.seq, conversationId = alias.conversation, isSent = true, deliveryState = model.MessageDeliveryState.ACCEPTED)
            core.IndexedHistoryJournal.Row(canonical.messageId, canonical.timestamp, objectMapper.writeValueAsString(messageToJson(canonical)))
        }
        journal(accountId, targetId, isGroup).write(rows + superseded)
    }

    private fun loadMessages(accountId: String, targetId: String, isGroup: Boolean,
                             from: Long = Long.MIN_VALUE, through: Long = Long.MAX_VALUE): List<Any> =
        journal(accountId, targetId, isGroup).range(from, through).map { row -> decodeMessage(accountId, isGroup, row.body!!) }

    private fun journal(accountId: String, targetId: String, isGroup: Boolean): core.IndexedHistoryJournal =
        core.IndexedHistoryJournal.open(core.IndexedHistoryJournal.conversation(getAccountDir(accountId), targetId, isGroup))

    private fun getAccountDir(accountId: String): File {
        val folder = historyRoot
        if (!folder.exists()) {
            folder.mkdirs()
        }
        val safeAccountId = accountId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(folder, safeAccountId)
    }

    private fun messageToJson(message: Message): ObjectNode {
        return objectMapper.createObjectNode().apply {
            put("senderId", message.senderId)
            put("receiverId", message.receiverId)
            put("content", message.message)
            put("sender", message.sender)
            put("timestamp", message.timestamp)
            put("isSent", message.isSent)
            put("deliveryState", message.deliveryState.name)
            put("messageId", message.messageId)
            put("replyToMessageId", message.replyToMessageId)
            put("replyToContent", message.replyToContent)
            put("replyToSender", message.replyToSender)
            put("messageType", message.messageType.name)
            put("fileUrl", message.fileUrl)
            put("fileName", message.fileName)
            put("fileSize", message.fileSize)
            put("seqId", message.seqId)
            put("conversationId", message.conversationId)
            message.clientMessageId?.let { put("clientMessageId", it) }
        }
    }

    private fun jsonToMessage(json: ObjectNode, isGroup: Boolean): Any {
        val messageType = MessageType.valueOf(json.required("messageType").asText())

        return if (isGroup) {
            GroupMessage(
                groupId = json.path("receiverId").asInt(0).let { if (it < 0) -it else it },
                senderName = json.path("senderName").asText(unknownSender()),
                text = json.path("content").asText(""),
                senderId = json.path("senderId").asInt(0),
                timestamp = json.path("timestamp").asLong(System.currentTimeMillis()),
                isSent = json.path("isSent").asBoolean(true),
                deliveryState = model.MessageDeliveryState.valueOf(json.required("deliveryState").asText()),
                messageId = json.path("messageId").asText(""),
                clientMessageId = json.path("clientMessageId").takeIf { it.isTextual }?.asText(),
                replyToMessageId = json.path("replyToMessageId").asText(null),
                replyToContent = json.path("replyToContent").asText(null),
                replyToSender = json.path("replyToSender").asText(null),
                messageType = messageType,
                fileUrl = json.path("fileUrl").asText(null),
                fileName = json.path("fileName").asText(null),
                fileSize = if (json.hasNonNull("fileSize")) json.path("fileSize").asLong() else null,
                seqId = json.path("seqId").asLong(0),
                conversationId = json.path("conversationId").asText("")
            )
        } else {
            Message(
                senderId = json.path("senderId").asInt(0),
                receiverId = json.path("receiverId").asInt(0),
                message = json.path("content").asText(""),
                sender = json.path("sender").asBoolean(false),
                timestamp = json.path("timestamp").asLong(System.currentTimeMillis()),
                isSent = json.path("isSent").asBoolean(true),
                deliveryState = model.MessageDeliveryState.valueOf(json.required("deliveryState").asText()),
                messageId = json.path("messageId").asText(""),
                clientMessageId = json.path("clientMessageId").takeIf { it.isTextual }?.asText(),
                replyToMessageId = json.path("replyToMessageId").asText(null),
                replyToContent = json.path("replyToContent").asText(null),
                replyToSender = json.path("replyToSender").asText(null),
                messageType = messageType,
                fileUrl = json.path("fileUrl").asText(null),
                fileName = json.path("fileName").asText(null),
                fileSize = if (json.hasNonNull("fileSize")) json.path("fileSize").asLong() else null,
                seqId = json.path("seqId").asLong(0),
                conversationId = json.path("conversationId").asText("")
            )
        }
    }
}
