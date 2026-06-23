package data.datasource.remote

import model.Group
import model.Message
import model.User
import core.ApiService as CoreApiService

/**
 * 远程数据源接口
 * 定义所有远程API操作，只做网络请求，不包含业务逻辑和数据转换
 */
interface RemoteDataSource {
    /**
     * 登录，返回token对
     */
    suspend fun login(account: String, password: String): CoreApiService.LoginTokens?

    /**
     * 注册，返回账号ID
     */
    suspend fun register(username: String, password: String): Int

    /**
     * 验证注册（与网页端逻辑一致），返回账号ID
     */
    suspend fun verifyRegister(username: String, password: String, email: String = "", verifyCode: String = ""): Int

    /**
     * 发送注册验证码
     */
    suspend fun sendRegisterVerifyCode(email: String): Boolean

    /**
     * 验证token有效性，返回新的token对
     */
    suspend fun validateToken(token: String): CoreApiService.LoginTokens?

    /**
     * 刷新token，返回新的token对
     */
    suspend fun refreshToken(refreshToken: String): CoreApiService.LoginTokens?

    /**
     * 获取用户信息
     */
    suspend fun getUserInfo(token: String): User?

    /**
     * 获取好友列表
     */
    suspend fun getFriendList(token: String): List<User>

    /**
     * 获取群组列表
     */
    suspend fun getGroupList(token: String): List<Group>

    /**
     * 添加好友
     */
    suspend fun addFriend(token: String, account: String): Boolean

    /**
     * 加入群组
     */
    suspend fun addGroup(token: String, groupId: String): Boolean

    /**
     * 查询用户详情
     */
    suspend fun getUserDetail(token: String, userId: String): User?

    /**
     * 查询群组详情
     */
    suspend fun getGroupDetail(token: String, groupId: String): Group?

    /**
     * 获取离线消息
     */
    suspend fun getOfflineMessages(token: String): List<Message>

    /**
     * 增量同步消息
     */
    suspend fun syncMessages(token: String, conversationId: String, lastSeqId: Long, limit: Int = 50): core.SyncMessagesResult

    /**
     * 发送邮箱更新验证码
     */
    suspend fun sendEmailUpdateVerifyCode(token: String, email: String): Boolean

    /**
     * 更新用户个人资料
     */
    suspend fun updateUserProfile(
        token: String,
        username: String,
        phone: String,
        signature: String,
        password: String? = null
    ): Boolean

    /**
     * 更新邮箱
     */
    suspend fun updateEmail(token: String, newEmail: String, verifyCode: String): Boolean

    /**
     * 获取好友申请列表
     */
    suspend fun getFriendRequests(token: String): List<User>

    /**
     * 获取群聊申请列表
     */
    suspend fun getGroupRequests(token: String): List<User>

    /**
     * 同意好友申请
     */
    suspend fun acceptFriend(token: String, requestId: String): Boolean

    /**
     * 拒绝好友申请
     */
    suspend fun rejectFriend(token: String, requestId: String): Boolean

    /**
     * 同意群聊申请
     */
    suspend fun acceptGroupApplication(token: String, groupId: String, userId: String): Boolean

    /**
     * 拒绝群聊申请
     */
    suspend fun rejectGroupApplication(token: String, groupId: String, userId: String): Boolean
}
