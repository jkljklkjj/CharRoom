package viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import model.GroupMessage
import model.Message
import model.User
import model.groupMessages
import model.messages
import model.users

/**
 * 全局聊天状态管理
 * 提供Flow接口用于状态观察，保持与原有全局状态的同步
 */
object ChatStateManager {
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    // 用户列表状态Flow
    val usersFlow: StateFlow<List<User>> = snapshotFlow { users }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    // 私聊消息状态Flow
    val messagesFlow: StateFlow<List<Message>> = snapshotFlow { messages.toList() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    // 群聊消息状态Flow
    val groupMessagesFlow: StateFlow<List<GroupMessage>> = snapshotFlow { groupMessages.toList() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    // 当前选中的用户/群组
    private val _selectedUser = mutableStateOf<User?>(null)
    val selectedUserFlow: StateFlow<User?> = snapshotFlow { _selectedUser.value }
        .stateIn(scope, SharingStarted.Eagerly, null)

    var selectedUser: User?
        get() = _selectedUser.value
        set(value) {
            _selectedUser.value = value
        }

    // 加载更多历史消息的状态
    private val _isLoadingMore = mutableStateOf(false)
    val isLoadingMoreFlow: StateFlow<Boolean> = snapshotFlow { _isLoadingMore.value }
        .stateIn(scope, SharingStarted.Eagerly, false)

    var isLoadingMore: Boolean
        get() = _isLoadingMore.value
        set(value) {
            _isLoadingMore.value = value
        }

    /**
     * 更新用户在线状态
     */
    fun updateUserOnlineStatus(userId: Int, online: Boolean) {
        users = users.map { user ->
            if (user.id == userId) user.copy(online = online) else user
        }

        // 如果当前选中的是这个用户，也更新选中用户的状态
        if (_selectedUser.value?.id == userId) {
            _selectedUser.value = _selectedUser.value?.copy(online = online)
        }
    }

    /**
     * 前置添加私聊消息（用于加载历史消息）
     */
    fun prependMessages(newMessages: List<Message>) {
        val existingIds = messages.map { it.messageId }.toSet()
        val messagesToAdd = newMessages.filter { it.messageId !in existingIds }
        if (messagesToAdd.isNotEmpty()) {
            messages.addAll(0, messagesToAdd)
        }
    }

    /**
     * 前置添加群聊消息（用于加载历史消息）
     */
    fun prependGroupMessages(newMessages: List<GroupMessage>) {
        val existingIds = groupMessages.map { it.messageId }.toSet()
        val messagesToAdd = newMessages.filter { it.messageId !in existingIds }
        if (messagesToAdd.isNotEmpty()) {
            groupMessages.addAll(0, messagesToAdd)
        }
    }
}

// 全局实例，保持API兼容
val chatViewModel = ChatStateManager
