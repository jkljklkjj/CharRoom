package core

import model.GroupMessage
import model.Message
import model.MessageType
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * 桌面端本地聊天历史存储实现（SQLite 版本）
 *
 * 使用 SQLite 作为本地数据库，支持：
 * - 增量写入（INSERT OR REPLACE）
 * - 按会话查询
 * - 消息上限管理（每会话 1000 条）
 * - Per-account 隔离
 * - 离线队列（带重试和状态）
 */
object DesktopLocalChatHistoryStore : LocalChatHistoryStoreProvider {
    private val logger = io.github.oshai.kotlinlogging.KotlinLogging.logger {}
    private const val HISTORY_DIR_NAME = ".qingliao"
    private const val DB_NAME = "chatroom.db"
    private const val MAX_MESSAGES_PER_CONVERSATION = 1000

    private var connection: Connection? = null

    private fun getConnection(): Connection {
        if (connection == null || connection!!.isClosed) {
            val userHome = System.getProperty("user.home")
            val folder = File(userHome, HISTORY_DIR_NAME)
            if (!folder.exists()) folder.mkdirs()
            val dbFile = File(folder, DB_NAME)
            connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
            initSchema()
        }
        return connection!!
    }

    private fun initSchema() {
        val conn = getConnection()
        conn.createStatement().use { stmt ->
            // 消息表
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS messages (
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
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_messages_conv ON messages(account_id, conversation_id)")
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_messages_ts ON messages(account_id, conversation_id, timestamp)")

            // SeqId 表
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS seq_ids (
                    account_id TEXT NOT NULL,
                    conversation_id TEXT NOT NULL,
                    seq_id INTEGER DEFAULT 0,
                    PRIMARY KEY (account_id, conversation_id)
                )
            """)

            // 离线队列表
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pending_messages (
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
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pending_status ON pending_messages(status)")
        }
    }

    override fun save(accountId: String, privateMessages: List<Message>, groupMessages: List<GroupMessage>) {
        runCatching {
            val conn = getConnection()

            // 保存私聊消息
            privateMessages.forEach { msg ->
                val convId = "user:${minOf(msg.senderId, msg.receiverId)}:${maxOf(msg.senderId, msg.receiverId)}"
                insertMessage(conn, accountId, convId, msg)
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
                insertMessage(conn, accountId, convId, msg)
            }

            // 清理超出上限的旧消息
            cleanupOldMessages(conn, accountId)
        }.onFailure {
            logger.warn(it) { "LocalChatHistoryStore.save failed" }
        }
    }

    private fun insertMessage(conn: Connection, accountId: String, convId: String, msg: Message) {
        val sql = """
            INSERT OR REPLACE INTO messages
            (id, account_id, conversation_id, sender_id, receiver_id, content, timestamp,
             is_sent, message_type, file_url, file_name, file_size,
             reply_to_message_id, reply_to_content, reply_to_sender)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        conn.prepareStatement(sql).use { ps ->
            ps.setString(1, msg.messageId)
            ps.setString(2, accountId)
            ps.setString(3, convId)
            ps.setInt(4, msg.senderId)
            ps.setInt(5, msg.receiverId)
            ps.setString(6, msg.message)
            ps.setLong(7, msg.timestamp)
            ps.setInt(8, if (msg.isSent) 1 else 0)
            ps.setString(9, msg.messageType.name)
            ps.setString(10, msg.fileUrl)
            ps.setString(11, msg.fileName)
            ps.setLong(12, msg.fileSize ?: 0)
            ps.setString(13, msg.replyToMessageId)
            ps.setString(14, msg.replyToContent)
            ps.setString(15, msg.replyToSender)
            ps.executeUpdate()
        }
    }

    private fun cleanupOldMessages(conn: Connection, accountId: String) {
        val sql = """
            DELETE FROM messages WHERE id IN (
                SELECT id FROM messages
                WHERE account_id = ?
                GROUP BY conversation_id, id
                HAVING rowid NOT IN (
                    SELECT rowid FROM messages m2
                    WHERE m2.account_id = messages.account_id
                    AND m2.conversation_id = messages.conversation_id
                    ORDER BY m2.timestamp DESC
                    LIMIT $MAX_MESSAGES_PER_CONVERSATION
                )
            )
        """.trimIndent()
        // 简化版：按会话删除超出的消息
        val getConvs = "SELECT DISTINCT conversation_id FROM messages WHERE account_id = ?"
        conn.prepareStatement(getConvs).use { ps ->
            ps.setString(1, accountId)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    val convId = rs.getString("conversation_id")
                    val deleteOld = """
                        DELETE FROM messages WHERE account_id = ? AND conversation_id = ?
                        AND id NOT IN (
                            SELECT id FROM messages
                            WHERE account_id = ? AND conversation_id = ?
                            ORDER BY timestamp DESC
                            LIMIT $MAX_MESSAGES_PER_CONVERSATION
                        )
                    """.trimIndent()
                    conn.prepareStatement(deleteOld).use { del ->
                        del.setString(1, accountId)
                        del.setString(2, convId)
                        del.setString(3, accountId)
                        del.setString(4, convId)
                        del.executeUpdate()
                    }
                }
            }
        }
    }

