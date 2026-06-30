package core

import com.chatlite.i18n.currentStrings
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import model.GroupMessage
import model.Message
import model.MessageType
import java.io.File
import androidx.compose.runtime.mutableStateOf

/**
 * 桌面端本地聊天历史存储实现
 */
object DesktopLocalChatHistoryStore : LocalChatHistoryStoreProvider {
    private const val HISTORY_DIR_NAME = ".qingliao/history"
    private val objectMapper = jacksonObjectMapper()

    override fun save(accountId: String, privateMessages: List<Message>, groupMessages: List<GroupMessage>) {
        // 保存私聊消息
        privateMessages
            .groupBy { it.receiverId }
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
        if (accountId.isBlank()) {
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
        if (accountId.isBlank()) {
            return emptyList()
        }

        return loadMessages(accountId, userId.toString(), false)
            .filterIsInstance<Message>()
            .filter { it.timestamp in startTime..endTime }
            .sortedBy { it.timestamp }
    }

    override fun getGroupMessagesByTimeRange(accountId: String, groupId: Int, startTime: Long, endTime: Long): List<GroupMessage> {
        if (accountId.isBlank()) {
            return emptyList()
        }

        return loadMessages(accountId, groupId.toString(), true)
            .filterIsInstance<GroupMessage>()
            .filter { it.timestamp in startTime..endTime }
            .sortedBy { it.timestamp }
    }

    override fun getPrivateMessagesPage(accountId: String, userId: Int, page: Int, pageSize: Int): List<Message> {
        if (accountId.isBlank()) {
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
        if (accountId.isBlank()) {
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
            val file = getHistoryFile(accountId, targetId, isGroup)
            if (file.exists()) file.delete()
            true
        }.getOrDefault(false)
    }

    override fun clearAll(): Boolean {
        return runCatching {
            val userHome = System.getProperty("user.home")
            val folder = java.io.File(userHome, HISTORY_DIR_NAME)
            if (folder.exists()) {
                folder.deleteRecursively()
            }
            true
        }.getOrDefault(false)
    }

    override fun saveConversationSeqIds(ids: Map<String, Long>) {
        runCatching {
            val userHome = System.getProperty("user.home")
            val folder = java.io.File(userHome, HISTORY_DIR_NAME)
            if (!folder.exists()) folder.mkdirs()
            val file = java.io.File(folder, "seq_ids.json")
            file.writeText(objectMapper.writeValueAsString(ids))
        }.onFailure {
            it.printStackTrace()
        }
    }

    override fun restoreConversationSeqIds(): Map<String, Long> {
        return runCatching<Map<String, Long>> {
            val userHome = System.getProperty("user.home")
            val folder = java.io.File(userHome, HISTORY_DIR_NAME)
            val file = java.io.File(folder, "seq_ids.json")
            if (!file.exists()) return@runCatching emptyMap<String, Long>()
            val text = file.readText()
            if (text.isBlank()) return@runCatching emptyMap<String, Long>()
            objectMapper.readValue(text, objectMapper.typeFactory.constructMapType(
                Map::class.java, String::class.java, Long::class.javaObjectType
            ))
        }.getOrDefault(emptyMap())
    }

    // 内部方法

    private fun saveMessages(accountId: String, targetId: String, isGroup: Boolean, messages: List<Message>) {
        runCatching {
            val file = getHistoryFile(accountId, targetId, isGroup)
            val array = objectMapper.createArrayNode()
            messages.takeLast(500).forEach { // 只保留最近500条
                array.add(messageToJson(it))
            }
            file.writeText(objectMapper.writeValueAsString(array))
        }.onFailure {
            it.printStackTrace()
        }
    }

    private fun loadMessages(accountId: String, targetId: String, isGroup: Boolean): List<Any> {
        return runCatching {
            val file = getHistoryFile(accountId, targetId, isGroup)
            if (!file.exists()) return emptyList()
            val text = file.readText()
            if (text.isBlank()) return emptyList()
            val array = objectMapper.readTree(text) as? ArrayNode ?: return emptyList()
            val records = mutableListOf<Any>()
            for (index in 0 until array.size()) {
                val item = array.get(index) as? ObjectNode ?: continue
                records.add(jsonToMessage(item, isGroup))
            }
            records
        }.getOrElse {
            it.printStackTrace()
            emptyList()
        }
    }

    private fun getHistoryFile(accountId: String, targetId: String, isGroup: Boolean): File {
        val userHome = System.getProperty("user.home")
        val folder = File(userHome, HISTORY_DIR_NAME)
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
        val userHome = System.getProperty("user.home")
        val folder = File(userHome, HISTORY_DIR_NAME)
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

    private fun jsonToMessage(json: ObjectNode, isGroup: Boolean): Any {
        val messageType = try {
            MessageType.valueOf(json.path("messageType").asText("TEXT"))
        } catch (_: Exception) {
            MessageType.TEXT
        }

        return if (isGroup) {
            GroupMessage(
                groupId = json.path("receiverId").asInt(0).let { if (it < 0) -it else it },
                senderName = json.path("senderName").asText(currentStrings["contact.unknown"]),
                text = json.path("content").asText(""),
                senderId = json.path("senderId").asInt(0),
                timestamp = json.path("timestamp").asLong(System.currentTimeMillis()),
                isSent = json.path("isSent").asBoolean(true),
                messageId = json.path("messageId").asText(""),
                replyToMessageId = json.path("replyToMessageId").asText(null),
                replyToContent = json.path("replyToContent").asText(null),
                replyToSender = json.path("replyToSender").asText(null),
                messageType = messageType,
                fileUrl = json.path("fileUrl").asText(null),
                fileName = json.path("fileName").asText(null),
                fileSize = if (json.has("fileSize")) json.path("fileSize").asLong() else null
            )
        } else {
            Message(
                senderId = json.path("senderId").asInt(0),
                receiverId = json.path("receiverId").asInt(0),
                message = json.path("content").asText(""),
                sender = json.path("sender").asBoolean(false),
                timestamp = json.path("timestamp").asLong(System.currentTimeMillis()),
                isSent = json.path("isSent").asBoolean(true),
                messageId = json.path("messageId").asText(""),
                replyToMessageId = json.path("replyToMessageId").asText(null),
                replyToContent = json.path("replyToContent").asText(null),
                replyToSender = json.path("replyToSender").asText(null),
                messageType = messageType,
                fileUrl = json.path("fileUrl").asText(null),
                fileName = json.path("fileName").asText(null),
                fileSize = if (json.has("fileSize")) json.path("fileSize").asLong() else null
            )
        }
    }
}