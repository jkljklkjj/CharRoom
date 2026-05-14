package data.repository

import data.datasource.local.LocalDataSource
import data.datasource.local.LocalDataSourceImpl
import data.datasource.remote.RemoteDataSource
import data.datasource.remote.RemoteDataSourceImpl
import model.Group
import model.Message
import model.User
import model.toUiUser
import model.withAgentAssistant

/**
 * 聊天Repository
 * 处理聊天相关业务逻辑：用户、好友、群组、消息等
 */
class ChatRepository(
    private val remoteDataSource: RemoteDataSource,
    private val localDataSource: LocalDataSource,
    private val authRepository: AuthRepository
) {
    /**
     * 获取当前用户信息
     */
    suspend fun getCurrentUserProfile(): User? {
        val token = authRepository.getCurrentToken() ?: return null
        return remoteDataSource.getUserInfo(token)
    }

    /**
     * 获取好友列表
     */
    suspend fun fetchFriends(): List<User> {
        val token = authRepository.getCurrentToken() ?: return emptyList()
        val remoteFriends = remoteDataSource.getFriendList(token)
        // 保存到本地
        localDataSource.saveFriends(remoteFriends)
        return remoteFriends
    }

    /**
     * 获取群组列表（转换为UI友好的User类型）
     */
    suspend fun fetchGroups(): List<User> {
        val token = authRepository.getCurrentToken() ?: return emptyList()
        val remoteGroups = remoteDataSource.getGroupList(token)
        val uiGroups = remoteGroups.map { it.toUiUser() }
        // 保存到本地
        localDataSource.saveGroups(uiGroups)
        return uiGroups
    }

    /**
     * 获取好友和群组的合并列表（包含AI助手）
     */
    suspend fun fetchAllContacts(): List<User> {
        val friends = fetchFriends()
        val groups = fetchGroups()
        return (friends + groups).withAgentAssistant()
    }

    /**
     * 更新并获取所有联系人（包含AI助手）
     */
    suspend fun updateAllContacts(): List<User> {
        return fetchAllContacts()
    }

    /**
     * 添加好友
     */
    suspend fun addFriend(account: String): Boolean {
        val token = authRepository.getCurrentToken() ?: return false
        return remoteDataSource.addFriend(token, account)
    }

    /**
     * 加入群组
     */
    suspend fun addGroup(groupId: String): Boolean {
        val token = authRepository.getCurrentToken() ?: return false
        return remoteDataSource.addGroup(token, groupId)
    }

    /**
     * 查询用户详情
     */
    suspend fun getUserDetail(userId: String): User? {
        val token = authRepository.getCurrentToken() ?: return null
        return remoteDataSource.getUserDetail(token, userId)
    }

    /**
     * 查询群组详情
     */
    suspend fun getGroupDetail(groupId: String): User? {
        val token = authRepository.getCurrentToken() ?: return null
        val group = remoteDataSource.getGroupDetail(token, groupId)
        return group?.toUiUser()
    }

    /**
     * 获取离线消息（全量）
     */
    suspend fun getOfflineMessages(): List<Message> {
        val token = authRepository.getCurrentToken() ?: return emptyList()
        return remoteDataSource.getOfflineMessages(token)
    }

    /**
     * 分页获取离线消息
     */
    suspend fun getOfflineMessagesPage(page: Int = 0, pageSize: Int = 50): List<Message> {
        val token = authRepository.getCurrentToken() ?: return emptyList()
        // 如果API还不支持分页，先调用全量接口分页返回（兼容模式）
        val allMessages = remoteDataSource.getOfflineMessages(token)
        val start = page * pageSize
        val end = minOf(start + pageSize, allMessages.size)
        return if (start < allMessages.size) allMessages.subList(start, end) else emptyList()
    }

    /**
     * 更新用户个人资料
     */
    suspend fun updateUserProfile(
        username: String,
        phone: String,
        signature: String,
        password: String? = null
    ): Boolean {
        val token = authRepository.getCurrentToken() ?: return false
        return remoteDataSource.updateUserProfile(token, username, phone, signature, password)
    }

    /**
     * 发送邮箱更新验证码
     */
    suspend fun sendEmailUpdateVerifyCode(email: String): Boolean {
        val token = authRepository.getCurrentToken() ?: return false
        return remoteDataSource.sendEmailUpdateVerifyCode(token, email)
    }

    /**
     * 更新邮箱
     */
    suspend fun updateEmail(newEmail: String, verifyCode: String): Boolean {
        val token = authRepository.getCurrentToken() ?: return false
        return remoteDataSource.updateEmail(token, newEmail, verifyCode)
    }

    /**
     * 获取好友申请列表
     */
    suspend fun fetchFriendRequests(): List<User> {
        val token = authRepository.getCurrentToken() ?: return emptyList()
        return remoteDataSource.getFriendRequests(token)
    }

    /**
     * 获取群聊申请列表
     */
    suspend fun fetchGroupRequests(): List<User> {
        val token = authRepository.getCurrentToken() ?: return emptyList()
        return remoteDataSource.getGroupRequests(token)
    }

    /**
     * 同意好友申请
     */
    suspend fun acceptFriend(requestId: String): Boolean {
        val token = authRepository.getCurrentToken() ?: return false
        return remoteDataSource.acceptFriend(token, requestId)
    }

    /**
     * 拒绝好友申请
     */
    suspend fun rejectFriend(requestId: String): Boolean {
        val token = authRepository.getCurrentToken() ?: return false
        return remoteDataSource.rejectFriend(token, requestId)
    }

    /**
     * 同意群聊申请
     */
    suspend fun acceptGroupApplication(groupId: String, userId: String): Boolean {
        val token = authRepository.getCurrentToken() ?: return false
        return remoteDataSource.acceptGroupApplication(token, groupId, userId)
    }

    /**
     * 拒绝群聊申请
     */
    suspend fun rejectGroupApplication(groupId: String, userId: String): Boolean {
        val token = authRepository.getCurrentToken() ?: return false
        return remoteDataSource.rejectGroupApplication(token, groupId, userId)
    }
}

/**
 * 全局单例，兼容旧代码
 */
val GlobalChatRepository = ChatRepository(
    remoteDataSource = RemoteDataSourceImpl(),
    localDataSource = LocalDataSourceImpl(),
    authRepository = GlobalAuthRepository
)