    override fun restore(accountId: String): RestoredChatHistory {
        return restorePage(accountId, page = 0, pageSize = 100)
    }

    override fun restorePage(accountId: String, page: Int, pageSize: Int): RestoredChatHistory {
        if (accountId.isBlank()) return RestoredChatHistory()

        val conn = getConnection()
        val privateMessages = loadPrivateMessages(conn, accountId, page, pageSize)
        val groupMessages = loadGroupMessages(conn, accountId, page, pageSize)

        return RestoredChatHistory(
            privateMessages = privateMessages,
            groupMessages = groupMessages
        )
    }

    private fun loadPrivateMessages(conn: Connection, accountId: String, page: Int, pageSize: Int): List<Message> {
        val sql = """
            SELECT * FROM messages
            WHERE account_id = ? AND conversation_id LIKE 'user:%'
            ORDER BY timestamp DESC
            LIMIT ? OFFSET ?
        """.trimIndent()
        return conn.prepareStatement(sql).use { ps ->
            ps.setString(1, accountId)
            ps.setInt(2, pageSize)
            ps.setInt(3, page * pageSize)
            ps.executeQuery().use { rs ->
                generateSequence { if (rs.next()) rs else null }
                    .map { rsToMessage(rs) }
                    .toList()
                    .reversed()
            }
        }
    }

    private fun loadGroupMessages(conn: Connection, accountId: String, page: Int, pageSize: Int): List<GroupMessage> {
        val sql = """
            SELECT * FROM messages
            WHERE account_id = ? AND conversation_id LIKE 'group:%'
            ORDER BY timestamp DESC
            LIMIT ? OFFSET ?
        """.trimIndent()
        return conn.prepareStatement(sql).use { ps ->
            ps.setString(1, accountId)
            ps.setInt(2, pageSize)
            ps.setInt(3, page * pageSize)
            ps.executeQuery().use { rs ->
                generateSequence { if (rs.next()) rs else null }
                    .map { rsToGroupMessage(rs) }
                    .toList()
                    .reversed()
            }
        }
    }

    private fun rsToMessage(rs: ResultSet): Message {
        return Message(
            senderId = rs.getInt("sender_id"),
            receiverId = rs.getInt("receiver_id"),
            message = rs.getString("content") ?: "",
            sender = rs.getInt("is_sent") == 0,
            timestamp = rs.getLong("timestamp"),
            isSent = rs.getInt("is_sent") == 1,
            messageId = rs.getString("id") ?: "",
            replyToMessageId = rs.getString("reply_to_message_id"),
            replyToContent = rs.getString("reply_to_content"),
            replyToSender = rs.getString("reply_to_sender"),
            messageType = try {
                MessageType.valueOf(rs.getString("message_type") ?: "TEXT")
            } catch (_: Exception) { MessageType.TEXT },
            fileUrl = rs.getString("file_url"),
            fileName = rs.getString("file_name"),
            fileSize = rs.getLong("file_size").takeIf { it > 0 }
        )
    }

    private fun rsToGroupMessage(rs: ResultSet): GroupMessage {
        val convId = rs.getString("conversation_id") ?: ""
        val groupId = convId.removePrefix("group:").toIntOrNull() ?: 0
        return GroupMessage(
            groupId = groupId,
            senderName = "",
            text = rs.getString("content") ?: "",
            senderId = rs.getInt("sender_id"),
            timestamp = rs.getLong("timestamp"),
            isSent = rs.getInt("is_sent") == 1,
            messageId = rs.getString("id") ?: "",
            replyToMessageId = rs.getString("reply_to_message_id"),
            replyToContent = rs.getString("reply_to_content"),
            replyToSender = rs.getString("reply_to_sender"),
            messageType = try {
                MessageType.valueOf(rs.getString("message_type") ?: "TEXT")
            } catch (_: Exception) { MessageType.TEXT },
            fileUrl = rs.getString("file_url"),
            fileName = rs.getString("file_name"),
            fileSize = rs.getLong("file_size").takeIf { it > 0 }
        )
    }

