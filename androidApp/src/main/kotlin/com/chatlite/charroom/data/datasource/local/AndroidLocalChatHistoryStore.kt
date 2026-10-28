package com.chatlite.charroom.data.datasource.local

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import core.LocalChatHistoryStoreProvider
import core.RestoredChatHistory
import model.GroupMessage
import model.Message
import model.MessageType
import org.json.JSONObject
import java.io.File

/**
 * Android端本地聊天历史存储实现
 */
object AndroidLocalChatHistoryStore : LocalChatHistoryStoreProvider {
    private const val HISTORY_DIR_NAME = "chat_history"
    private const val MAX_PRIVATE_HISTORY = 5000
    private const val MAX_GROUP_HISTORY = 5000

    private lateinit var context: Context

    /**
     * 初始化，需要在Application中调用
     */
    fun init(context: Context) {
        this.context = context.applicationContext
    }

    override fun save(accountId: String, privateMessages: List<Message>, groupMessages: List<GroupMessage>) {
        if (accountId.isBlank() || !this::context.isInitialized) {
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
                        deliveryState = groupMsg.deliveryState
                    )
                })
            }
    }

    override fun confirmMessageId(accountId: String, ack: com.chatlite.proto.MessageProtos.AckMessage) {
        core.MessageIdAliases.confirm(getAccountDir(accountId), accountId, ack)
        val (target, group) = core.MessageIdAliases.target(accountId, ack.conversationId)
        val history = journal(accountId, target, group)
        history.read(ack.messageId)?.let { row ->
            val node = JSONObject(row.body!!)
            node.put("messageId", ack.serverMessageId); node.put("seqId", ack.seqId); node.put("conversationId", ack.conversationId); node.put("isSent", true); node.put("deliveryState", "ACCEPTED")
            history.write(listOf(core.IndexedHistoryJournal.Row(ack.serverMessageId, row.timestamp, node.toString()),
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
        if (accountId.isBlank() || !this::context.isInitialized) return RestoredChatHistory()
        val privateRows = core.IndexedHistoryJournal.page(getAccountDir(accountId), false, page, pageSize)
            .asReversed().map { row -> jsonToMessage(JSONObject(row.body!!), false) as Message }
        val groupRows = core.IndexedHistoryJournal.page(getAccountDir(accountId), true, page, pageSize)
            .asReversed().map { row -> jsonToMessage(JSONObject(row.body!!), true) as GroupMessage }
        return RestoredChatHistory(privateRows, groupRows)
    }

    override fun getPrivateMessagesByTimeRange(accountId: String, userId: Int, startTime: Long, endTime: Long): List<Message> {
        if (accountId.isBlank() || !this::context.isInitialized) {
            return emptyList()
        }

        return loadMessages(accountId, userId.toString(), false, startTime, endTime)
            .filterIsInstance<Message>()
            .filter { it.timestamp in startTime..endTime }
            .sortedBy { it.timestamp }
    }

    override fun getGroupMessagesByTimeRange(accountId: String, groupId: Int, startTime: Long, endTime: Long): List<GroupMessage> {
        if (accountId.isBlank() || !this::context.isInitialized) {
            return emptyList()
        }

        return loadMessages(accountId, groupId.toString(), true, startTime, endTime)
            .filterIsInstance<GroupMessage>()
            .filter { it.timestamp in startTime..endTime }
            .sortedBy { it.timestamp }
    }

    override fun getPrivateMessagesPage(accountId: String, userId: Int, page: Int, pageSize: Int): List<Message> {
        if (accountId.isBlank() || !this::context.isInitialized) return emptyList()
        require(page >= 0 && pageSize in 1..10000)
        return journal(accountId, userId.toString(), false).page(Math.multiplyExact(page, pageSize), pageSize)
            .asReversed().map { row -> jsonToMessage(JSONObject(row.body!!), false) as Message }
    }

    override fun getGroupMessagesPage(accountId: String, groupId: Int, page: Int, pageSize: Int): List<GroupMessage> {
        if (accountId.isBlank() || !this::context.isInitialized) return emptyList()
        require(page >= 0 && pageSize in 1..10000)
        return journal(accountId, groupId.toString(), true).page(Math.multiplyExact(page, pageSize), pageSize)
            .asReversed().map { row -> jsonToMessage(JSONObject(row.body!!), true) as GroupMessage }
    }

    override fun clear(accountId: String): Boolean {
        if (accountId.isBlank() || !this::context.isInitialized) {
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
        if (accountId.isBlank() || !this::context.isInitialized) return false
        return runCatching {
            journal(accountId, targetId, isGroup).delete()
            true
        }.getOrDefault(false)
    }

    override fun clearAll(): Boolean {
        if (!this::context.isInitialized) return false
        return runCatching {
            val folder = java.io.File(context.filesDir, HISTORY_DIR_NAME)
            if (folder.exists()) {
                folder.deleteRecursively()
            }
            true
        }.getOrDefault(false)
    }

    override fun saveConversationSeqIds(accountId: String, ids: Map<String, Long>) {
        if (!this::context.isInitialized) return
        runCatching {
            val prefs = context.getSharedPreferences("conversation_seq_ids_${accountId}", Context.MODE_PRIVATE)
            val json = JSONObject()
            ids.forEach { (key, value) -> json.put(key, value) }
            check(prefs.edit().putString("seq_ids", json.toString()).commit()) { "Cursor persistence failed" }
        }.onFailure {
            timber.log.Timber.w(it, "操作失败")
        }.getOrThrow()
    }

    override fun restoreConversationSeqIds(accountId: String): Map<String, Long> {
        if (!this::context.isInitialized) return emptyMap()
        return runCatching {
            val prefs = context.getSharedPreferences("conversation_seq_ids_${accountId}", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("seq_ids", null) ?: return@runCatching emptyMap()
            val json = JSONObject(jsonStr)
            val result = mutableMapOf<String, Long>()
            json.keys().forEach { key ->
                result[key] = json.optLong(key, 0L)
            }
            result
        }.getOrDefault(emptyMap())
    }

    private fun saveMessages(accountId: String, targetId: String, isGroup: Boolean, messages: List<Message>) {
        val rows = messages.map { message ->
            val alias = core.MessageIdAliases.resolve(getAccountDir(accountId), targetId, isGroup, message.messageId)
            val canonical = if (alias == null) message else message.copy(messageId = alias.serverId,
                seqId = alias.seq, conversationId = alias.conversation, isSent = true, deliveryState = model.MessageDeliveryState.ACCEPTED)
            core.IndexedHistoryJournal.Row(canonical.messageId, canonical.timestamp, messageToJson(canonical).toString())
        }
        journal(accountId, targetId, isGroup).write(rows)
    }

    private fun loadMessages(accountId: String, targetId: String, isGroup: Boolean,
                             from: Long = Long.MIN_VALUE, through: Long = Long.MAX_VALUE): List<Any> =
        journal(accountId, targetId, isGroup).range(from, through).map { row -> jsonToMessage(JSONObject(row.body!!), isGroup) }

    private fun journal(accountId: String, targetId: String, isGroup: Boolean): core.IndexedHistoryJournal =
        core.IndexedHistoryJournal.open(core.IndexedHistoryJournal.conversation(getAccountDir(accountId), targetId, isGroup))

    private fun getAccountDir(accountId: String): File {
        val folder = File(context.filesDir, HISTORY_DIR_NAME)
        if (!folder.exists()) {
            folder.mkdirs()
        }
        val safeAccountId = accountId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(folder, safeAccountId)
    }

    private fun messageToJson(message: Message): JSONObject {
        return JSONObject().apply {
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
        }
    }

    private fun groupMessageToJson(message: GroupMessage): JSONObject {
        return JSONObject().apply {
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
        }
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
                conversationId = json.optString("conversationId", "")
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
                conversationId = json.optString("conversationId", "")
            )
        }
    }
}
