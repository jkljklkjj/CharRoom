package core

import model.GroupMessage
import model.Message

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
    fun save(accountId: String, privateMessages: List<Message>, groupMessages: List<GroupMessage>)

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

    /**
     * 清除指定用户的所有聊天历史
     * @param accountId 用户账号ID
     * @return 是否清除成功
     */
    fun clear(accountId: String): Boolean
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