    override fun getPrivateMessagesByTimeRange(accountId: String, userId: Int, startTime: Long, endTime: Long): List<Message> {
        val conn = getConnection()
        val convId = "user:${minOf(0, userId)}:${maxOf(0, userId)}"
        val sql = """
            SELECT * FROM messages
            WHERE account_id = ? AND conversation_id = ? AND timestamp BETWEEN ? AND ?
            ORDER BY timestamp ASC
        """.trimIndent()
        return conn.prepareStatement(sql).use { ps ->
            ps.setString(1, accountId)
            ps.setString(2, convId)
            ps.setLong(3, startTime)
            ps.setLong(4, endTime)
            ps.executeQuery().use { rs ->
                generateSequence { if (rs.next()) rs else null }
                    .map { rsToMessage(it) }
                    .toList()
            }
        }
    }

    override fun getGroupMessagesByTimeRange(accountId: String, groupId: Int, startTime: Long, endTime: Long): List<GroupMessage> {
        val conn = getConnection()
        val convId = "group:$groupId"
        val sql = """
            SELECT * FROM messages
            WHERE account_id = ? AND conversation_id = ? AND timestamp BETWEEN ? AND ?
            ORDER BY timestamp ASC
        """.trimIndent()
        return conn.prepareStatement(sql).use { ps ->
            ps.setString(1, accountId)
            ps.setString(2, convId)
            ps.setLong(3, startTime)
            ps.setLong(4, endTime)
            ps.executeQuery().use { rs ->
                generateSequence { if (rs.next()) rs else null }
                    .map { rsToGroupMessage(it) }
                    .toList()
            }
        }
    }

    override fun getPrivateMessagesPage(accountId: String, userId: Int, page: Int, pageSize: Int): List<Message> {
        val conn = getConnection()
        val convId = "user:${minOf(0, userId)}:${maxOf(0, userId)}"
        val sql = """
            SELECT * FROM messages
            WHERE account_id = ? AND conversation_id = ?
            ORDER BY timestamp DESC
            LIMIT ? OFFSET ?
        """.trimIndent()
        return conn.prepareStatement(sql).use { ps ->
            ps.setString(1, accountId)
            ps.setString(2, convId)
            ps.setInt(3, pageSize)
            ps.setInt(4, page * pageSize)
            ps.executeQuery().use { rs ->
                generateSequence { if (rs.next()) rs else null }
                    .map { rsToMessage(it) }
                    .toList()
                    .reversed()
            }
        }
    }

    override fun getGroupMessagesPage(accountId: String, groupId: Int, page: Int, pageSize: Int): List<GroupMessage> {
        val conn = getConnection()
        val convId = "group:$groupId"
        val sql = """
            SELECT * FROM messages
            WHERE account_id = ? AND conversation_id = ?
            ORDER BY timestamp DESC
            LIMIT ? OFFSET ?
        """.trimIndent()
        return conn.prepareStatement(sql).use { ps ->
            ps.setString(1, accountId)
            ps.setString(2, convId)
            ps.setInt(3, pageSize)
            ps.setInt(4, page * pageSize)
            ps.executeQuery().use { rs ->
                generateSequence { if (rs.next()) rs else null }
                    .map { rsToGroupMessage(it) }
                    .toList()
                    .reversed()
            }
        }
    }

    override fun clear(accountId: String): Boolean {
        if (accountId.isBlank()) return false
        return runCatching {
            val conn = getConnection()
            conn.prepareStatement("DELETE FROM messages WHERE account_id = ?").use { ps ->
                ps.setString(1, accountId)
                ps.executeUpdate()
            }
            conn.prepareStatement("DELETE FROM seq_ids WHERE account_id = ?").use { ps ->
                ps.setString(1, accountId)
                ps.executeUpdate()
            }
            conn.prepareStatement("DELETE FROM pending_messages WHERE account_id = ?").use { ps ->
                ps.setString(1, accountId)
                ps.executeUpdate()
            }
            true
        }.getOrDefault(false)
    }

