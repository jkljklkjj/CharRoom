package data.datasource.local

import core.json
import model.GroupMessage
import model.Message
import model.User
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

/**
 * 本地数据源实现
 * 基于 AES-256/GCM 加密文件存储，防止本地数据泄露。
 */
class LocalDataSourceImpl(
    private val authFile: File = File(System.getProperty("user.home"), ".qingliao/auth.enc"),
    private val friendsFile: File = File(System.getProperty("user.home"), ".qingliao/friends.enc"),
    private val groupsFile: File = File(System.getProperty("user.home"), ".qingliao/groups.enc")
) : LocalDataSource {

    // ── 写入辅助：加密后写入文件 ────────────────────

    private fun writeEncrypted(file: File, text: String) {
        file.parentFile?.mkdirs()
        file.writeBytes(CryptoUtil.encrypt(text.encodeToByteArray()))
    }

    private fun readEncrypted(file: File): String? {
        if (!file.exists()) return null
        return String(CryptoUtil.decrypt(file.readBytes()))
    }

    // ── Auth 凭证（加密存储） ───────────────────────
    // 格式: account \n accessToken \n refreshToken \n userId

    override suspend fun saveAuth(account: String, accessToken: String, refreshToken: String, userId: Int) {
        try {
            writeEncrypted(authFile, "$account\n$accessToken\n$refreshToken\n$userId")
        } catch (_: Exception) {
        }
    }

    override suspend fun getSavedAccount(): String? {
        return runCatching {
            readEncrypted(authFile)?.lines()?.getOrNull(0)?.trim()
        }.getOrNull()
    }

    override suspend fun getSavedAccessToken(): String? {
        return runCatching {
            readEncrypted(authFile)?.lines()?.getOrNull(1)?.trim()
        }.getOrNull()
    }

    override suspend fun getSavedRefreshToken(): String? {
        return runCatching {
            readEncrypted(authFile)?.lines()?.getOrNull(2)?.trim()
        }.getOrNull()
    }

    override suspend fun getSavedUserId(): Int {
        return runCatching {
            readEncrypted(authFile)?.lines()?.getOrNull(3)?.trim()?.toIntOrNull() ?: 0
        }.getOrDefault(0)
    }

    override suspend fun clearAuth() {
        try {
            if (authFile.exists()) authFile.delete()
        } catch (_: Exception) {
        }
    }

    // ── 用户、好友、群组数据（加密 JSON） ───────────

    override suspend fun saveUserProfile(user: User) {
        // TODO: 实现用户信息本地存储
    }

    override suspend fun getUserProfile(): User? {
        // TODO: 实现用户信息本地读取
        return null
    }

    override suspend fun saveFriends(friends: List<User>) {
        runCatching {
            writeEncrypted(friendsFile, json.encodeToString(friends))
        }
    }

    override suspend fun getFriends(): List<User> {
        return runCatching {
            val text = readEncrypted(friendsFile) ?: return@runCatching emptyList<User>()
            json.decodeFromString<List<User>>(text)
        }.getOrDefault(emptyList())
    }

    override suspend fun saveGroups(groups: List<User>) {
        runCatching {
            writeEncrypted(groupsFile, json.encodeToString(groups))
        }
    }

    override suspend fun getGroups(): List<User> {
        return runCatching {
            val text = readEncrypted(groupsFile) ?: return@runCatching emptyList<User>()
            json.decodeFromString<List<User>>(text)
        }.getOrDefault(emptyList())
    }

    override suspend fun saveMessages(messages: List<Message>) {
        // TODO: 实现私聊消息本地存储
    }

    override suspend fun getMessages(userId: Int): List<Message> {
        // TODO: 实现私聊消息本地读取
        return emptyList()
    }

    override suspend fun saveGroupMessages(messages: List<GroupMessage>) {
        // TODO: 实现群聊消息本地存储
    }

    override suspend fun getGroupMessages(groupId: Int): List<GroupMessage> {
        // TODO: 实现群聊消息本地读取
        return emptyList()
    }

    override suspend fun clearAll() {
        clearAuth()
        // TODO: 清除其他本地数据
    }
}
