package presentation.viewmodel

import core.GlobalApiService
import core.LocalChatHistoryStore
import core.batchOnlineStatus
import core.state.ChatState
import core.state.GlobalAppState
import data.repository.ChatRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import model.GroupMessage
import model.User

private val logger = KotlinLogging.logger {}

/**
 * 会话同步服务：负责联系人加载、SeqId 增量同步、离线消息拉取。
 *
 * 从 ChatViewModel 中提取，降低 ViewModel 复杂度。
 */
class ConversationSyncService(
    private val chatRepository: ChatRepository,
    private val chatState: ChatState,
    private val scope: CoroutineScope
) {

    /**
     * 加载好友和群组列表 + 触发 SeqId 增量同步。
     */
    fun loadContacts(onComplete: (() -> Unit)? = null) {
        scope.launch(Dispatchers.IO) {
            val cachedContacts = chatRepository.getCachedContacts()
            if (cachedContacts.isNotEmpty() && chatState.users.value.isEmpty()) {
                chatState.updateUsers(cachedContacts)
            }

            val contacts = chatRepository.fetchAllContacts()
            if (contacts.isNotEmpty()) {
                chatState.replaceUsersPreservingOrder(contacts)
                loadConversationSeqIds()
                syncAllConversationsFromContacts(contacts)
                saveConversationSeqIds()
            }
            onComplete?.invoke()
        }
    }

    /**
     * 启动增量同步：一次性拉取所有离线消息，按 conversationId 分发。
     */
    private suspend fun syncAllConversationsFromContacts(contacts: List<User>) {
        try {
            val allMessages = chatRepository.getOfflineMessages()
            if (allMessages.isEmpty()) return

            val (privateMsgIds, groupMsgIds) = allMessages.partition {
                it.conversationId.isBlank() || !it.conversationId.startsWith("group:")
            }

            // 私聊消息：按 conversationId 分发
            val privateByConv = privateMsgIds.groupBy { msg ->
                if (msg.conversationId.isNotBlank()) msg.conversationId
                else {
                    val ids = listOf(GlobalAppState.currentUserId?.toString() ?: "0", msg.senderId.toString()).sorted()
                    "user:${ids[0]}:${ids[1]}"
                }
            }
            privateByConv.forEach { (convId, msgs) ->
                chatState.prependMessages(msgs.sortedBy { it.timestamp }, markAsUnread = true)
                val maxSeqId = msgs.maxOfOrNull { it.seqId } ?: return@forEach
                if (maxSeqId > 0L) chatState.updateConversationSeqId(convId, maxSeqId)
            }

            // 群聊消息
            val groupByConv = groupMsgIds.groupBy { it.conversationId }
            groupByConv.forEach { (convId, msgs) ->
                val groupMessages = msgs.map { msg ->
                    val gid = convId.removePrefix("group:").toIntOrNull() ?: return@forEach
                    GroupMessage(
                        groupId = gid,
                        senderName = msg.senderId.toString(),
                        text = msg.message,
                        senderId = msg.senderId,
                        timestamp = msg.timestamp,
                        isSent = true,
                        messageId = msg.messageId,
                        seqId = msg.seqId,
                        conversationId = convId
                    )
                }
                chatState.prependGroupMessages(groupMessages.sortedBy { it.timestamp }, markAsUnread = true)
                val maxSeqId = msgs.maxOfOrNull { it.seqId } ?: return@forEach
                if (maxSeqId > 0L) chatState.updateConversationSeqId(convId, maxSeqId)
            }

            println("[SyncService] 增量同步完成: ${allMessages.size} 条消息, ${privateByConv.size} 个私聊, ${groupByConv.size} 个群聊")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            println("[SyncService] 增量同步失败: ${e.message}")
        }
    }

    /**
     * 全量增量同步（基于 seqId 游标）。
     */
    suspend fun syncAllConversations() {
        println("[SyncService] 开始增量同步所有会话")

        val currentUserId = GlobalAppState.currentUserId ?: run {
            println("[SyncService] 当前用户ID为空，跳过同步")
            return
        }

        // 1. 从本地存储恢复 seqId 游标
        try {
            val savedSeqIds = LocalChatHistoryStore.restoreConversationSeqIds()
            if (savedSeqIds.isNotEmpty()) {
                for ((convId, seqId) in savedSeqIds) {
                    chatState.updateConversationSeqId(convId, seqId)
                }
                println("[SyncService] 从本地恢复了 ${savedSeqIds.size} 个会话的 seqId 游标")
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            println("[SyncService] 恢复 seqId 游标失败: ${e.message}")
        }

        // 2. 获取好友列表
        val friends = try {
            chatRepository.fetchFriends()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            println("[SyncService] 获取好友列表失败: ${e.message}")
            return
        }

        // 批量拉取在线状态
        syncOnlineStatus(friends)

        println("[SyncService] 开始同步 ${friends.size} 个好友的会话")

        // 3. 对每个好友进行增量同步
        for (friend in friends) {
            syncConversation(currentUserId, friend.id, isGroup = false)
        }

        // 4. 增量同步群聊消息
        syncGroupConversations()

        // 5. 持久化
        persistSeqIds()
        saveChatHistoryToLocal()
        println("[SyncService] 增量同步完成")
    }

    private suspend fun syncOnlineStatus(friends: List<User>) {
        try {
            val token = GlobalAppState.currentToken
            if (token != null && friends.isNotEmpty()) {
                val friendIds = friends.map { it.id }.filter { it > 0 && it != 900000001 }
                val statusMap = batchOnlineStatus(token, friendIds)
                for ((userIdStr, online) in statusMap) {
                    val userId = userIdStr.toIntOrNull() ?: continue
                    chatState.updateUserOnlineStatus(userId, online)
                }
                println("[SyncService] 批量在线状态已更新: ${statusMap.size} 人")
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
    }

    private suspend fun syncConversation(currentUserId: Int, targetId: Int, isGroup: Boolean) {
        val smallId = minOf(currentUserId, targetId)
        val bigId = maxOf(currentUserId, targetId)
        val conversationId = if (isGroup) "group:$targetId" else "user:$smallId:$bigId"

        var lastSeqId = chatState.getConversationSeqId(conversationId)
        var hasMore = true
        var pageCount = 0

        while (hasMore) {
            try {
                val result = chatRepository.syncMessages(conversationId, lastSeqId, 50)
                if (result.messages.isEmpty()) break

                pageCount++
                val existingIds = if (isGroup) {
                    chatState.groupMessages.value.map { it.messageId }.toSet()
                } else {
                    chatState.messages.value.map { it.messageId }.toSet()
                }
                val newMessages = result.messages.filter { it.messageId !in existingIds }

                println("[SyncService] 会话 $conversationId: 第 $pageCount 页, 获取 ${result.messages.size} 条, 新增 ${newMessages.size} 条")

                if (isGroup) {
                    for (msg in newMessages) {
                        val groupMsg = GroupMessage(
                            groupId = targetId,
                            senderName = "",
                            text = msg.message,
                            senderId = msg.senderId,
                            timestamp = msg.timestamp,
                            messageId = msg.messageId,
                            seqId = msg.seqId,
                            conversationId = conversationId
                        )
                        chatState.addGroupMessage(groupMsg)
                    }
                } else {
                    for (msg in newMessages) {
                        chatState.addMessage(msg)
                    }
                }

                if (result.nextSeqId > lastSeqId) {
                    chatState.updateConversationSeqId(conversationId, result.nextSeqId)
                    lastSeqId = result.nextSeqId
                }

                hasMore = result.hasMore && result.messages.size >= 50
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                println("[SyncService] 同步会话 $conversationId 失败: ${e.message}")
                break
            }
        }
    }

    private suspend fun syncGroupConversations() {
        try {
            val groups = chatRepository.fetchGroups()
            println("[SyncService] 开始同步 ${groups.size} 个群聊")
            for (group in groups) {
                syncConversation(0, group.id, isGroup = true)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            println("[SyncService] 获取群聊列表失败: ${e.message}")
        }
    }

    private fun persistSeqIds() {
        try {
            LocalChatHistoryStore.saveConversationSeqIds(chatState.conversationSeqIds.value)
            println("[SyncService] seqId 游标已持久化到本地存储")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            println("[SyncService] 持久化 seqId 游标失败: ${e.message}")
        }
    }

    private fun saveChatHistoryToLocal() {
        try {
            val userId = GlobalAppState.currentUserId ?: return
            val privateMessages = chatState.messages.value
            val groupMessages = chatState.groupMessages.value
            LocalChatHistoryStore.save(userId.toString(), privateMessages, groupMessages)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            println("[SyncService] 保存聊天记录失败: ${e.message}")
        }
    }

    private fun loadConversationSeqIds() {
        try {
            val savedSeqIds = LocalChatHistoryStore.restoreConversationSeqIds()
            for ((convId, seqId) in savedSeqIds) {
                chatState.updateConversationSeqId(convId, seqId)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
    }

    private fun saveConversationSeqIds() {
        try {
            val seqIds = chatState.conversationSeqIds.value
            if (seqIds.isEmpty()) return
            LocalChatHistoryStore.saveConversationSeqIds(seqIds)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
    }
}
