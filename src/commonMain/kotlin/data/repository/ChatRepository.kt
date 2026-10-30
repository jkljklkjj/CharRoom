package data.repository

import core.acceptFriend
import core.acceptGroupApplication
import core.addFriend
import core.addGroup
import core.deleteFriend
import core.getFriendList
import core.getFriendRequests
import core.getGroupList
import core.getGroupRequests
import core.getUserDetail
import core.getUserInfo
import core.rejectFriend
import core.rejectGroupApplication
import core.sendEmailUpdateVerifyCode
import core.syncMessages
import core.updateEmail
import core.updateUserProfile
import data.datasource.local.LocalDataSource
import data.datasource.local.LocalDataSourceImpl
import model.User
import model.toUiUser
import model.withAgentAssistant

/** 聊天Repository 处理聊天相关业务逻辑：用户、好友、群组、消息等 直接调用 ApiClient 函数，移除 RemoteDataSource 中间层 */
class ChatRepository(
    private val localDataSource: LocalDataSource,
    private val authRepository: AuthRepository,
) {
    /** 获取当前用户信息 */
    suspend fun getCurrentUserProfile(): User? {
        val token = authRepository.getCurrentToken() ?: return null
        return getUserInfo(token)
    }

    /** 获取好友列表 */
    suspend fun fetchFriends(): List<User> {
        val token = authRepository.getCurrentToken() ?: return emptyList()
        val remoteFriends = getFriendList(token)
        // 保存到本地
        localDataSource.saveFriends(remoteFriends)
        return remoteFriends
    }

    /** 获取群组列表（转换为UI友好的User类型） */
    suspend fun fetchGroups(): List<User> {
        val token = authRepository.getCurrentToken() ?: return emptyList()
        val remoteGroups = getGroupList(token)
        val uiGroups = remoteGroups.map { it.toUiUser() }
        // 保存到本地
        localDataSource.saveGroups(uiGroups)
        return uiGroups
    }

    /** 获取好友和群组的合并列表（包含AI助手） */
    suspend fun fetchAllContacts(): List<User> {
        val friends = fetchFriends()
        val groups = fetchGroups()
        return (friends + groups).withAgentAssistant()
    }

    /** 获取本地缓存的联系人列表（用于启动时先渲染旧数据） */
    suspend fun getCachedContacts(): List<User> {
        val friends = localDataSource.getFriends()
        val groups = localDataSource.getGroups()
        return (friends + groups).withAgentAssistant()
    }

    /** 添加好友 */
    suspend fun addFriend(account: String): Boolean {
        val token = authRepository.getCurrentToken() ?: return false
        return core.addFriend(token, account)
    }

    /** 加入群组 */
    suspend fun addGroup(groupId: String): Boolean {
        val token = authRepository.getCurrentToken() ?: return false
        return core.addGroup(token, groupId).isSuccess
    }

    /** 查询用户详情 */
    suspend fun getUserDetail(userId: String): User? {
        val token = authRepository.getCurrentToken() ?: return null
        return getUserDetail(token, userId)
    }

    /** 增量同步消息（基于 seqId 游标）。 */
    suspend fun syncMessages(
        conversationId: String,
        lastSeqId: Long,
        limit: Int = 50,
    ): core.SyncMessagesResult {
        val token = authRepository.getCurrentToken() ?: return core.SyncMessagesResult()
        return syncMessages(token, conversationId, lastSeqId, limit)
    }

    /** 更新用户个人资料 */
    suspend fun updateUserProfile(
        username: String,
        phone: String,
        signature: String,
        password: String? = null,
    ): Boolean {
        val token = authRepository.getCurrentToken() ?: return false
        return updateUserProfile(token, username, phone, signature, password)
    }

    /** 发送邮箱更新验证码 */
    suspend fun sendEmailUpdateVerifyCode(email: String): Boolean {
        val token = authRepository.getCurrentToken() ?: return false
        return sendEmailUpdateVerifyCode(token, email)
    }

    /** 更新邮箱 */
    suspend fun updateEmail(newEmail: String, verifyCode: String): Boolean {
        val token = authRepository.getCurrentToken() ?: return false
        return updateEmail(token, newEmail, verifyCode)
    }

    /** 获取好友申请列表 */
    suspend fun fetchFriendRequests(): List<User> {
        val token = authRepository.getCurrentToken() ?: return emptyList()
        return getFriendRequests(token).map { request ->
            User(
                id = request.id,
                username = request.senderName,
                avatarUrl = request.senderAvatar,
            )
        }
    }

    /** 获取群聊申请列表 */
    suspend fun fetchGroupRequests(): List<User> {
        val token = authRepository.getCurrentToken() ?: return emptyList()
        return getGroupRequests(token).map { request ->
            User(
                id = request.id,
                username = request.senderName,
                avatarUrl = request.senderAvatar,
            )
        }
    }

    /** 同意好友申请 */
    suspend fun acceptFriend(requestId: String): Boolean {
        val token = authRepository.getCurrentToken() ?: return false
        return acceptFriend(token, requestId)
    }

    /** 拒绝好友申请 */
    suspend fun rejectFriend(requestId: String): Boolean {
        val token = authRepository.getCurrentToken() ?: return false
        return rejectFriend(token, requestId)
    }

    /** 删除好友 */
    suspend fun deleteFriend(friendId: Int): Boolean {
        val token = authRepository.getCurrentToken() ?: return false
        return deleteFriend(token, friendId)
    }

    /** 同意群聊申请 */
    suspend fun acceptGroupApplication(groupId: String, userId: String): Boolean {
        val token = authRepository.getCurrentToken() ?: return false
        return acceptGroupApplication(token, groupId, userId)
    }

    /** 拒绝群聊申请 */
    suspend fun rejectGroupApplication(groupId: String, userId: String): Boolean {
        val token = authRepository.getCurrentToken() ?: return false
        return rejectGroupApplication(token, groupId, userId)
    }
}

/** 全局单例，兼容旧代码 */
val GlobalChatRepository =
    ChatRepository(
        localDataSource = LocalDataSourceImpl(),
        authRepository = GlobalAuthRepository,
    )
