package core

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import model.GroupMessage
import model.Message

/** Same journal implementation, with an explicit root for server-isolated CLI sessions. */
open class FileLocalChatHistoryStore(
    private val directory: () -> File,
    unknownSender: () -> String = { "Unknown" },
) : LocalChatHistoryStoreProvider {
    constructor(root: File) : this({ root })

    private val historyRoot: File
        get() = directory()

    private val logger = io.github.oshai.kotlinlogging.KotlinLogging.logger {}
    private val objectMapper = jacksonObjectMapper()
    private val paths = HistoryAccountDirectory(directory)
    private val codec = HistoryMessageCodec(objectMapper, paths::account, unknownSender)

    override fun save(
        accountId: String,
        privateMessages: List<Message>,
        groupMessages: List<GroupMessage>,
    ) {
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
                saveMessages(
                    accountId,
                    groupId.toString(),
                    true,
                    messages.map { groupMsg ->
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
                            clientMessageId = groupMsg.clientMessageId,
                        )
                    },
                )
            }
    }

    override fun confirmMessageId(
        accountId: String,
        ack: com.chatlite.proto.MessageProtos.AckMessage,
    ) {
        core.MessageIdAliases.confirm(paths.account(accountId), accountId, ack)
        val (target, group) = core.MessageIdAliases.target(accountId, ack.conversationId)
        val history = journal(accountId, target, group)
        history.read(ack.messageId)?.let { row ->
            val node = objectMapper.readTree(row.body) as ObjectNode
            node.put("messageId", ack.serverMessageId)
            node.put("seqId", ack.seqId)
            node.put("conversationId", ack.conversationId)
            node.put("isSent", true)
            node.put("deliveryState", "ACCEPTED")
            history.write(
                listOf(
                        core.IndexedHistoryJournal.Row(
                            ack.serverMessageId,
                            row.timestamp,
                            objectMapper.writeValueAsString(node),
                        ),
                        core.IndexedHistoryJournal.Row(ack.messageId, 0, null),
                    )
                    .filterIndexed { index, _ ->
                        index == 0 || ack.messageId != ack.serverMessageId
                    }
            )
        }
    }

    override fun deleteMessage(
        accountId: String,
        targetId: String,
        messageId: String,
        isGroup: Boolean,
    ) {
        journal(accountId, targetId, isGroup)
            .write(listOf(IndexedHistoryJournal.Row(messageId, 0, null)))
    }

    override fun restore(accountId: String): RestoredChatHistory =
        restorePage(accountId, page = 0, pageSize = 100)

    override fun restorePage(accountId: String, page: Int, pageSize: Int): RestoredChatHistory {
        if (accountId.isBlank()) return RestoredChatHistory()
        val privateRows =
            codec.distinctById(
                codec
                    .decodeMessages(
                        accountId,
                        false,
                        core.IndexedHistoryJournal.page(
                                paths.account(accountId),
                                false,
                                page,
                                pageSize,
                            )
                            .asReversed(),
                    )
                    .map { it as Message }
            )
        val groupRows =
            codec.distinctById(
                codec
                    .decodeMessages(
                        accountId,
                        true,
                        core.IndexedHistoryJournal.page(
                                paths.account(accountId),
                                true,
                                page,
                                pageSize,
                            )
                            .asReversed(),
                    )
                    .map { it as GroupMessage }
            )
        return RestoredChatHistory(privateRows, groupRows)
    }

    override fun getPrivateMessagesByTimeRange(
        accountId: String,
        userId: Int,
        startTime: Long,
        endTime: Long,
    ): List<Message> {
        if (accountId.isBlank()) {
            return emptyList()
        }

        return loadMessages(accountId, userId.toString(), false, startTime, endTime)
            .filterIsInstance<Message>()
            .filter { it.timestamp in startTime..endTime }
            .let { codec.distinctById(it) }
            .sortedBy { it.timestamp }
    }

    override fun getGroupMessagesByTimeRange(
        accountId: String,
        groupId: Int,
        startTime: Long,
        endTime: Long,
    ): List<GroupMessage> {
        if (accountId.isBlank()) {
            return emptyList()
        }

        return loadMessages(accountId, groupId.toString(), true, startTime, endTime)
            .filterIsInstance<GroupMessage>()
            .filter { it.timestamp in startTime..endTime }
            .let { codec.distinctById(it) }
            .sortedBy { it.timestamp }
    }

    override fun getPrivateMessagesPage(
        accountId: String,
        userId: Int,
        page: Int,
        pageSize: Int,
    ): List<Message> {
        if (accountId.isBlank()) return emptyList()
        require(page >= 0 && pageSize in 1..10000)
        return codec.distinctById(
            codec
                .decodeMessages(
                    accountId,
                    false,
                    journal(accountId, userId.toString(), false)
                        .page(Math.multiplyExact(page, pageSize), pageSize)
                        .asReversed(),
                )
                .map { it as Message }
        )
    }

    override fun getGroupMessagesPage(
        accountId: String,
        groupId: Int,
        page: Int,
        pageSize: Int,
    ): List<GroupMessage> {
        if (accountId.isBlank()) return emptyList()
        require(page >= 0 && pageSize in 1..10000)
        return codec.distinctById(
            codec
                .decodeMessages(
                    accountId,
                    true,
                    journal(accountId, groupId.toString(), true)
                        .page(Math.multiplyExact(page, pageSize), pageSize)
                        .asReversed(),
                )
                .map { it as GroupMessage }
        )
    }

    override fun getPrivateMessagesBefore(
        accountId: String,
        userId: Int,
        before: HistoryCursor?,
        limit: Int,
    ): LocalHistoryPage<Message> {
        if (accountId.isBlank()) return LocalHistoryPage(emptyList(), null, false)
        val page = journal(accountId, userId.toString(), false).pageBefore(before, limit)
        return LocalHistoryPage(
            codec.distinctById(
                codec.decodeMessages(accountId, false, page.rows.asReversed()).map { it as Message }
            ),
            page.before,
            page.hasMore,
        )
    }

    override fun getGroupMessagesBefore(
        accountId: String,
        groupId: Int,
        before: HistoryCursor?,
        limit: Int,
    ): LocalHistoryPage<GroupMessage> {
        if (accountId.isBlank()) return LocalHistoryPage(emptyList(), null, false)
        val page = journal(accountId, groupId.toString(), true).pageBefore(before, limit)
        return LocalHistoryPage(
            codec.distinctById(
                codec.decodeMessages(accountId, true, page.rows.asReversed()).map {
                    it as GroupMessage
                }
            ),
            page.before,
            page.hasMore,
        )
    }

    override fun clear(accountId: String): Boolean {
        if (accountId.isBlank()) {
            return false
        }

        return runCatching {
                val accountDir = paths.account(accountId)
                if (accountDir.exists()) {
                    accountDir.deleteRecursively()
                }
                true
            }
            .getOrDefault(false)
    }

    override fun clearConversation(accountId: String, targetId: String, isGroup: Boolean): Boolean {
        if (accountId.isBlank()) return false
        return runCatching {
                journal(accountId, targetId, isGroup).delete()
                true
            }
            .getOrDefault(false)
    }

    override fun clearAll(): Boolean = runCatching {
        val folder = historyRoot
        if (folder.exists()) {
            folder.deleteRecursively()
        }
        true
    }
        .getOrDefault(false)

    override fun saveConversationSeqIds(accountId: String, ids: Map<String, Long>) {
        runCatching {
            val folder = paths.account(accountId)
            if (!folder.exists()) folder.mkdirs()
            val file = java.io.File(folder, "seq_ids.json")
            AtomicHistoryFile.write(file, objectMapper.writeValueAsString(ids))
        }
            .onFailure {
                logger.warn(it) { "LocalChatHistoryStore operation failed" }
            }
            .getOrThrow()
    }

    override fun restoreConversationSeqIds(accountId: String): Map<String, Long> =
        runCatching<Map<String, Long>> {
                val folder = paths.account(accountId)
                val file = java.io.File(folder, "seq_ids.json")
                if (!file.exists()) return@runCatching emptyMap<String, Long>()
                val text = file.readText()
                if (text.isBlank()) return@runCatching emptyMap<String, Long>()
                objectMapper.readValue(
                    text,
                    objectMapper.typeFactory.constructMapType(
                        Map::class.java,
                        String::class.java,
                        Long::class.javaObjectType,
                    ),
                )
            }
            .getOrDefault(emptyMap())

    private fun saveMessages(
        accountId: String,
        targetId: String,
        isGroup: Boolean,
        messages: List<Message>,
    ) {
        val directory = paths.account(accountId)
        val entries = linkedMapOf<String, MessageIdAliases.Entry>()
        messages.forEach { message ->
            // A server row that still names its client ID proves the mapping even without an ACK.
            // Record it, then tombstone the placeholder it supersedes so one send keeps one row.
            val clientId =
                message.clientMessageId?.takeIf { it.isNotBlank() && it != message.messageId }
            if (clientId != null && message.seqId > 0) {
                val conversation =
                    message.conversationId.ifBlank {
                        if (isGroup) "group:$targetId" else ""
                    }
                entries[clientId] =
                    MessageIdAliases.Entry(message.messageId, message.seqId, conversation)
            }
        }
        MessageIdAliases.recordAll(directory, targetId, isGroup, entries)
        val aliases =
            MessageIdAliases.resolveAll(directory, targetId, isGroup, messages.map { it.messageId })
        val rows = messages.map { message ->
            val alias = aliases[message.messageId]
            val canonical =
                if (alias == null) message
                else
                    message.copy(
                        messageId = alias.serverId,
                        seqId = alias.seq,
                        conversationId = alias.conversation,
                        isSent = true,
                        deliveryState = model.MessageDeliveryState.ACCEPTED,
                    )
            core.IndexedHistoryJournal.Row(
                canonical.messageId,
                canonical.timestamp,
                objectMapper.writeValueAsString(codec.messageToJson(canonical)),
            )
        }
        val superseded = entries.keys.map { IndexedHistoryJournal.Row(it, 0, null) }
        journal(accountId, targetId, isGroup).write(rows + superseded)
    }

    private fun loadMessages(
        accountId: String,
        targetId: String,
        isGroup: Boolean,
        from: Long = Long.MIN_VALUE,
        through: Long = Long.MAX_VALUE,
    ): List<Any> =
        codec.decodeMessages(
            accountId,
            isGroup,
            journal(accountId, targetId, isGroup).range(from, through),
        )

    private fun journal(
        accountId: String,
        targetId: String,
        isGroup: Boolean,
    ): core.IndexedHistoryJournal =
        core.IndexedHistoryJournal.open(
            core.IndexedHistoryJournal.conversation(paths.account(accountId), targetId, isGroup)
        )
}
