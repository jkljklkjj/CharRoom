package com.chatlite.charroom.data.datasource.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import core.LocalChatHistoryStoreProvider
import core.RestoredChatHistory
import model.GroupMessage
import model.Message
import model.MessageType

/**
 * Android端本地聊天历史存储实现（SQLite 版本）
 *
 * 使用 Android 原生 SQLite API，支持：
 * - 增量写入（INSERT OR REPLACE）
 * - 按会话查询
 * - 消息上限管理（每会话 1000 条）
 * - Per-account 隔离
 * - 离线队列（带重试和状态）
 */
object AndroidLocalChatHistoryStore : LocalChatHistoryStoreProvider {
    private const val DB_NAME = "chatroom.db"
    private const val DB_VERSION = 1
    private const val MAX_MESSAGES_PER_CONVERSATION = 1000

    private lateinit var context: Context
    private var dbHelper: DatabaseHelper? = null

    fun init(context: Context) {
        this.context = context.applicationContext
        dbHelper = DatabaseHelper(this.context)
    }

    private fun getDb(): SQLiteDatabase {
        return dbHelper?.writableDatabase ?: throw IllegalStateException("AndroidLocalChatHistoryStore not initialized")
    }

    private class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE messages (
                    id TEXT PRIMARY KEY,
                    account_id TEXT NOT NULL,
                    conversation_id TEXT NOT NULL,
                    sender_id INTEGER,
                    receiver_id INTEGER,
                    content TEXT,
                    timestamp INTEGER,
                    is_sent INTEGER DEFAULT 0,
                    message_type TEXT DEFAULT 'TEXT',
                    file_url TEXT,
                    file_name TEXT,
                    file_size INTEGER,
                    reply_to_message_id TEXT,
                    reply_to_content TEXT,
                    reply_to_sender TEXT,
                    created_at INTEGER DEFAULT (strftime('%s','now') * 1000)
                )
            """)
            db.execSQL("CREATE INDEX idx_messages_conv ON messages(account_id, conversation_id)")
            db.execSQL("CREATE INDEX idx_messages_ts ON messages(account_id, conversation_id, timestamp)")

            db.execSQL("""
                CREATE TABLE seq_ids (
                    account_id TEXT NOT NULL,
                    conversation_id TEXT NOT NULL,
                    seq_id INTEGER DEFAULT 0,
                    PRIMARY KEY (account_id, conversation_id)
                )
            """)

            db.execSQL("""
                CREATE TABLE pending_messages (
                    message_id TEXT PRIMARY KEY,
                    account_id TEXT NOT NULL,
                    conversation_id TEXT NOT NULL,
                    message_type TEXT NOT NULL,
                    payload TEXT,
                    created_at INTEGER DEFAULT (strftime('%s','now') * 1000),
                    retry_count INTEGER DEFAULT 0,
                    status TEXT DEFAULT 'pending'
                )
            """)
            db.execSQL("CREATE INDEX idx_pending_status ON pending_messages(status)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // 版本迁移逻辑
        }
    }

    override fun save(accountId: String, privateMessages: List<Message>, groupMessages: List<GroupMessage>) {
        if (accountId.isBlank() || !::context.isInitialized) return

        val db = getDb()
        db.beginTransaction()
        try {
            // 保存私聊消息
            privateMessages.forEach { msg ->
                val convId = "user:${minOf(msg.senderId, msg.receiverId)}:${maxOf(msg.senderId, msg.receiverId)}"
                insertMessage(db, accountId, convId, msg)
            }

            // 保存群聊消息
            groupMessages.forEach { groupMsg ->
                val convId = "group:${groupMsg.groupId}"
                val msg = Message(
                    senderId = groupMsg.senderId,
                    receiverId = -groupMsg.groupId,
                    message = groupMsg.text,
                    sender = false,
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
                insertMessage(db, accountId, convId, msg)
            }

            // 清理超出上限的旧消息
            cleanupOldMessages(db, accountId)

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun insertMessage(db: SQLiteDatabase, accountId: String, convId: String, msg: Message) {
        val values = ContentValues().apply {
            put("id", msg.messageId)
            put("account_id", accountId)
            put("conversation_id", convId)
            put("sender_id", msg.senderId)
            put("receiver_id", msg.receiverId)
            put("content", msg.message)
            put("timestamp", msg.timestamp)
            put("is_sent", if (msg.isSent) 1 else 0)
            put("message_type", msg.messageType.name)
            put("file_url", msg.fileUrl)
            put("file_name", msg.fileName)
            put("file_size", msg.fileSize ?: 0)
            put("reply_to_message_id", msg.replyToMessageId)
            put("reply_to_content", msg.replyToContent)
            put("reply_to_sender", msg.replyToSender)
        }
        db.insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun cleanupOldMessages(db: SQLiteDatabase, accountId: String) {
        val cursor = db.rawQuery(
            "SELECT DISTINCT conversation_id FROM messages WHERE account_id = ?",
            arrayOf(accountId)
        )
        cursor.use {
            while (it.moveToNext()) {
                val convId = it.getString(0)
                db.execSQL(
                    """
                    DELETE FROM messages WHERE account_id = ? AND conversation_id = ?
                    AND id NOT IN (
                        SELECT id FROM messages
                        WHERE account_id = ? AND conversation_id = ?
                        ORDER BY timestamp DESC
                        LIMIT $MAX_MESSAGES_PER_CONVERSATION
                    )
                    """.trimIndent(),
                    arrayOf(accountId, convId, accountId, convId)
                )
            }
        }
    }

    override fun restore(accountId: String): RestoredChatHistory {
        return restorePage(accountId, page = 0, pageSize = 100)
    }

    override fun restorePage(accountId: String, page: Int, pageSize: Int): RestoredChatHistory {
        if (accountId.isBlank() || !::context.isInitialized) return RestoredChatHistory()

        val db = getDb()
        val privateMessages = loadPrivateMessages(db, accountId, page, pageSize)
        val groupMessages = loadGroupMessages(db, accountId, page, pageSize)

        return RestoredChatHistory(
            privateMessages = privateMessages,
            groupMessages = groupMessages
        )
    }

    private fun loadPrivateMessages(db: SQLiteDatabase, accountId: String, page: Int, pageSize: Int): List<Message> {
        val cursor = db.rawQuery(
            """
            SELECT * FROM messages
            WHERE account_id = ? AND conversation_id LIKE 'user:%'
            ORDER BY timestamp DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            arrayOf(accountId, pageSize.toString(), (page * pageSize).toString())
        )
        return cursor.use {
            val messages = mutableListOf<Message>()
            while (it.moveToNext()) {
                messages.add(cursorToMessage(it))
            }
            messages.reversed()
        }
    }

    private fun loadGroupMessages(db: SQLiteDatabase, accountId: String, page: Int, pageSize: Int): List<GroupMessage> {
        val cursor = db.rawQuery(
            """
            SELECT * FROM messages
            WHERE account_id = ? AND conversation_id LIKE 'group:%'
            ORDER BY timestamp DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            arrayOf(accountId, pageSize.toString(), (page * pageSize).toString())
        )
        return cursor.use {
            val messages = mutableListOf<GroupMessage>()
            while (it.moveToNext()) {
                messages.add(cursorToGroupMessage(it))
            }
            messages.reversed()
        }
    }

    private fun cursorToMessage(cursor: Cursor): Message {
        return Message(
            senderId = cursor.getInt(cursor.getColumnIndexOrThrow("sender_id")),
            receiverId = cursor.getInt(cursor.getColumnIndexOrThrow("receiver_id")),
            message = cursor.getString(cursor.getColumnIndexOrThrow("content")) ?: "",
            sender = cursor.getInt(cursor.getColumnIndexOrThrow("is_sent")) == 0,
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
            isSent = cursor.getInt(cursor.getColumnIndexOrThrow("is_sent")) == 1,
            messageId = cursor.getString(cursor.getColumnIndexOrThrow("id")) ?: "",
            replyToMessageId = cursor.getString(cursor.getColumnIndexOrThrow("reply_to_message_id")),
            replyToContent = cursor.getString(cursor.getColumnIndexOrThrow("reply_to_content")),
            replyToSender = cursor.getString(cursor.getColumnIndexOrThrow("reply_to_sender")),
            messageType = try {
                MessageType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("message_type")) ?: "TEXT")
            } catch (_: Exception) { MessageType.TEXT },
            fileUrl = cursor.getString(cursor.getColumnIndexOrThrow("file_url")),
            fileName = cursor.getString(cursor.getColumnIndexOrThrow("file_name")),
            fileSize = cursor.getLong(cursor.getColumnIndexOrThrow("file_size")).takeIf { it > 0 }
        )
    }

    private fun cursorToGroupMessage(cursor: Cursor): GroupMessage {
        val convId = cursor.getString(cursor.getColumnIndexOrThrow("conversation_id")) ?: ""
        val groupId = convId.removePrefix("group:").toIntOrNull() ?: 0
        return GroupMessage(
            groupId = groupId,
            senderName = "",
            text = cursor.getString(cursor.getColumnIndexOrThrow("content")) ?: "",
            senderId = cursor.getInt(cursor.getColumnIndexOrThrow("sender_id")),
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
            isSent = cursor.getInt(cursor.getColumnIndexOrThrow("is_sent")) == 1,
            messageId = cursor.getString(cursor.getColumnIndexOrThrow("id")) ?: "",
            replyToMessageId = cursor.getString(cursor.getColumnIndexOrThrow("reply_to_message_id")),
            replyToContent = cursor.getString(cursor.getColumnIndexOrThrow("reply_to_content")),
            replyToSender = cursor.getString(cursor.getColumnIndexOrThrow("reply_to_sender")),
            messageType = try {
                MessageType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("message_type")) ?: "TEXT")
            } catch (_: Exception) { MessageType.TEXT },
            fileUrl = cursor.getString(cursor.getColumnIndexOrThrow("file_url")),
            fileName = cursor.getString(cursor.getColumnIndexOrThrow("file_name")),
            fileSize = cursor.getLong(cursor.getColumnIndexOrThrow("file_size")).takeIf { it > 0 }
        )
    }

    override fun getPrivateMessagesByTimeRange(accountId: String, userId: Int, startTime: Long, endTime: Long): List<Message> {
        if (accountId.isBlank() || !::context.isInitialized) return emptyList()

        val db = getDb()
        val convId = "user:${minOf(0, userId)}:${maxOf(0, userId)}"
        val cursor = db.rawQuery(
            """
            SELECT * FROM messages
            WHERE account_id = ? AND conversation_id = ? AND timestamp BETWEEN ? AND ?
            ORDER BY timestamp ASC
            """.trimIndent(),
            arrayOf(accountId, convId, startTime.toString(), endTime.toString())
        )
        return cursor.use {
            val messages = mutableListOf<Message>()
            while (it.moveToNext()) {
                messages.add(cursorToMessage(it))
            }
            messages
        }
    }

    override fun getGroupMessagesByTimeRange(accountId: String, groupId: Int, startTime: Long, endTime: Long): List<GroupMessage> {
        if (accountId.isBlank() || !::context.isInitialized) return emptyList()

        val db = getDb()
        val convId = "group:$groupId"
        val cursor = db.rawQuery(
            """
            SELECT * FROM messages
            WHERE account_id = ? AND conversation_id = ? AND timestamp BETWEEN ? AND ?
            ORDER BY timestamp ASC
            """.trimIndent(),
            arrayOf(accountId, convId, startTime.toString(), endTime.toString())
        )
        return cursor.use {
            val messages = mutableListOf<GroupMessage>()
            while (it.moveToNext()) {
                messages.add(cursorToGroupMessage(it))
            }
            messages
        }
    }

    override fun getPrivateMessagesPage(accountId: String, userId: Int, page: Int, pageSize: Int): List<Message> {
        if (accountId.isBlank() || !::context.isInitialized) return emptyList()

        val db = getDb()
        val convId = "user:${minOf(0, userId)}:${maxOf(0, userId)}"
        val cursor = db.rawQuery(
            """
            SELECT * FROM messages
            WHERE account_id = ? AND conversation_id = ?
            ORDER BY timestamp DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            arrayOf(accountId, convId, pageSize.toString(), (page * pageSize).toString())
        )
        return cursor.use {
            val messages = mutableListOf<Message>()
            while (it.moveToNext()) {
                messages.add(cursorToMessage(it))
            }
            messages.reversed()
        }
    }

    override fun getGroupMessagesPage(accountId: String, groupId: Int, page: Int, pageSize: Int): List<GroupMessage> {
        if (accountId.isBlank() || !::context.isInitialized) return emptyList()

        val db = getDb()
        val convId = "group:$groupId"
        val cursor = db.rawQuery(
            """
            SELECT * FROM messages
            WHERE account_id = ? AND conversation_id = ?
            ORDER BY timestamp DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            arrayOf(accountId, convId, pageSize.toString(), (page * pageSize).toString())
        )
        return cursor.use {
            val messages = mutableListOf<GroupMessage>()
            while (it.moveToNext()) {
                messages.add(cursorToGroupMessage(it))
            }
            messages.reversed()
        }
    }

    override fun clear(accountId: String): Boolean {
        if (accountId.isBlank() || !::context.isInitialized) return false

        return runCatching {
            val db = getDb()
            db.delete("messages", "account_id = ?", arrayOf(accountId))
            db.delete("seq_ids", "account_id = ?", arrayOf(accountId))
            db.delete("pending_messages", "account_id = ?", arrayOf(accountId))
            true
        }.getOrDefault(false)
    }

    override fun clearConversation(accountId: String, targetId: String, isGroup: Boolean): Boolean {
        if (accountId.isBlank() || !::context.isInitialized) return false

        return runCatching {
            val db = getDb()
            val prefix = if (isGroup) "group" else "user"
            val convId = "$prefix:$targetId"
            db.delete("messages", "account_id = ? AND conversation_id = ?", arrayOf(accountId, convId))
            true
        }.getOrDefault(false)
    }

    override fun clearAll(): Boolean {
        if (!::context.isInitialized) return false

        return runCatching {
            val db = getDb()
            db.delete("messages", null, null)
            db.delete("seq_ids", null, null)
            db.delete("pending_messages", null, null)
            true
        }.getOrDefault(false)
    }

    override fun saveConversationSeqIds(ids: Map<String, Long>) {
        if (!::context.isInitialized) return

        runCatching {
            val db = getDb()
            db.beginTransaction()
            try {
                ids.forEach { (convId, seqId) ->
                    val values = ContentValues().apply {
                        put("account_id", "default")
                        put("conversation_id", convId)
                        put("seq_id", seqId)
                    }
                    db.insertWithOnConflict("seq_ids", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }.onFailure {
            timber.log.Timber.w(it, "saveConversationSeqIds failed")
        }
    }

    override fun restoreConversationSeqIds(): Map<String, Long> {
        if (!::context.isInitialized) return emptyMap()

        return runCatching {
            val db = getDb()
            val cursor = db.rawQuery(
                "SELECT conversation_id, seq_id FROM seq_ids WHERE account_id = 'default'",
                null
            )
            cursor.use {
                val result = mutableMapOf<String, Long>()
                while (it.moveToNext()) {
                    result[it.getString(0)] = it.getLong(1)
                }
                result
            }
        }.getOrDefault(emptyMap())
    }

    override fun savePendingMessages(privatePending: List<Message>, groupPending: List<GroupMessage>) {
        if (!::context.isInitialized) return

        runCatching {
            val db = getDb()
            db.beginTransaction()
            try {
                // 先清除旧的待发送消息
                db.delete("pending_messages", "status = 'pending'", null)

                privatePending.forEach { msg ->
                    val values = ContentValues().apply {
                        put("message_id", msg.messageId)
                        put("account_id", "default")
                        put("conversation_id", "user:${minOf(msg.senderId, msg.receiverId)}:${maxOf(msg.senderId, msg.receiverId)}")
                        put("message_type", "private")
                        put("payload", msg.message)
                    }
                    db.insertWithOnConflict("pending_messages", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }

                groupPending.forEach { msg ->
                    val values = ContentValues().apply {
                        put("message_id", msg.messageId)
                        put("account_id", "default")
                        put("conversation_id", "group:${msg.groupId}")
                        put("message_type", "group")
                        put("payload", msg.text)
                    }
                    db.insertWithOnConflict("pending_messages", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }.onFailure {
            timber.log.Timber.w(it, "savePendingMessages failed")
        }
    }

    override fun restorePendingMessages(): Pair<List<Message>, List<GroupMessage>> {
        if (!::context.isInitialized) return Pair(emptyList(), emptyList())

        return runCatching {
            val db = getDb()
            val cursor = db.rawQuery(
                "SELECT * FROM pending_messages WHERE status = 'pending'",
                null
            )
            cursor.use {
                val privateList = mutableListOf<Message>()
                val groupList = mutableListOf<GroupMessage>()

                while (it.moveToNext()) {
                    val type = it.getString(it.getColumnIndexOrThrow("message_type"))
                    val payload = it.getString(it.getColumnIndexOrThrow("payload")) ?: ""
                    val convId = it.getString(it.getColumnIndexOrThrow("conversation_id")) ?: ""

                    if (type == "private") {
                        val parts = convId.removePrefix("user:").split(":")
                        val receiverId = parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0
                        privateList.add(Message(
                            senderId = 0,
                            receiverId = receiverId,
                            message = payload,
                            timestamp = System.currentTimeMillis(),
                            isSent = true,
                            messageId = it.getString(it.getColumnIndexOrThrow("message_id")) ?: ""
                        ))
                    } else if (type == "group") {
                        val groupId = convId.removePrefix("group:").toIntOrNull() ?: 0
                        groupList.add(GroupMessage(
                            groupId = groupId,
                            senderName = "",
                            text = payload,
                            senderId = 0,
                            timestamp = System.currentTimeMillis(),
                            isSent = true,
                            messageId = it.getString(it.getColumnIndexOrThrow("message_id")) ?: ""
                        ))
                    }
                }
                Pair(privateList, groupList)
            }
        }.getOrDefault(Pair(emptyList(), emptyList()))
    }

    override fun clearPendingMessages() {
        if (!::context.isInitialized) return

        runCatching {
            val db = getDb()
            db.delete("pending_messages", null, null)
        }
    }
}
