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
 * 基于文件存储实现，后续可以替换为数据库实现
 */
class LocalDataSourceImpl(
    private val authFile: File = File(System.getProperty("user.home"), ".qingliao/auth.txt"),
    private val credentialsFile: File = File("credentials.txt"),
    private val friendsFile: File = File(System.getProperty("user.home"), ".qingliao/friends.json"),
    private val groupsFile: File = File(System.getProperty("user.home"), ".qingliao/groups.json")
) : LocalDataSource {

    override suspend fun saveAuth(account: String, accessToken: String, refreshToken: String) {
        try {
            authFile.parentFile?.mkdirs()
            authFile.writeText("$account\n$accessToken\n$refreshToken")
        } catch (_: Exception) {
        }
    }

    override suspend fun getSavedAccount(): String? {
        return runCatching {
            if (authFile.exists()) {
                authFile.readLines().getOrNull(0)?.trim()
            } else {
                // 兼容旧的credentials文件
                if (credentialsFile.exists()) {
                    credentialsFile.readLines().getOrNull(0)?.trim()
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    override suspend fun getSavedAccessToken(): String? {
        return runCatching {
            if (authFile.exists()) {
                authFile.readLines().getOrNull(1)?.trim()
            } else {
                null
            }
        }.getOrNull()
    }

    override suspend fun getSavedRefreshToken(): String? {
        return runCatching {
            if (authFile.exists()) {
                authFile.readLines().getOrNull(2)?.trim()
            } else {
                null
            }
        }.getOrNull()
    }

    override suspend fun clearAuth() {
        try {
            if (authFile.exists()) authFile.delete()
            if (credentialsFile.exists()) credentialsFile.delete()
        } catch (_: Exception) {
        }
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
        runCatching {
            friendsFile.parentFile?.mkdirs()
            friendsFile.writeText(json.encodeToString(friends))
        }
    }

    override suspend fun getFriends(): List<User> {
        return runCatching {
            if (!friendsFile.exists()) {
                emptyList()
            } else {
                json.decodeFromString<List<User>>(friendsFile.readText())
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun saveGroups(groups: List<User>) {
        runCatching {
            groupsFile.parentFile?.mkdirs()
            groupsFile.writeText(json.encodeToString(groups))
        }
    }

    override suspend fun getGroups(): List<User> {
        return runCatching {
            if (!groupsFile.exists()) {
                emptyList()
            } else {
                json.decodeFromString<List<User>>(groupsFile.readText())
            }
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
