package data.datasource.remote

import core.ApiService
import core.ApiService.LoginTokens
import model.Group
import model.Message
import model.User

/**
 * 远程数据源实现
 * 基于现有的core API实现，只做网络请求转发，不包含任何业务逻辑
 */
class RemoteDataSourceImpl : RemoteDataSource {
    override suspend fun login(account: String, password: String): LoginTokens? {
        return core.ApiService.loginTokens(account, password)
    }

    override suspend fun register(username: String, password: String): Int {
        return core.ApiService.register(username, password)
    }

    override suspend fun verifyRegister(username: String, password: String, email: String, verifyCode: String): Int {
        return core.ApiService.verifyRegister(username, password, email, verifyCode)
    }

    override suspend fun sendRegisterVerifyCode(email: String): Boolean {
        return core.ApiService.sendRegisterVerifyCode(email)
    }

    override suspend fun validateToken(token: String): LoginTokens? {
        return core.ApiService.validateToken(token)
    }

    override suspend fun refreshToken(refreshToken: String): LoginTokens? {
        return core.ApiService.refreshTokenBundle(refreshToken)
    }

    override suspend fun getUserInfo(token: String): User? {
        return core.ApiService.getUserInfo(token)
    }

    override suspend fun getFriendList(token: String): List<User> {
        return core.ApiService.getFriendList(token)
    }

    override suspend fun getGroupList(token: String): List<Group> {
        return core.ApiService.getGroupList(token)
    }

    override suspend fun addFriend(token: String, account: String): Boolean {
        return core.addFriend(token, account)
    }

    override suspend fun addGroup(token: String, groupId: String): Boolean {
        return core.addGroup(token, groupId)
    }

    override suspend fun getUserDetail(token: String, userId: String): User? {
        return core.getUserDetail(token, userId)
    }

    override suspend fun getGroupDetail(token: String, groupId: String): Group? {
        return core.getGroupDetail(token, groupId)
    }

    override suspend fun getOfflineMessages(token: String): List<Message> {
        return core.getOfflineMessages(token)
    }

    override suspend fun sendEmailUpdateVerifyCode(token: String, email: String): Boolean {
        return core.sendEmailUpdateVerifyCode(token, email)
    }

    override suspend fun updateUserProfile(
        token: String,
        username: String,
        phone: String,
        signature: String,
        password: String?
    ): Boolean {
        return core.updateUserProfile(token, username, phone, signature, password)
    }

    override suspend fun updateEmail(token: String, newEmail: String, verifyCode: String): Boolean {
        return core.updateEmail(token, newEmail, verifyCode)
    }

    override suspend fun getFriendRequests(token: String): List<User> {
        return core.getFriendRequests(token)
            .map { request ->
                User(
                    id = request.id,
                    username = request.senderName,
                    avatarUrl = request.senderAvatar
                )
            }
    }

    override suspend fun getGroupRequests(token: String): List<User> {
        return core.getGroupRequests(token)
            .map { request ->
                User(
                    id = request.id,
                    username = request.senderName,
                    avatarUrl = request.senderAvatar
                )
            }
    }

    override suspend fun acceptFriend(token: String, requestId: String): Boolean {
        return core.acceptFriend(token, requestId)
    }

    override suspend fun rejectFriend(token: String, requestId: String): Boolean {
        return core.rejectFriend(token, requestId)
    }

    override suspend fun acceptGroupApplication(token: String, groupId: String, userId: String): Boolean {
        return core.acceptGroupApplication(token, groupId, userId)
    }

    override suspend fun rejectGroupApplication(token: String, groupId: String, userId: String): Boolean {
        return core.rejectGroupApplication(token, groupId, userId)
    }
}
