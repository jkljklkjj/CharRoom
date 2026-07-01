package com.chatlite.charroom.data.datasource.local

import android.content.Context
import data.datasource.local.LocalDataSource
import model.GroupMessage
import model.Message
import model.User

/**
 * Android端本地数据源实现
 * 使用SharedPreferences存储认证信息
 */
class AndroidLocalDataSourceImpl(
    private val context: Context
) : LocalDataSource {

    override suspend fun saveAuth(account: String, accessToken: String, refreshToken: String, userId: Int) {
        val accountId = userId
        if (accountId > 0) {
            AndroidTokenStorage.save(context, accessToken, accountId, refreshToken)
        }
    }

    override suspend fun getSavedAccount(): String? {
        // 从AndroidTokenStorage读取
        return AndroidTokenStorage.load(context)?.accountId?.toString()
    }

    override suspend fun getSavedAccessToken(): String? {
        return AndroidTokenStorage.load(context)?.token
    }

    override suspend fun getSavedRefreshToken(): String? {
        return AndroidTokenStorage.load(context)?.refreshToken
    }

    override suspend fun getSavedUserId(): Int {
        return AndroidTokenStorage.load(context)?.accountId ?: 0
    }

    override suspend fun clearAuth() {
        AndroidTokenStorage.clear(context)
    }

    // 以下方法暂未实现，后续可以逐步实现
    override suspend fun saveUserProfile(user: User) {
        // TODO: 实现用户信息本地存储
    }

    override suspend fun getUserProfile(): User? {
        // TODO: 实现用户信息本地读取
        return null
    }

    override suspend fun saveFriends(friends: List<User>) {
        // TODO: 实现好友列表本地存储
    }

    override suspend fun getFriends(): List<User> {
        // TODO: 实现好友列表本地读取
        return emptyList()
    }

    override suspend fun saveGroups(groups: List<User>) {
        // TODO: 实现群组列表本地存储
    }

    override suspend fun getGroups(): List<User> {
        // TODO: 实现群组列表本地读取
        return emptyList()
    }

    override suspend fun saveMessages(messages: List<Message>) {
        // 消息存储已经由LocalChatHistoryStore实现
    }

    override suspend fun getMessages(userId: Int): List<Message> {
        // 消息读取已经由LocalChatHistoryStore实现
        return emptyList()
    }

    override suspend fun saveGroupMessages(messages: List<GroupMessage>) {
        // 消息存储已经由LocalChatHistoryStore实现
    }

    override suspend fun getGroupMessages(groupId: Int): List<GroupMessage> {
        // 消息读取已经由LocalChatHistoryStore实现
        return emptyList()
    }

    override suspend fun clearAll() {
        clearAuth()
        // TODO: 清除其他本地数据
    }
}
