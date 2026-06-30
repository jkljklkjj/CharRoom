package com.chatlite.charroom.data.datasource.remote

import com.chatlite.charroom.data.repository.NetworkRepository
import com.chatlite.charroom.data.repository.NetworkRepository.TokenBundle
import core.ApiService
import core.ApiService.LoginTokens
import data.datasource.remote.RemoteDataSource
import model.Group
import model.Message
import model.User
import model.toUiUser

/**
 * Android端远程数据源实现
 * 基于Android现有的NetworkRepository实现，复用现有稳定的网络逻辑
 */
class AndroidRemoteDataSource(
    private val networkRepository: NetworkRepository = NetworkRepository.getInstance()
) : RemoteDataSource {

    override suspend fun login(account: String, password: String): ApiService.LoginTokens? {
        return networkRepository.login(account, password)?.let {
            ApiService.LoginTokens(
                accessToken = it.accessToken,
                refreshToken = it.refreshToken
            )
        }
    }

    override suspend fun register(username: String, password: String): Int {
        return networkRepository.register(username, password)
    }

    override suspend fun verifyRegister(username: String, password: String, email: String, verifyCode: String): Int {
        // 使用core的ApiService实现，与网页端逻辑一致
        return core.ApiService.verifyRegister(username, password, email, verifyCode)
    }

    override suspend fun sendRegisterVerifyCode(email: String): Boolean {
        // 使用core的ApiService实现，与网页端逻辑一致
        return core.ApiService.sendRegisterVerifyCode(email)
    }

    override suspend fun validateToken(token: String): ApiService.LoginTokens? {
        return networkRepository.validateToken(token)?.let {
            ApiService.LoginTokens(
                accessToken = it.accessToken,
                refreshToken = it.refreshToken
            )
        }
    }

    override suspend fun refreshToken(refreshToken: String): ApiService.LoginTokens? {
        return networkRepository.refreshAccessToken(refreshToken)?.let {
            ApiService.LoginTokens(
                accessToken = it.accessToken,
                refreshToken = it.refreshToken
            )
        }
    }

    override suspend fun getUserInfo(token: String): User? {
        // 获取当前用户信息需要自己的ID，这里暂时返回null，后续可以完善
        // 或者通过其他方式获取当前用户ID
        return null
    }

    override suspend fun getFriendList(token: String): List<User> {
        return networkRepository.fetchFriendAndGroupList(token)
            .filter { it.id > 0 } // 好友ID为正值
            .map { localUser ->
                User(
                    id = localUser.id,
                    username = localUser.username,
                    email = localUser.email,
                    phone = localUser.phone,
                    signature = localUser.signature,
                    avatarUrl = localUser.avatarUrl,
                    online = localUser.online
                )
            }
    }

    override suspend fun getGroupList(token: String): List<Group> {
        return networkRepository.fetchFriendAndGroupList(token)
            .filter { it.id < 0 } // 群组ID为负值
            .map { localUser ->
                Group(
                    id = -localUser.id, // Android端群组id是负值，转换为正值
                    name = localUser.username
                )
            }
    }

    override suspend fun addFriend(token: String, account: String): Boolean {
        return networkRepository.addFriend(account, token)
    }

    override suspend fun addGroup(token: String, groupId: String): Boolean {
        return networkRepository.addGroup(groupId, token)
    }

    override suspend fun getUserDetail(token: String, userId: String): User? {
        return networkRepository.getUserDetail(userId, token)?.let { localUser ->
            User(
                id = localUser.id,
                username = localUser.username,
                email = localUser.email,
                phone = localUser.phone,
                signature = localUser.signature,
                avatarUrl = localUser.avatarUrl,
                online = localUser.online
            )
        }
    }

    override suspend fun getGroupDetail(token: String, groupId: String): Group? {
        return networkRepository.getGroupDetail(groupId, token)?.let { localUser ->
            Group(
                id = -localUser.id,
                name = localUser.username
            )
        }
    }

    override suspend fun getOfflineMessages(token: String): List<Message> {
        // 现有实现返回空列表，后续可以完善
        return emptyList()
    }

    override suspend fun syncMessages(token: String, conversationId: String, lastSeqId: Long, limit: Int): core.SyncMessagesResult {
        return core.SyncMessagesResult()
    }

    override suspend fun sendEmailUpdateVerifyCode(token: String, email: String): Boolean {
        // 这个接口Android端还没有实现，暂时返回false
        return false
    }

    override suspend fun updateUserProfile(
        token: String,
        username: String,
        phone: String,
        signature: String,
        password: String?
    ): Boolean {
        val userId = "" // 需要获取当前用户ID
        return networkRepository.updateUserProfile(userId, token, username, phone, signature)
    }

    override suspend fun updateEmail(token: String, newEmail: String, verifyCode: String): Boolean {
        // 这个接口Android端还没有实现，暂时返回false
        return false
    }

    override suspend fun getFriendRequests(token: String): List<User> {
        // 这个接口Android端还没有实现，暂时返回空列表
        return emptyList()
    }

    override suspend fun getGroupRequests(token: String): List<User> {
        // 这个接口Android端还没有实现，暂时返回空列表
        return emptyList()
    }

    override suspend fun acceptFriend(token: String, requestId: String): Boolean {
        // 这个接口Android端还没有实现，暂时返回false
        return false
    }

    override suspend fun rejectFriend(token: String, requestId: String): Boolean {
        // 这个接口Android端还没有实现，暂时返回false
        return false
    }

    override suspend fun acceptGroupApplication(token: String, groupId: String, userId: String): Boolean {
        // 这个接口Android端还没有实现，暂时返回false
        return false
    }

    override suspend fun rejectGroupApplication(token: String, groupId: String, userId: String): Boolean {
        // 这个接口Android端还没有实现，暂时返回false
        return false
    }

    override suspend fun deleteFriend(token: String, friendId: Int): Boolean {
        // Android 暂无删除好友的 API 实现
        return false
    }

    /**
     * 上传头像 - Android特有方法
     */
    suspend fun uploadAvatar(token: String, imageBytes: ByteArray, fileName: String): String? {
        return networkRepository.uploadAvatar(token, imageBytes, fileName)
    }

    /**
     * 连接WebSocket - Android特有方法
     */
    suspend fun connectWebSocket(
        token: String,
        ownUserId: Int,
        onMessage: (Message) -> Unit,
        onStatusUpdate: (clientId: String, online: Boolean) -> Unit,
        onAuthFailed: ((reason: String) -> Unit)? = null
    ): Boolean {
        return networkRepository.connectWebSocket(
            token = token,
            ownUserId = ownUserId,
            onMessage = onMessage,
            onStatusUpdate = onStatusUpdate,
            onAuthFailed = onAuthFailed
        )
    }

    /**
     * 发送聊天消息 - Android特有方法
     */
    fun sendMessage(targetId: Int, content: String, senderId: Int = 0): Boolean {
        return networkRepository.sendMessage(targetId, content, senderId)
    }

    /**
     * 发送群聊消息 - Android特有方法
     */
    fun sendGroupMessage(targetId: Int, content: String, senderId: Int = 0): Boolean {
        return networkRepository.sendGroupMessage(targetId, content, senderId)
    }

    /**
     * 发送AI助手消息 - Android特有方法
     */
    fun sendAgentMessage(targetId: Int, content: String, senderId: Int = 0): Boolean {
        return networkRepository.sendAgentMessage(targetId, content, senderId)
    }

    /**
     * 发送在线状态检查 - Android特有方法
     */
    fun sendCheck(targetId: Int): Boolean {
        return networkRepository.sendCheck(targetId)
    }

    /**
     * 发送登出消息 - Android特有方法
     */
    fun sendLogout(userId: String): Boolean {
        return networkRepository.sendLogout(userId)
    }

    /**
     * 断开WebSocket连接 - Android特有方法
     */
    fun disconnectWebSocket() {
        networkRepository.disconnectWebSocket()
    }

    /**
     * 网络恢复时自动重连 - Android特有方法
     */
    fun reconnect() {
        networkRepository.reconnect()
    }

    /**
     * 网络断开通知 - Android特有方法
     */
    fun onNetworkDisconnected() {
        networkRepository.onNetworkDisconnected()
    }

    /**
     * 确保WebSocket连接正常 - Android特有方法
     */
    fun ensureConnected() {
        networkRepository.ensureConnected()
    }

    /**
     * 应用退出清理 - Android特有方法
     */
    suspend fun onAppQuit() {
        networkRepository.onAppQuit()
    }

    /**
     * 清除连接信息 - Android特有方法
     */
    fun clearConnectionInfo() {
        networkRepository.clearConnectionInfo()
    }
}
