package core

import model.Group
import model.Message
import model.User

/**
 * Api服务单例，兼容现有调用
 */
object ApiService {
    /**
     * 登录，返回 token
     */
    suspend fun login(account: String, password: String): String {
        return core.login(account, password).orEmpty()
    }

    /**
     * 注册，返回账号 ID
     */
    suspend fun register(username: String, password: String): Int {
        return core.register(username, password) ?: -1
    }

    /**
     * 获取用户信息
     */
    suspend fun getUserInfo(token: String): User? {
        return core.getUserInfo(token)
    }

    /**
     * 获取当前用户信息
     */
    suspend fun getCurrentUserProfile(): User? {
        return core.getUserInfo(ServerConfig.Token)
    }

    /**
     * 获取好友列表（对 UI 友好）
     */
    suspend fun fetchFriends(): List<User> {
        return core.getFriendList(ServerConfig.Token)
    }

    /**
     * 获取群组列表（对 UI 友好）
     */
    suspend fun fetchGroups(): List<User> {
        return core.getGroupList(ServerConfig.Token).map { group ->
            User(id = -group.id, username = group.name)
        }
    }

    /**
     * 获取好友列表
     */
    suspend fun getFriendList(token: String): List<User> {
        return core.getFriendList(token)
    }

    /**
     * 获取群组列表
     */
    suspend fun getGroupList(token: String): List<Group> {
        return core.getGroupList(token)
    }

    /**
     * 添加好友
     */
    suspend fun addFriend(account: String): Boolean {
        return core.addFriend(ServerConfig.Token, account)
    }

    /**
     * 加入群组
     */
    suspend fun addGroup(groupId: String): Boolean {
        return core.addGroup(ServerConfig.Token, groupId)
    }

    /**
     * 查询用户详情
     */
    suspend fun getUserDetail(userId: String): User? {
        return core.getUserDetail(ServerConfig.Token, userId)
    }

    /**
     * 查询群组详情
     */
    suspend fun getGroupDetail(groupId: String): User? {
        return core.getGroupDetail(ServerConfig.Token, groupId)?.let { group ->
            User(id = -group.id, username = group.name)
        }
    }

    /**
     * 获取离线消息
     */
    suspend fun getOfflineMessages(): List<Message> {
        return core.getOfflineMessages(ServerConfig.Token)
    }

    /**
     * 发送邮箱更新验证码
     */
    suspend fun sendEmailUpdateVerifyCode(email: String): Boolean {
        return core.sendEmailUpdateVerifyCode(ServerConfig.Token, email)
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
        return core.updateUserProfile(ServerConfig.Token, username, phone, signature, password)
    }

    /**
     * 更新邮箱
     */
    suspend fun updateEmail(newEmail: String, verifyCode: String): Boolean {
        return core.updateEmail(ServerConfig.Token, newEmail, verifyCode)
    }

    /**
     * 获取好友申请列表（UI 用 User 类型）
     */
    suspend fun fetchFriendRequests(): List<User> {
        return core.getFriendRequests(ServerConfig.Token)
            .map { request ->
                User(
                    id = request.id,
                    username = request.senderName,
                    avatarUrl = request.senderAvatar
                )
            }
    }

    /**
     * 获取群聊申请列表（UI 用 User 类型）
     */
    suspend fun fetchGroupRequests(): List<User> {
        return core.getGroupRequests(ServerConfig.Token)
            .map { request ->
                User(
                    id = request.id,
                    username = request.senderName,
                    avatarUrl = request.senderAvatar
                )
            }
    }

    /**
     * 同意好友申请
     */
    suspend fun acceptFriend(requestId: String): Boolean {
        return core.acceptFriend(ServerConfig.Token, requestId)
    }

    /**
     * 拒绝好友申请
     */
    suspend fun rejectFriend(requestId: String): Boolean {
        return core.rejectFriend(ServerConfig.Token, requestId)
    }

    /**
     * 同意群聊申请
     */
    suspend fun acceptGroupApplication(groupId: String, userId: String): Boolean {
        return core.acceptGroupApplication(ServerConfig.Token, groupId, userId)
    }

    /**
     * 拒绝群聊申请
     */
    suspend fun rejectGroupApplication(groupId: String, userId: String): Boolean {
        return core.rejectGroupApplication(ServerConfig.Token, groupId, userId)
    }
}