    override fun clearConversation(accountId: String, targetId: String, isGroup: Boolean): Boolean {
        if (accountId.isBlank()) return false
        return runCatching {
            val conn = getConnection()
            val prefix = if (isGroup) "group" else "user"
            val convId = "$prefix:$targetId"
            conn.prepareStatement("DELETE FROM messages WHERE account_id = ? AND conversation_id = ?").use { ps ->
                ps.setString(1, accountId)
                ps.setString(2, convId)
                ps.executeUpdate()
            }
            true
        }.getOrDefault(false)
    }

    override fun clearAll(): Boolean {
        return runCatching {
            val conn = getConnection()
            conn.createStatement().use { stmt ->
                stmt.executeUpdate("DELETE FROM messages")
                stmt.executeUpdate("DELETE FROM seq_ids")
                stmt.executeUpdate("DELETE FROM pending_messages")
            }
            true
        }.getOrDefault(false)
    }

    override fun saveConversationSeqIds(ids: Map<String, Long>) {
        // 此方法保留向后兼容，但新实现使用 per-account 存储
        runCatching {
            val conn = getConnection()
            val sql = "INSERT OR REPLACE INTO seq_ids (account_id, conversation_id, seq_id) VALUES ('default', ?, ?)"
            conn.prepareStatement(sql).use { ps ->
                ids.forEach { (convId, seqId) ->
                    ps.setString(1, convId)
                    ps.setLong(2, seqId)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }.onFailure {
            logger.warn(it) { "saveConversationSeqIds failed" }
        }
    }

    override fun restoreConversationSeqIds(): Map<String, Long> {
        return runCatching {
            val conn = getConnection()
            conn.prepareStatement("SELECT conversation_id, seq_id FROM seq_ids WHERE account_id = 'default'").use { ps ->
                ps.executeQuery().use { rs ->
                    generateSequence { if (rs.next()) rs else null }
                        .associate { it.getString("conversation_id") to it.getLong("seq_id") }
                }
            }
        }.getOrDefault(emptyMap())
    }

    override fun savePendingMessages(privatePending: List<Message>, groupPending: List<GroupMessage>) {
        runCatching {
            val conn = getConnection()
            // 先清除旧的待发送消息
            conn.prepareStatement("DELETE FROM pending_messages WHERE status = 'pending'").use { it.executeUpdate() }

            val sql = """
                INSERT OR REPLACE INTO pending_messages
                (message_id, account_id, conversation_id, message_type, payload)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent()

            privatePending.forEach { msg ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, msg.messageId)
                    ps.setString(2, "default")
                    ps.setString(3, "user:${minOf(msg.senderId, msg.receiverId)}:${maxOf(msg.senderId, msg.receiverId)}")
                    ps.setString(4, "private")
                    ps.setString(5, msg.message)
                    ps.executeUpdate()
                }
            }

            groupPending.forEach { msg ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, msg.messageId)
                    ps.setString(2, "default")
                    ps.setString(3, "group:${msg.groupId}")
                    ps.setString(4, "group")
                    ps.setString(5, msg.text)
                    ps.executeUpdate()
                }
            }
        }.onFailure {
            logger.warn(it) { "savePendingMessages failed" }
        }
    }

    override fun restorePendingMessages(): Pair<List<Message>, List<GroupMessage>> {
        return runCatching {
            val conn = getConnection()
            conn.prepareStatement("SELECT * FROM pending_messages WHERE status = 'pending'").use { ps ->
                ps.executeQuery().use { rs ->
                    val privateList = mutableListOf<Message>()
                    val groupList = mutableListOf<GroupMessage>()

                    while (rs.next()) {
                        val type = rs.getString("message_type")
                        val payload = rs.getString("payload") ?: ""
                        val convId = rs.getString("conversation_id") ?: ""

                        if (type == "private") {
                            val parts = convId.removePrefix("user:").split(":")
                            val receiverId = parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0
                            privateList.add(Message(
                                senderId = 0,
                                receiverId = receiverId,
                                message = payload,
                                timestamp = System.currentTimeMillis(),
                                isSent = true,
                                messageId = rs.getString("message_id") ?: ""
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
                                messageId = rs.getString("message_id") ?: ""
                            ))
                        }
                    }
                    Pair(privateList, groupList)
                }
            }
        }.getOrDefault(Pair(emptyList(), emptyList()))
    }

    override fun clearPendingMessages() {
        runCatching {
            val conn = getConnection()
            conn.prepareStatement("DELETE FROM pending_messages").use { it.executeUpdate() }
        }
    }
}
