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
    private val groupsFile: File = File(System.getProperty("user.home"), ".qingliao/groups.enc"),
    private val profileFile: File = File(System.getProperty("user.home"), ".qingliao/profile.enc"),
    private val messagesFile: File = File(System.getProperty("user.home"), ".qingliao/messages.enc"),
    private val groupMessagesFile: File = File(System.getProperty("user.home"), ".qingliao/group_messages.enc")
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
        runCatching { writeEncrypted(profileFile, json.encodeToString(user)) }
    }

    override suspend fun getUserProfile(): User? {
        return runCatching {
            val text = readEncrypted(profileFile) ?: return@runCatching null
            json.decodeFromString<User>(text)
        }.getOrNull()
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
        runCatching { writeEncrypted(messagesFile, json.encodeToString(messages)) }
    }

    override suspend fun getMessages(userId: Int): List<Message> {
        return runCatching {
            val text = readEncrypted(messagesFile) ?: return@runCatching emptyList()
            json.decodeFromString<List<Message>>(text).filter { it.senderId == userId || it.receiverId == userId }
        }.getOrDefault(emptyList())
    }

    override suspend fun saveGroupMessages(messages: List<GroupMessage>) {
        runCatching { writeEncrypted(groupMessagesFile, json.encodeToString(messages)) }
    }

    override suspend fun getGroupMessages(groupId: Int): List<GroupMessage> {
        return runCatching {
            val text = readEncrypted(groupMessagesFile) ?: return@runCatching emptyList()
            json.decodeFromString<List<GroupMessage>>(text).filter { it.groupId == groupId }
        }.getOrDefault(emptyList())
    }

    override suspend fun clearAll() {
        clearAuth()
        listOf(profileFile, friendsFile, groupsFile, messagesFile, groupMessagesFile).forEach {
            runCatching { if (it.exists()) it.delete() }
        }
    }
}
