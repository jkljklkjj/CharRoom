package com.chatlite.charroom.data.datasource.local

import android.app.Application
import android.content.Context
import core.LocalChatHistoryStoreProvider
import core.RestoredChatHistory
import java.io.File
import model.GroupMessage
import model.Message
import model.MessageType
import org.json.JSONObject

/** Android端本地聊天历史存储实现 */
object AndroidLocalChatHistoryStore : LocalChatHistoryStoreProvider {
    private const val HISTORY_DIR_NAME = "chat_history"
    private const val MAX_PRIVATE_HISTORY = 5000
    private const val MAX_GROUP_HISTORY = 5000

    private lateinit var application: Application

    /** 初始化，需要在Application中调用 */
    fun init(context: Context) {
        application = context.applicationContext as Application
    }

    override fun save(
        accountId: String,
        privateMessages: List<Message>,
        groupMessages: List<GroupMessage>,
    ) {
        if (accountId.isBlank() || !this::application.isInitialized) {
            return
        }

        // 保存私聊消息
        privateMessages
            .groupBy { if (it.sender) it.receiverId else it.senderId }
            .forEach { (peerId, messages) ->
                saveMessages(accountId, peerId.toString(), false, messages)
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
        core.MessageIdAliases.confirm(getAccountDir(accountId), accountId, ack)
        val (target, group) = core.MessageIdAliases.target(accountId, ack.conversationId)
        val history = journal(accountId, target, group)
        history.read(ack.messageId)?.let { row ->
            val node = JSONObject(row.body!!)
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
                            node.toString(),
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
            .write(listOf(core.IndexedHistoryJournal.Row(messageId, 0, null)))
    }

    override fun restore(accountId: String): RestoredChatHistory =
        restorePage(accountId, page = 0, pageSize = 100)

    override fun restorePage(accountId: String, page: Int, pageSize: Int): RestoredChatHistory {
        if (accountId.isBlank() || !this::application.isInitialized) return RestoredChatHistory()
        val privateRows =
            decodeMessages(
                    accountId,
                    false,
                    core.IndexedHistoryJournal.page(getAccountDir(accountId), false, page, pageSize)
                        .asReversed(),
                )
                .map { it as Message }
                .distinctBy { it.messageId }
        val groupRows =
            decodeMessages(
                    accountId,
                    true,
                    core.IndexedHistoryJournal.page(getAccountDir(accountId), true, page, pageSize)
                        .asReversed(),
                )
                .map { it as GroupMessage }
                .distinctBy { it.messageId }
        return RestoredChatHistory(privateRows, groupRows)
    }

    override fun getPrivateMessagesByTimeRange(
        accountId: String,
        userId: Int,
        startTime: Long,
        endTime: Long,
    ): List<Message> {
        if (accountId.isBlank() || !this::application.isInitialized) {
            return emptyList()
        }

        return loadMessages(accountId, userId.toString(), false, startTime, endTime)
            .filterIsInstance<Message>()
            .distinctBy { it.messageId }
            .filter { it.timestamp in startTime..endTime }
            .sortedBy { it.timestamp }
    }

    override fun getGroupMessagesByTimeRange(
        accountId: String,
        groupId: Int,
        startTime: Long,
        endTime: Long,
    ): List<GroupMessage> {
        if (accountId.isBlank() || !this::application.isInitialized) {
            return emptyList()
        }

        return loadMessages(accountId, groupId.toString(), true, startTime, endTime)
            .filterIsInstance<GroupMessage>()
            .distinctBy { it.messageId }
            .filter { it.timestamp in startTime..endTime }
            .sortedBy { it.timestamp }
    }

    override fun getPrivateMessagesPage(
        accountId: String,
        userId: Int,
        page: Int,
        pageSize: Int,
    ): List<Message> {
        if (accountId.isBlank() || !this::application.isInitialized) return emptyList()
        require(page >= 0 && pageSize in 1..10000)
        return decodeMessages(
                accountId,
                false,
                journal(accountId, userId.toString(), false)
                    .page(Math.multiplyExact(page, pageSize), pageSize)
                    .asReversed(),
            )
            .map { it as Message }
            .distinctBy { it.messageId }
    }

    override fun getGroupMessagesPage(
        accountId: String,
        groupId: Int,
        page: Int,
        pageSize: Int,
    ): List<GroupMessage> {
        if (accountId.isBlank() || !this::application.isInitialized) return emptyList()
        require(page >= 0 && pageSize in 1..10000)
        return decodeMessages(
                accountId,
                true,
                journal(accountId, groupId.toString(), true)
                    .page(Math.multiplyExact(page, pageSize), pageSize)
                    .asReversed(),
            )
            .map { it as GroupMessage }
            .distinctBy { it.messageId }
    }

    override fun getPrivateMessagesBefore(
        accountId: String,
        userId: Int,
        before: core.HistoryCursor?,
        limit: Int,
    ): core.LocalHistoryPage<Message> {
        if (accountId.isBlank() || !this::application.isInitialized)
            return core.LocalHistoryPage(emptyList(), null, false)
        val page = journal(accountId, userId.toString(), false).pageBefore(before, limit)
        return core.LocalHistoryPage(
            decodeMessages(accountId, false, page.rows.asReversed())
                .map { it as Message }
                .distinctBy { it.messageId },
            page.before,
            page.hasMore,
        )
    }

    override fun getGroupMessagesBefore(
        accountId: String,
        groupId: Int,
        before: core.HistoryCursor?,
        limit: Int,
    ): core.LocalHistoryPage<GroupMessage> {
        if (accountId.isBlank() || !this::application.isInitialized)
            return core.LocalHistoryPage(emptyList(), null, false)
        val page = journal(accountId, groupId.toString(), true).pageBefore(before, limit)
        return core.LocalHistoryPage(
            decodeMessages(accountId, true, page.rows.asReversed())
                .map { it as GroupMessage }
                .distinctBy { it.messageId },
            page.before,
            page.hasMore,
        )
    }

    override fun clear(accountId: String): Boolean {
        if (accountId.isBlank() || !this::application.isInitialized) {
            return false
        }

        return runCatching {
                val accountDir = getAccountDir(accountId)
                if (accountDir.exists()) {
                    accountDir.deleteRecursively()
                }
                true
            }
            .getOrDefault(false)
    }

    override fun clearConversation(accountId: String, targetId: String, isGroup: Boolean): Boolean {
        if (accountId.isBlank() || !this::application.isInitialized) return false
        return runCatching {
                journal(accountId, targetId, isGroup).delete()
                true
            }
            .getOrDefault(false)
    }

    override fun clearAll(): Boolean {
        if (!this::application.isInitialized) return false
        return runCatching {
                val folder = java.io.File(application.filesDir, HISTORY_DIR_NAME)
                if (folder.exists()) {
                    folder.deleteRecursively()
                }
                true
            }
            .getOrDefault(false)
    }

    @Suppress("UseKtx") // Keep commit() failure visible to the history durability barrier.
    override fun saveConversationSeqIds(accountId: String, ids: Map<String, Long>) {
        if (!this::application.isInitialized) return
        runCatching {
            val prefs =
                application.getSharedPreferences(
                    "conversation_seq_ids_${accountId}",
                    Context.MODE_PRIVATE,
                )
            val json = JSONObject()
            ids.forEach { (key, value) -> json.put(key, value) }
            check(prefs.edit().putString("seq_ids", json.toString()).commit()) {
                "Cursor persistence failed"
            }
        }
            .onFailure {
                timber.log.Timber.w(it, "操作失败")
            }
            .getOrThrow()
    }

    override fun restoreConversationSeqIds(accountId: String): Map<String, Long> {
        if (!this::application.isInitialized) return emptyMap()
        return runCatching {
                val prefs =
                    application.getSharedPreferences(
                        "conversation_seq_ids_${accountId}",
                        Context.MODE_PRIVATE,
                    )
                val jsonStr = prefs.getString("seq_ids", null) ?: return@runCatching emptyMap()
                val json = JSONObject(jsonStr)
                val result = mutableMapOf<String, Long>()
                json.keys().forEach { key ->
                    result[key] = json.optLong(key, 0L)
                }
                result
            }
            .getOrDefault(emptyMap())
    }

    private fun saveMessages(
        accountId: String,
        targetId: String,
        isGroup: Boolean,
        messages: List<Message>,
    ) {
        val directory = getAccountDir(accountId)
        val entries = linkedMapOf<String, core.MessageIdAliases.Entry>()
        messages.forEach { message ->
            val clientId = message.clientMessageId
            if (!clientId.isNullOrBlank() && clientId != message.messageId && message.seqId > 0) {
                val conversation =
                    message.conversationId.ifBlank { if (isGroup) "group:$targetId" else "" }
                entries[clientId] =
                    core.MessageIdAliases.Entry(message.messageId, message.seqId, conversation)
            }
        }
        core.MessageIdAliases.recordAll(directory, targetId, isGroup, entries)
        val aliases =
            core.MessageIdAliases.resolveAll(
                directory,
                targetId,
                isGroup,
                messages.map { it.messageId },
            )
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
                messageToJson(canonical).toString(),
            )
        }
        val superseded = entries.keys.map { core.IndexedHistoryJournal.Row(it, 0, null) }
        journal(accountId, targetId, isGroup).write(rows + superseded)
    }

