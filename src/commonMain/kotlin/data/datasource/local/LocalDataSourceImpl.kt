package data.datasource.local

import core.json
import model.GroupMessage
import model.Message
import model.User
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * 本地数据源实现
 * 基于 AES-256/GCM 加密文件存储，防止本地数据泄露。
 * 使用 FileProvider 接口实现 KMP 兼容。
 */
class LocalDataSourceImpl(
    private val provider: FileProvider = fileProvider
) : LocalDataSource {

    private val dataDir = provider.getAppDataDir()
    private val authFile = "$dataDir/auth.enc"
    private val friendsFile = "$dataDir/friends.enc"
    private val groupsFile = "$dataDir/groups.enc"
    private val profileFile = "$dataDir/profile.enc"
    private val messagesFile = "$dataDir/messages.enc"
    private val groupMessagesFile = "$dataDir/group_messages.enc"

    // ── 写入辅助：加密后写入文件 ────────────────────

    private fun writeEncrypted(path: String, text: String) {
        provider.mkdirs(dataDir)
        provider.writeFile(path, CryptoUtil.encrypt(text.encodeToByteArray()))
    }

    private fun readEncrypted(path: String): String? {
        if (!provider.fileExists(path)) return null
        return String(CryptoUtil.decrypt(provider.readFile(path)!!))
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
            provider.deleteFile(authFile)
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
            runCatching { provider.deleteFile(it) }
        }
    }
}
