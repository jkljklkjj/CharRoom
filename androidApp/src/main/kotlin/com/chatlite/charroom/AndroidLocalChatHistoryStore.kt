package com.chatlite.charroom

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object AndroidLocalChatHistoryStore {
    private const val HISTORY_DIR_NAME = "chat_history"

    fun loadChatHistory(context: Context, accountId: Int, peerId: Int): List<ChatMessage> {
        return loadHistory(context, accountId, peerId, false)
    }

    fun saveChatHistory(context: Context, accountId: Int, peerId: Int, messages: List<ChatMessage>) {
        saveHistory(context, accountId, peerId, false, messages)
    }

    fun clearChatHistory(context: Context, accountId: Int, peerId: Int): Boolean {
        return clearHistory(context, accountId, peerId, false)
    }

    fun loadGroupChatHistory(context: Context, accountId: Int, groupId: Int): List<ChatMessage> {
        return loadHistory(context, accountId, groupId, true)
    }

    fun saveGroupChatHistory(context: Context, accountId: Int, groupId: Int, messages: List<ChatMessage>) {
        saveHistory(context, accountId, groupId, true, messages)
    }

    fun clearGroupChatHistory(context: Context, accountId: Int, groupId: Int): Boolean {
        return clearHistory(context, accountId, groupId, true)
    }

    private fun loadHistory(context: Context, accountId: Int, peerId: Int, group: Boolean): List<ChatMessage> {
        return runCatching {
            val file = historyFile(context, accountId, peerId, group)
            if (!file.exists()) return emptyList()
            val text = file.readText()
            if (text.isBlank()) return emptyList()
            val array = JSONArray(text)
            val records = mutableListOf<ChatMessage>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                records.add(jsonToChatMessage(item))
            }
            records
        }.getOrElse {
            it.printStackTrace()
            emptyList()
        }
    }

    private fun saveHistory(context: Context, accountId: Int, peerId: Int, group: Boolean, messages: List<ChatMessage>) {
        runCatching {
            val file = historyFile(context, accountId, peerId, group)
            val array = JSONArray()
            messages.forEach { array.put(chatMessageToJson(it)) }
            file.writeText(array.toString())
        }.onFailure {
            it.printStackTrace()
        }
    }

    private fun clearHistory(context: Context, accountId: Int, peerId: Int, group: Boolean): Boolean {
        return runCatching {
            val file = historyFile(context, accountId, peerId, group)
            file.delete()
        }.getOrDefault(false)
    }

    private fun historyFile(context: Context, accountId: Int, peerId: Int, group: Boolean): File {
        val folder = File(context.filesDir, HISTORY_DIR_NAME)
        if (!folder.exists()) {
            folder.mkdirs()
        }
        val safeAccountId = accountId.toString().replace(Regex("[^A-Za-z0-9_-]"), "_")
        val accountFolder = File(folder, safeAccountId)
        if (!accountFolder.exists()) {
            accountFolder.mkdirs()
        }
        val prefix = if (group) "group" else "chat"
        return File(accountFolder, "${prefix}_${peerId}.json")
    }

    private fun jsonToChatMessage(json: JSONObject): ChatMessage {
        return ChatMessage(
            senderId = json.optInt("senderId", 0),
            receiverId = json.optInt("receiverId", 0),
            content = json.optString("content", ""),
            isMe = json.optBoolean("isMe", false),
            timestamp = json.optLong("timestamp", System.currentTimeMillis()),
            messageId = json.optString("messageId", "")
        )
    }

    private fun chatMessageToJson(message: ChatMessage): JSONObject {
        return JSONObject().apply {
            put("senderId", message.senderId)
            put("receiverId", message.receiverId)
            put("content", message.content)
            put("isMe", message.isMe)
            put("timestamp", message.timestamp)
            put("messageId", message.messageId)
        }
    }
}
