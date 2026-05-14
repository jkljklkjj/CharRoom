package data.datasource.local

import model.GroupMessage
import model.Message
import model.User

/**
 * 本地数据源接口
 * 定义所有本地存储操作
 */
interface LocalDataSource {
    /**
     * 保存认证信息
     */
    suspend fun saveAuth(account: String, accessToken: String, refreshToken: String)

    /**
     * 获取保存的账号
     */
    suspend fun getSavedAccount(): String?

    /**
     * 获取保存的访问令牌
     */
    suspend fun getSavedAccessToken(): String?

    /**
     * 获取保存的刷新令牌
     */
    suspend fun getSavedRefreshToken(): String?

    /**
     * 清除认证信息
     */
    suspend fun clearAuth()

    /**
     * 保存用户信息
     */
    suspend fun saveUserProfile(user: User)

    /**
     * 获取本地保存的用户信息
     */
    suspend fun getUserProfile(): User?

    /**
     * 保存好友列表
     */
    suspend fun saveFriends(friends: List<User>)

    /**
     * 获取本地保存的好友列表
     */
    suspend fun getFriends(): List<User>

    /**
     * 保存群组列表
     */
    suspend fun saveGroups(groups: List<User>)

    /**
     * 获取本地保存的群组列表
     */
    suspend fun getGroups(): List<User>

    /**
     * 保存私聊消息
     */
    suspend fun saveMessages(messages: List<Message>)

    /**
     * 获取本地保存的私聊消息
     */
    suspend fun getMessages(userId: Int): List<Message>

    /**
     * 保存群聊消息
     */
    suspend fun saveGroupMessages(messages: List<GroupMessage>)

    /**
     * 获取本地保存的群聊消息
     */
    suspend fun getGroupMessages(groupId: Int): List<GroupMessage>

    /**
     * 清除所有本地数据
     */
    suspend fun clearAll()
}
