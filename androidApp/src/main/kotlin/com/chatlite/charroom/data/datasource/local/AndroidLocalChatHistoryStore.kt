package com.chatlite.charroom.data.datasource.local

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import core.LocalChatHistoryStoreProvider
import core.RestoredChatHistory
import model.GroupMessage
import model.Message
import model.MessageType
import org.json.JSONArray
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
                        receiverId = -groupId, // 使用负的groupId表示群聊
                        timestamp = groupMsg.timestamp,
                        isSent = groupMsg.isSent,
                        messageId = groupMsg.messageId,
                        replyToMessageId = groupMsg.replyToMessageId,
                        replyToContent = groupMsg.replyToContent,
                        replyToSender = groupMsg.replyToSender,
                        messageType = groupMsg.messageType,
                        fileUrl = groupMsg.fileUrl,
                        fileName = groupMsg.fileName,
                        fileSize = groupMsg.fileSize
                    )
                })
            }
    }

    override fun restore(accountId: String): RestoredChatHistory {
        return restorePage(accountId, page = 0, pageSize = 100)
    }

    override fun restorePage(accountId: String, page: Int, pageSize: Int): RestoredChatHistory {
        if (accountId.isBlank() || !this::context.isInitialized) {
            return RestoredChatHistory()
        }

        val accountDir = getAccountDir(accountId)
        if (!accountDir.exists()) {
            return RestoredChatHistory()
        }

        // 加载所有私聊消息
        val privateFiles = accountDir.listFiles { file -> file.name.startsWith("private_") } ?: emptyArray()
        val allPrivateMessages = privateFiles.flatMap { loadMessages(accountId, it.nameWithoutExtension.removePrefix("private_"), false) }
            .filterIsInstance<Message>()
            .sortedByDescending { it.timestamp }

        // 加载所有群聊消息
        val groupFiles = accountDir.listFiles { file -> file.name.startsWith("group_") } ?: emptyArray()
        val allGroupMessages = groupFiles.flatMap { loadMessages(accountId, it.nameWithoutExtension.removePrefix("group_"), true) }
            .filterIsInstance<GroupMessage>()
            .sortedByDescending { it.timestamp }

        // 分页处理
        val privateStart = page * pageSize
        val privateEnd = minOf(privateStart + pageSize, allPrivateMessages.size)
        val privatePage = if (privateStart < allPrivateMessages.size) {
            allPrivateMessages.subList(privateStart, privateEnd).reversed()
        } else {
            emptyList()
        }

        val groupStart = page * pageSize
        val groupEnd = minOf(groupStart + pageSize, allGroupMessages.size)
        val groupPage = if (groupStart < allGroupMessages.size) {
            allGroupMessages.subList(groupStart, groupEnd).reversed()
        } else {
            emptyList()
        }

        return RestoredChatHistory(
            privateMessages = privatePage,
            groupMessages = groupPage
        )
    }

    override fun getPrivateMessagesByTimeRange(accountId: String, userId: Int, startTime: Long, endTime: Long): List<Message> {
        if (accountId.isBlank() || !this::context.isInitialized) {
            return emptyList()
        }

        return loadMessages(accountId, userId.toString(), false)
            .filterIsInstance<Message>()
            .filter { it.timestamp in startTime..endTime }
            .sortedBy { it.timestamp }
    }

    override fun getGroupMessagesByTimeRange(accountId: String, groupId: Int, startTime: Long, endTime: Long): List<GroupMessage> {
        if (accountId.isBlank() || !this::context.isInitialized) {
            return emptyList()
        }

        return loadMessages(accountId, groupId.toString(), true)
            .filterIsInstance<GroupMessage>()
            .filter { it.timestamp in startTime..endTime }
            .sortedBy { it.timestamp }
    }

    override fun getPrivateMessagesPage(accountId: String, userId: Int, page: Int, pageSize: Int): List<Message> {
        if (accountId.isBlank() || !this::context.isInitialized) {
            return emptyList()
        }

        val allMessages = loadMessages(accountId, userId.toString(), false)
            .filterIsInstance<Message>()
            .sortedByDescending { it.timestamp }

        val startIndex = page * pageSize
        val endIndex = minOf(startIndex + pageSize, allMessages.size)

        return if (startIndex < allMessages.size) {
            allMessages.subList(startIndex, endIndex).reversed()
        } else {
            emptyList()
        }
    }

    override fun getGroupMessagesPage(accountId: String, groupId: Int, page: Int, pageSize: Int): List<GroupMessage> {
        if (accountId.isBlank() || !this::context.isInitialized) {
            return emptyList()
        }

        val allMessages = loadMessages(accountId, groupId.toString(), true)
            .filterIsInstance<GroupMessage>()
            .sortedByDescending { it.timestamp }

        val startIndex = page * pageSize
        val endIndex = minOf(startIndex + pageSize, allMessages.size)

        return if (startIndex < allMessages.size) {
            allMessages.subList(startIndex, endIndex).reversed()
        } else {
            emptyList()
        }
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
            val file = getHistoryFile(accountId, targetId, isGroup)
            if (file.exists()) file.delete()
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

    override fun saveConversationSeqIds(ids: Map<String, Long>) {
        if (!this::context.isInitialized) return
        runCatching {
            val prefs = context.getSharedPreferences("conversation_seq_ids", Context.MODE_PRIVATE)
            val json = JSONObject()
            ids.forEach { (key, value) -> json.put(key, value) }
            prefs.edit().putString("seq_ids", json.toString()).apply()
        }.onFailure {
            timber.log.Timber.w(it, "操作失败")
        }
    }

    override fun restoreConversationSeqIds(): Map<String, Long> {
        if (!this::context.isInitialized) return emptyMap()
        return runCatching {
            val prefs = context.getSharedPreferences("conversation_seq_ids", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("seq_ids", null) ?: return@runCatching emptyMap()
            val json = JSONObject(jsonStr)
            val result = mutableMapOf<String, Long>()
            json.keys().forEach { key ->
                result[key] = json.optLong(key, 0L)
            }
            result
        }.getOrDefault(emptyMap())
    }

    // 内部方法

    private fun saveMessages(accountId: String, targetId: String, isGroup: Boolean, messages: List<Message>) {
        runCatching {
            val file = getHistoryFile(accountId, targetId, isGroup)
            val array = JSONArray()
            messages.takeLast(500).forEach { // 只保留最近500条
                array.put(messageToJson(it))
            }
            file.writeText(array.toString())
        }.onFailure {
            timber.log.Timber.w(it, "操作失败")
        }
    }

    private fun loadMessages(accountId: String, targetId: String, isGroup: Boolean): List<Any> {
        return runCatching {
            val file = getHistoryFile(accountId, targetId, isGroup)
            if (!file.exists()) return emptyList()
            val text = file.readText()
            if (text.isBlank()) return emptyList()
            val array = JSONArray(text)
            val records = mutableListOf<Any>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                records.add(jsonToMessage(item, isGroup))
            }
            records
        }.getOrElse {
            timber.log.Timber.w(it, "操作失败")
            emptyList()
        }
    }

    private fun getHistoryFile(accountId: String, targetId: String, isGroup: Boolean): File {
        val folder = File(context.filesDir, HISTORY_DIR_NAME)
        if (!folder.exists()) {
            folder.mkdirs()
        }
        val safeAccountId = accountId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val accountFolder = File(folder, safeAccountId)
        if (!accountFolder.exists()) {
            accountFolder.mkdirs()
        }
        val prefix = if (isGroup) "group" else "private"
        return File(accountFolder, "${prefix}_${targetId}.json")
    }

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
            put("messageId", message.messageId)
            put("replyToMessageId", message.replyToMessageId)
            put("replyToContent", message.replyToContent)
            put("replyToSender", message.replyToSender)
            put("messageType", message.messageType.name)
            put("fileUrl", message.fileUrl)
            put("fileName", message.fileName)
            put("fileSize", message.fileSize)
        }
    }

    private fun jsonToMessage(json: JSONObject, isGroup: Boolean): Any {
        val messageType = try {
            MessageType.valueOf(json.optString("messageType", "TEXT"))
        } catch (_: Exception) {
            MessageType.TEXT
        }

        return if (isGroup) {
            GroupMessage(
                groupId = json.optInt("receiverId", 0).let { if (it < 0) -it else it },
                senderName = json.optString("senderName", "未知用户"),
                text = json.optString("content", ""),
                senderId = json.optInt("senderId", 0),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                isSent = json.optBoolean("isSent", true),
                messageId = json.optString("messageId", ""),
                replyToMessageId = json.optString("replyToMessageId").takeIf { it.isNotEmpty() },
                replyToContent = json.optString("replyToContent").takeIf { it.isNotEmpty() },
                replyToSender = json.optString("replyToSender").takeIf { it.isNotEmpty() },
                messageType = messageType,
                fileUrl = json.optString("fileUrl").takeIf { it.isNotEmpty() },
                fileName = json.optString("fileName").takeIf { it.isNotEmpty() },
                fileSize = if (json.has("fileSize")) json.optLong("fileSize") else null
            )
        } else {
            Message(
                senderId = json.optInt("senderId", 0),
                receiverId = json.optInt("receiverId", 0),
                message = json.optString("content", ""),
                sender = json.optBoolean("sender", false),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                isSent = json.optBoolean("isSent", true),
                messageId = json.optString("messageId", ""),
                replyToMessageId = json.optString("replyToMessageId").takeIf { it.isNotEmpty() },
                replyToContent = json.optString("replyToContent").takeIf { it.isNotEmpty() },
                replyToSender = json.optString("replyToSender").takeIf { it.isNotEmpty() },
                messageType = messageType,
                fileUrl = json.optString("fileUrl").takeIf { it.isNotEmpty() },
                fileName = json.optString("fileName").takeIf { it.isNotEmpty() },
                fileSize = if (json.has("fileSize")) json.optLong("fileSize") else null
            )
        }
    }
}