    private fun decodeMessages(
        accountId: String,
        isGroup: Boolean,
        rows: List<core.IndexedHistoryJournal.Row>,
    ): List<Any> {
        val decoded = rows.map { jsonToMessage(JSONObject(it.body!!), isGroup) }
        fun target(message: Any): String =
            when (message) {
                is Message ->
                    (if (message.senderId.toString() == accountId) message.receiverId
                        else message.senderId)
                        .toString()
                is GroupMessage -> message.groupId.toString()
                else -> error("Unknown history message")
            }
        val directory = getAccountDir(accountId)
        val aliases =
            decoded.groupBy(::target).mapValues { (id, messages) ->
                core.MessageIdAliases.resolveAll(
                    directory,
                    id,
                    isGroup,
                    messages.map { (it as model.ChatMessage).chatMessageId },
                )
            }
        return decoded.map { message ->
            val id = target(message)
            val alias = aliases.getValue(id)[(message as model.ChatMessage).chatMessageId]
            if (alias == null) message
            else {
                val conversation = alias.conversation.ifBlank { if (isGroup) "group:$id" else "" }
                when (message) {
                    is Message ->
                        message.copy(
                            messageId = alias.serverId,
                            seqId = alias.seq,
                            conversationId = conversation,
                            isSent = true,
                            deliveryState = model.MessageDeliveryState.ACCEPTED,
                        )
                    is GroupMessage ->
                        message.copy(
                            messageId = alias.serverId,
                            seqId = alias.seq,
                            conversationId = conversation,
                            isSent = true,
                            deliveryState = model.MessageDeliveryState.ACCEPTED,
                        )
                    else -> message
                }
            }
        }
    }

