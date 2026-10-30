package core

import model.GroupMessage
import model.Message

/** Ordered by timestamp then journal row ID; the boundary is exclusive. */
data class HistoryCursor(val timestamp: Long, val id: String)
data class LocalHistoryPage<T>(val messages: List<T>, val before: HistoryCursor?, val hasMore: Boolean)

/**
 * 本地聊天历史存储接口
 */
interface LocalChatHistoryStoreProvider {
    /**
     * 保存聊天历史
     * @param accountId 用户账号ID
     * @param privateMessages 私聊消息列表
     * @param groupMessages 群聊消息列表
     */
    /** Merge by message ID; a UI snapshot may contain only a page of the full local history. */
    fun save(accountId: String, privateMessages: List<Message>, groupMessages: List<GroupMessage>)

    fun confirmMessageId(accountId: String, ack: com.chatlite.proto.MessageProtos.AckMessage)

    fun deleteMessage(accountId: String, targetId: String, messageId: String, isGroup: Boolean)

    /**
     * 恢复聊天历史（默认加载最近100条）
     * @param accountId 用户账号ID
     * @return 恢复的聊天历史
     */
    fun restore(accountId: String): RestoredChatHistory

    /**
     * 分页加载历史消息
     * @param accountId 用户账号ID
     * @param page 页码，从0开始，0表示最新的一页
     * @param pageSize 每页消息数量
     * @return 恢复的聊天历史
     */
    fun restorePage(accountId: String, page: Int = 0, pageSize: Int = 50): RestoredChatHistory

    /**
     * 按时间范围查询私聊消息
     * @param accountId 用户账号ID
     * @param userId 对方用户ID
     * @param startTime 开始时间戳
     * @param endTime 结束时间戳
     * @return 消息列表
     */
    fun getPrivateMessagesByTimeRange(accountId: String, userId: Int, startTime: Long, endTime: Long): List<Message>

    /**
     * 按时间范围查询群聊消息
     * @param accountId 用户账号ID
     * @param groupId 群组ID
     * @param startTime 开始时间戳
     * @param endTime 结束时间戳
     * @return 消息列表
     */
    fun getGroupMessagesByTimeRange(accountId: String, groupId: Int, startTime: Long, endTime: Long): List<GroupMessage>

    /**
     * 分页查询与指定用户的私聊消息
     * @param accountId 用户账号ID
     * @param userId 对方用户ID
     * @param page 页码，从0开始，0表示最新的一页
     * @param pageSize 每页消息数量
     * @return 消息列表
     */
    fun getPrivateMessagesPage(accountId: String, userId: Int, page: Int = 0, pageSize: Int = 50): List<Message>

    /**
     * 分页查询指定群组的消息
     * @param accountId 用户账号ID
     * @param groupId 群组ID
     * @param page 页码，从0开始，0表示最新的一页
     * @param pageSize 每页消息数量
     * @return 消息列表
     */
    fun getGroupMessagesPage(accountId: String, groupId: Int, page: Int = 0, pageSize: Int = 50): List<GroupMessage>

    fun getPrivateMessagesBefore(accountId: String, userId: Int, before: HistoryCursor? = null, limit: Int = 50): LocalHistoryPage<Message>
    fun getGroupMessagesBefore(accountId: String, groupId: Int, before: HistoryCursor? = null, limit: Int = 50): LocalHistoryPage<GroupMessage>

    /**
     * 清除指定用户的所有聊天历史
     * @param accountId 用户账号ID
     * @return 是否清除成功
     */
    fun clear(accountId: String): Boolean

    /**
     * 清除指定会话的聊天历史（私聊或群聊）
     * @param accountId 用户账号ID
     * @param targetId 私聊时对方用户ID或群聊时的群ID（群为正数）
     * @param isGroup 是否为群聊
     * @return 是否清除成功
     */
    fun clearConversation(accountId: String, targetId: String, isGroup: Boolean): Boolean

    /**
     * 清除当前设备上所有账号的聊天历史（删除历史根目录）
     * @return 是否清除成功
     */
    fun clearAll(): Boolean

    /**
     * 保存会话 seqId 游标（增量同步用）
     * @param ids conversationId -> seqId 映射
     */
    fun saveConversationSeqIds(accountId: String, ids: Map<String, Long>)

    /**
     * 恢复会话 seqId 游标
     * @return conversationId -> seqId 映射
     */
    fun restoreConversationSeqIds(accountId: String): Map<String, Long>


}

/**
 * 恢复的聊天历史数据
 */
data class RestoredChatHistory(
    val privateMessages: List<Message> = emptyList(),
    val groupMessages: List<GroupMessage> = emptyList()
)

// 全局本地聊天历史存储实现，由各平台初始化
lateinit var LocalChatHistoryStore: LocalChatHistoryStoreProvider
