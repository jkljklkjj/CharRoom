package core

import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.File
import model.ChatMessage
import model.GroupMessage
import model.Message
import model.MessageType

/** Serialization and identity normalization, independent of paging and journal ownership. */
internal class HistoryMessageCodec(
    private val objectMapper: com.fasterxml.jackson.databind.ObjectMapper,
    private val accountDirectory: (String) -> File,
    private val unknownSender: () -> String,
) {
    fun decodeMessages(
        accountId: String,
        isGroup: Boolean,
        rows: List<IndexedHistoryJournal.Row>,
    ): List<Any> {
        val decoded = rows.map {
            jsonToMessage(objectMapper.readTree(it.body!!) as ObjectNode, isGroup)
        }
        fun target(message: Any): String =
            when (message) {
                is Message ->
                    (if (message.senderId.toString() == accountId) message.receiverId
                        else message.senderId)
                        .toString()
                is GroupMessage -> message.groupId.toString()
                else -> error("Unknown history message")
            }
        val directory = accountDirectory(accountId)
        val aliases =
            decoded.groupBy(::target).mapValues { (id, messages) ->
                MessageIdAliases.resolveAll(
                    directory,
                    id,
                    isGroup,
                    messages.map { (it as ChatMessage).chatMessageId },
                )
            }
        return decoded.map { message ->
            resolveMessage(message, target(message), isGroup, aliases.getValue(target(message)))
        }
    }

    fun resolveMessage(
        decoded: Any,
        targetId: String,
        group: Boolean,
        aliases: Map<String, MessageIdAliases.Entry>,
    ): Any {
        // The ACK records the alias before it renames the row, so a crash in between leaves a
        // row stored under its client ID. Resolve it on read: identity, not content, decides.
        val clientId = (decoded as ChatMessage).chatMessageId
        val alias = aliases[clientId] ?: return decoded
        val conversation =
            if (alias.conversation.isBlank()) (if (group) "group:$targetId" else "")
            else alias.conversation
        return when (decoded) {
            is Message ->
                decoded.copy(
                    messageId = alias.serverId,
                    seqId = alias.seq,
                    conversationId = conversation,
                    isSent = true,
                    deliveryState = model.MessageDeliveryState.ACCEPTED,
                )
            is GroupMessage ->
                decoded.copy(
                    messageId = alias.serverId,
                    seqId = alias.seq,
                    conversationId = conversation,
                    isSent = true,
                    deliveryState = model.MessageDeliveryState.ACCEPTED,
                )
            else -> decoded
        }
    }

    /** Canonical rows and a stale client-ID copy describe one send, so keep one bubble. */
    fun <T : ChatMessage> distinctById(messages: List<T>): List<T> {
        val seen = HashSet<String>(messages.size)
        return messages.filter { seen.add(it.chatMessageId) }
    }

    fun messageToJson(message: Message): ObjectNode =
        objectMapper.createObjectNode().apply {
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

    fun jsonToMessage(json: ObjectNode, isGroup: Boolean): Any =
        if (isGroup) groupFromJson(json) else privateFromJson(json)

    private fun groupFromJson(json: ObjectNode): GroupMessage {
        val messageType = MessageType.valueOf(json.required("messageType").asText())
        return GroupMessage(
            groupId = json.path("receiverId").asInt(0).let { if (it < 0) -it else it },
            senderName = json.path("senderName").asText(unknownSender()),
            text = json.path("content").asText(""),
            senderId = json.path("senderId").asInt(0),
            timestamp = json.path("timestamp").asLong(System.currentTimeMillis()),
            isSent = json.path("isSent").asBoolean(true),
            deliveryState =
                model.MessageDeliveryState.valueOf(json.required("deliveryState").asText()),
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
            conversationId = json.path("conversationId").asText(""),
        )
    }

    private fun privateFromJson(json: ObjectNode): Message {
        val messageType = MessageType.valueOf(json.required("messageType").asText())
        return Message(
            senderId = json.path("senderId").asInt(0),
            receiverId = json.path("receiverId").asInt(0),
            message = json.path("content").asText(""),
            sender = json.path("sender").asBoolean(false),
            timestamp = json.path("timestamp").asLong(System.currentTimeMillis()),
            isSent = json.path("isSent").asBoolean(true),
            deliveryState =
                model.MessageDeliveryState.valueOf(json.required("deliveryState").asText()),
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
            conversationId = json.path("conversationId").asText(""),
        )
    }
}

/** Account path normalization shared by the history facade and codec. */
internal class HistoryAccountDirectory(private val root: () -> File) {
    fun account(accountId: String): File {
        val folder = root()
        if (!folder.exists()) {
            folder.mkdirs()
        }
        val safeAccountId = accountId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(folder, safeAccountId)
    }
}
