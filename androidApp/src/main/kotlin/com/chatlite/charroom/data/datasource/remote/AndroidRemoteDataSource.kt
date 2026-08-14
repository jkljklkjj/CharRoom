package com.chatlite.charroom.data.datasource.remote

import com.chatlite.charroom.data.repository.NetworkRepository
import com.chatlite.charroom.data.repository.NetworkRepository.TokenBundle
import core.ApiService
import core.ApiService.LoginTokens
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
) {

    suspend fun login(account: String, password: String): ApiService.LoginTokens? {
        return networkRepository.login(account, password)?.let {
            ApiService.LoginTokens(
                accessToken = it.accessToken,
                refreshToken = it.refreshToken
            )
        }
    }

    suspend fun register(username: String, password: String): Int {
        return networkRepository.register(username, password)
    }

    suspend fun verifyRegister(username: String, password: String, email: String, verifyCode: String): Int {
        // 使用core的ApiService实现，与网页端逻辑一致
        return core.ApiService.verifyRegister(username, password, email, verifyCode)
    }

    suspend fun sendRegisterVerifyCode(email: String): Boolean {
        // 使用core的ApiService实现，与网页端逻辑一致
        return core.ApiService.sendRegisterVerifyCode(email)
    }

    suspend fun validateToken(token: String): ApiService.LoginTokens? {
        return networkRepository.validateToken(token)?.let {
            ApiService.LoginTokens(
                accessToken = it.accessToken,
                refreshToken = it.refreshToken
            )
        }
    }

    suspend fun refreshToken(refreshToken: String): ApiService.LoginTokens? {
        return networkRepository.refreshAccessToken(refreshToken)?.let {
            ApiService.LoginTokens(
                accessToken = it.accessToken,
                refreshToken = it.refreshToken
            )
        }
    }

    suspend fun getUserInfo(token: String): User? {
        // 获取当前用户信息需要自己的ID，这里暂时返回null，后续可以完善
        // 或者通过其他方式获取当前用户ID
        return null
    }

    suspend fun getFriendList(token: String): List<User> {
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

    suspend fun getGroupList(token: String): List<Group> {
        return networkRepository.fetchFriendAndGroupList(token)
            .filter { it.id < 0 } // 群组ID为负值
            .map { localUser ->
                Group(
                    id = -localUser.id, // Android端群组id是负值，转换为正值
                    name = localUser.username
                )
            }
    }

    suspend fun addFriend(token: String, account: String): Boolean {
        return networkRepository.addFriend(account, token)
    }

    suspend fun addGroup(token: String, groupId: String): Boolean {
        return networkRepository.addGroup(groupId, token)
    }

    suspend fun getUserDetail(token: String, userId: String): User? {
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

    suspend fun getGroupDetail(token: String, groupId: String): Group? {
        return networkRepository.getGroupDetail(groupId, token)?.let { localUser ->
            Group(
                id = -localUser.id,
                name = localUser.username
            )
        }
    }

    suspend fun getOfflineMessages(token: String): List<Message> {
        // 现有实现返回空列表，后续可以完善
        return emptyList()
    }

    suspend fun syncMessages(token: String, conversationId: String, lastSeqId: Long, limit: Int): core.SyncMessagesResult {
        return core.SyncMessagesResult()
    }

    suspend fun sendEmailUpdateVerifyCode(token: String, email: String): Boolean {
        // 这个接口Android端还没有实现，暂时返回false
        return false
    }

    suspend fun updateUserProfile(
        token: String,
        username: String,
        phone: String,
        signature: String,
        password: String?
    ): Boolean {
        val userId = "" // 需要获取当前用户ID
        return networkRepository.updateUserProfile(userId, token, username, phone, signature)
    }

    suspend fun updateEmail(token: String, newEmail: String, verifyCode: String): Boolean {
        // 这个接口Android端还没有实现，暂时返回false
        return false
    }

    suspend fun getFriendRequests(token: String): List<User> {
        // 这个接口Android端还没有实现，暂时返回空列表
        return emptyList()
    }

    suspend fun getGroupRequests(token: String): List<User> {
        // 这个接口Android端还没有实现，暂时返回空列表
        return emptyList()
    }

    suspend fun acceptFriend(token: String, requestId: String): Boolean {
        // 这个接口Android端还没有实现，暂时返回false
        return false
    }

    suspend fun rejectFriend(token: String, requestId: String): Boolean {
        // 这个接口Android端还没有实现，暂时返回false
        return false
    }

    suspend fun acceptGroupApplication(token: String, groupId: String, userId: String): Boolean {
        // 这个接口Android端还没有实现，暂时返回false
        return false
    }

    suspend fun rejectGroupApplication(token: String, groupId: String, userId: String): Boolean {
        // 这个接口Android端还没有实现，暂时返回false
        return false
    }

    suspend fun deleteFriend(token: String, friendId: Int): Boolean {
        // Android 暂无删除好友的 API 实现
        return false
    }

    /**
     * 上传头像 - Android特有方法
     */
    suspend fun uploadAvatar(token: String, imageBytes: ByteArray, fileName: String): String? {
        return networkRepository.uploadAvatar(token, imageBytes, fileName)
    }

}