    private fun loadMessages(
        accountId: String,
        targetId: String,
        isGroup: Boolean,
        from: Long = Long.MIN_VALUE,
        through: Long = Long.MAX_VALUE,
    ): List<Any> =
        decodeMessages(
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
            core.IndexedHistoryJournal.conversation(getAccountDir(accountId), targetId, isGroup)
        )

    private fun getAccountDir(accountId: String): File {
        val folder = File(application.filesDir, HISTORY_DIR_NAME)
        if (!folder.exists()) {
            folder.mkdirs()
        }
        val safeAccountId = accountId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(folder, safeAccountId)
    }

    private fun messageToJson(message: Message): JSONObject =
        JSONObject().apply {
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
            put("clientMessageId", message.clientMessageId)
        }

    private fun groupMessageToJson(message: GroupMessage): JSONObject =
        JSONObject().apply {
            put("senderId", message.senderId)
            put("receiverId", message.groupId)
            put("senderName", message.senderName)
            put("content", message.text)
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
            put("clientMessageId", message.clientMessageId)
        }

    private fun jsonToMessage(json: JSONObject, isGroup: Boolean): Any {
        val messageType = MessageType.valueOf(json.getString("messageType"))

        return if (isGroup) {
            GroupMessage(
                groupId = json.optInt("receiverId", 0).let { if (it < 0) -it else it },
                senderName = json.optString("senderName", "未知用户"),
                text = json.optString("content", ""),
                senderId = json.optInt("senderId", 0),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                isSent = json.optBoolean("isSent", true),
                deliveryState = model.MessageDeliveryState.valueOf(json.getString("deliveryState")),
                messageId = json.optString("messageId", ""),
                replyToMessageId = json.optString("replyToMessageId").takeIf { it.isNotEmpty() },
                replyToContent = json.optString("replyToContent").takeIf { it.isNotEmpty() },
                replyToSender = json.optString("replyToSender").takeIf { it.isNotEmpty() },
                messageType = messageType,
                fileUrl = json.optString("fileUrl").takeIf { it.isNotEmpty() },
                fileName = json.optString("fileName").takeIf { it.isNotEmpty() },
                fileSize = if (!json.isNull("fileSize")) json.optLong("fileSize") else null,
                seqId = json.optLong("seqId", 0),
                conversationId = json.optString("conversationId", ""),
                clientMessageId =
                    json.optString("clientMessageId").takeIf { it.isNotEmpty() && it != "null" },
            )
        } else {
            Message(
                senderId = json.optInt("senderId", 0),
                receiverId = json.optInt("receiverId", 0),
                message = json.optString("content", ""),
                sender = json.optBoolean("sender", false),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                isSent = json.optBoolean("isSent", true),
                deliveryState = model.MessageDeliveryState.valueOf(json.getString("deliveryState")),
                messageId = json.optString("messageId", ""),
                replyToMessageId = json.optString("replyToMessageId").takeIf { it.isNotEmpty() },
                replyToContent = json.optString("replyToContent").takeIf { it.isNotEmpty() },
                replyToSender = json.optString("replyToSender").takeIf { it.isNotEmpty() },
                messageType = messageType,
                fileUrl = json.optString("fileUrl").takeIf { it.isNotEmpty() },
                fileName = json.optString("fileName").takeIf { it.isNotEmpty() },
                fileSize = if (!json.isNull("fileSize")) json.optLong("fileSize") else null,
                seqId = json.optLong("seqId", 0),
                conversationId = json.optString("conversationId", ""),
                clientMessageId =
                    json.optString("clientMessageId").takeIf { it.isNotEmpty() && it != "null" },
            )
        }
    }
}
