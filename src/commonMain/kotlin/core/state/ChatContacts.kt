package core.state

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import model.ConversationKey
import model.User

interface ChatContactState {
    val selectedChatTarget: StateFlow<User?>
    val users: StateFlow<List<User>>

    suspend fun updateUsers(newUsers: List<User>)

    suspend fun replaceUsersPreservingOrder(newUsers: List<User>)

    suspend fun mergeUsers(incoming: List<User>)

    suspend fun upsertUser(user: User)

    suspend fun upsertUsers(users: List<User>)

    suspend fun updateUserOnlineStatus(userId: Int, online: Boolean)
}

internal class ChatContacts : ChatContactState {
    // 当前选中的聊天对象
    val selectedTargetState = MutableStateFlow<User?>(null)
    override val selectedChatTarget: StateFlow<User?> = selectedTargetState.asStateFlow()

    // 用户列表（包含好友和群组）
    private val _users = MutableStateFlow<List<User>>(emptyList())

    override val users: StateFlow<List<User>> = _users.asStateFlow()

    // 按领域拆分锁，减少互斥争用
    private val usersMutex = Mutex()

    /** 更新用户列表 */
    override suspend fun updateUsers(newUsers: List<User>) = usersMutex.withLock {
        if (_users.value == newUsers) return@withLock
        _users.value = newUsers
    }

    /** 全量替换联系人列表（保留顺序）。 触发全量重组，仅用于初始加载。 */
    override suspend fun replaceUsersPreservingOrder(newUsers: List<User>) = usersMutex.withLock {
        val currentUsers = _users.value
        if (currentUsers == newUsers) return@withLock

        val incomingById = newUsers.associateBy { it.conversationKey }
        val mergedUsers = buildList {
            currentUsers.forEach { existingUser ->
                incomingById[existingUser.conversationKey]?.let { add(it) }
            }
            newUsers.forEach { incomingUser ->
                if (currentUsers.none { it.conversationKey == incomingUser.conversationKey }) {
                    add(incomingUser)
                }
            }
        }

        if (mergedUsers != currentUsers) {
            _users.value = mergedUsers
        }
    }

    /** 增量合并联系人列表。 只更新有变化的字段，不重建列表。 Compose LazyColumn + key 可保持 item 复用。 */
    override suspend fun mergeUsers(incoming: List<User>) = usersMutex.withLock {
        if (incoming.isEmpty()) return@withLock
        val current = _users.value.toMutableList()
        val existingMap = current.associateBy { it.conversationKey }.toMutableMap()
        var changed = false

        for (user in incoming) {
            val existing = existingMap[user.conversationKey]
            if (existing == null) {
                existingMap[user.conversationKey] = user
                changed = true
            } else if (contactMetadataChanged(existing, user)) {
                existingMap[user.conversationKey] = user
                changed = true
            }
        }

        if (changed) {
            _users.value = existingMap.values.toList()
        }
    }

    /** 插入或更新单个联系人 */
    override suspend fun upsertUser(user: User) = usersMutex.withLock {
        val currentUsers = _users.value.toMutableList()
        val index = currentUsers.indexOfFirst { it.conversationKey == user.conversationKey }
        if (index == -1) {
            currentUsers.add(user)
        } else {
            currentUsers[index] = user
        }
        _users.value = currentUsers
    }

    /** 批量插入或更新联系人 */
    override suspend fun upsertUsers(users: List<User>) = usersMutex.withLock {
        if (users.isEmpty()) return@withLock
        val currentUsers = _users.value.toMutableList()
        users.forEach { user ->
            val index = currentUsers.indexOfFirst { it.conversationKey == user.conversationKey }
            if (index == -1) {
                currentUsers.add(user)
            } else {
                currentUsers[index] = user
            }
        }
        _users.value = currentUsers
    }

    /** 更新用户在线状态 */
    override suspend fun updateUserOnlineStatus(userId: Int, online: Boolean) =
        usersMutex.withLock {
            _users.value =
                _users.value.map { user ->
                    if (!user.isGroup && user.id == userId) user.copy(online = online) else user
                }

            // 如果当前选中的是这个用户，也更新选中用户的状态
            if (selectedTargetState.value?.conversationKey == ConversationKey.privateChat(userId)) {
                selectedTargetState.value = selectedTargetState.value?.copy(online = online)
            }
        }

    fun reset() {
        _users.value = emptyList()
    }
}

private fun contactMetadataChanged(previous: User, next: User): Boolean =
    previous.online != next.online ||
        previous.username != next.username ||
        previous.avatarUrl != next.avatarUrl
