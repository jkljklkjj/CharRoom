package presentation.viewmodel

import core.AppLogger
import core.batchOnlineStatus
import core.state.GlobalAppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import model.User

/** 更新用户在线状态 */
fun ChatViewModel.updateUserOnlineStatus(userId: Int, online: Boolean) {
    sessionScope.launch {
        chatState.updateUserOnlineStatus(userId, online)
    }
}

/** 加载好友和群组列表；历史恢复独立于联系人加载。 */
fun ChatViewModel.loadContacts() {
    sessionScope.launch(Dispatchers.IO) {
        val cachedContacts = chatRepository.getCachedContacts()
        if (cachedContacts.isNotEmpty() && chatState.users.value.isEmpty()) {
            chatState.updateUsers(cachedContacts)
        }

        val contacts = chatRepository.fetchAllContacts()
        if (contacts.isNotEmpty()) {
            chatState.replaceUsersPreservingOrder(contacts)
            loadConversationSeqIds()
            saveConversationSeqIds()
            val token = GlobalAppState.currentToken
            if (token != null) {
                core.recoverOperation(
                    onFailure = { e ->
                        AppLogger.w("加载联系人在线状态失败: ${e.message}")
                    }
                ) {
                    val ids =
                        contacts
                            .filterNot { it.isGroup }
                            .map { it.id }
                            .filter { it > 0 && it != AGENT_ASSISTANT_ID }
                    if (ids.isNotEmpty())
                        batchOnlineStatus(token, ids).forEach { (id, online) ->
                            id.toIntOrNull()?.let {
                                chatState.updateUserOnlineStatus(it, online)
                            }
                        }
                }
            }
        }
    }
}

/** 拉取好友和群聊请求 */
fun ChatViewModel.fetchRequests() {
    sessionScope.launch(Dispatchers.IO) {
        core.recoverOperation(
            onFailure = { e ->
                AppLogger.d(" 拉取请求失败: ${e.message}")
                logger.error(e) { "ChatViewModel error" }
            }
        ) {
            AppLogger.d(" 开始拉取好友和群聊请求")
            val friendRequests = chatRepository.fetchFriendRequests()
            val groupRequests = chatRepository.fetchGroupRequests()

            friendRequestState.value = friendRequests
            groupRequestState.value = groupRequests

            AppLogger.d(" 拉取请求完成: 好友请求 ${friendRequests.size} 条, 群聊请求 ${groupRequests.size} 条")
        }
    }
}

/** 获取当前用户信息 */
fun ChatViewModel.getCurrentUserProfile(onResult: (User?) -> Unit) {
    sessionScope.launch {
        val user = chatRepository.getCurrentUserProfile()
        onResult(user)
    }
}

/** 添加好友 */
fun ChatViewModel.addFriend(account: String, onResult: (Boolean) -> Unit) {
    sessionScope.launch {
        val success = chatRepository.addFriend(account)
        if (success) {
            loadContacts()
        }
        onResult(success)
    }
}

/** 删除好友 */
fun ChatViewModel.deleteFriend(friendId: Int, onResult: (Boolean) -> Unit) {
    sessionScope.launch {
        val success = chatRepository.deleteFriend(friendId)
        if (success) {
            loadContacts()
            // 如果当前选中的就是这个好友，取消选中
            if (
                selectedUserState.value?.conversationKey ==
                    model.ConversationKey.privateChat(friendId)
            ) {
                selectedUserState.value = null
            }
        }
        onResult(success)
    }
}

/** 加入群组 */
fun ChatViewModel.addGroup(groupId: String, onResult: (Boolean) -> Unit) {
    sessionScope.launch {
        val success = chatRepository.addGroup(groupId)
        if (success) {
            // 重新加载联系人列表
            loadContacts()
        }
        onResult(success)
    }
}

/** 更新用户个人资料 */
fun ChatViewModel.updateUserProfile(
    username: String,
    phone: String,
    signature: String,
    password: String? = null,
    onResult: (Boolean) -> Unit,
) {
    sessionScope.launch {
        val success = chatRepository.updateUserProfile(username, phone, signature, password)
        onResult(success)
    }
}

/** 更新用户列表 */
fun ChatViewModel.updateUsers(users: List<User>) {
    sessionScope.launch {
        chatState.updateUsers(users)
    }
}
