package presentation.viewmodel

import core.AppLogger
import core.LocalChatHistoryStore
import core.state.GlobalAppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import model.GroupMessage
import model.Message

suspend fun ChatViewModel.loadOlderMessages(
    userId: Int,
    before: core.HistoryCursor?,
): core.LocalHistoryPage<Message>? {
    val owner = GlobalAppState.currentUserId?.toString() ?: return null
    val visible = chatState.messagesFor(userId).value
    val page =
        olderPage(visible, before, { it.timestamp }, { it.messageId }) {
            LocalChatHistoryStore.getPrivateMessagesBefore(owner, userId, it)
        }
    return if (GlobalAppState.currentUserId?.toString() == owner) {
        chatState.prependMessages(
            page.messages,
            isCurrentAccount = { GlobalAppState.currentUserId?.toString() == owner },
        )
        page
    } else null
}

suspend fun ChatViewModel.loadOlderGroupMessages(
    groupId: Int,
    before: core.HistoryCursor?,
): core.LocalHistoryPage<GroupMessage>? {
    val owner = GlobalAppState.currentUserId?.toString() ?: return null
    val visible = chatState.groupMessagesFor(groupId).value
    val page =
        olderPage(visible, before, { it.timestamp }, { it.messageId }) {
            LocalChatHistoryStore.getGroupMessagesBefore(owner, groupId, it)
        }
    return if (GlobalAppState.currentUserId?.toString() == owner) {
        chatState.prependGroupMessages(
            page.messages,
            isCurrentAccount = { GlobalAppState.currentUserId?.toString() == owner },
        )
        page
    } else null
}

internal suspend fun <T> ChatViewModel.olderPage(
    visible: List<T>,
    before: core.HistoryCursor?,
    timestamp: (T) -> Long,
    id: (T) -> String,
    read: (core.HistoryCursor?) -> core.LocalHistoryPage<T>,
): core.LocalHistoryPage<T> =
    withContext(Dispatchers.IO) {
        val known = visible.mapTo(HashSet(), id)
        // The first boundary includes the whole timestamp tie: aliases may have changed visible
        // IDs. Subsequent pages use raw journal cursors, including across boundary deletion.
        var cursor =
            before ?: visible.minOfOrNull(timestamp)?.let { core.HistoryCursor(it, "\uffff") }
        var page: core.LocalHistoryPage<T>
        do {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            page = read(cursor)
            check(!page.hasMore || (page.before != null && page.before != cursor)) {
                "History cursor did not advance"
            }
            cursor = page.before
        } while (page.hasMore && page.messages.all { id(it) in known })
        page
    }

/** 将 seqId 游标持久化到加密文件（重启后增量同步不中断）。 */
internal fun ChatViewModel.saveConversationSeqIds() {
    val userId = GlobalAppState.currentUserId ?: return
    val accountId = userId.toString()
    LocalChatHistoryStore.saveConversationSeqIds(accountId, chatState.conversationSeqIds.value)
}

/** 从加密文件恢复 seqId 游标。 */
internal fun ChatViewModel.loadConversationSeqIds() {
    val userId = GlobalAppState.currentUserId ?: return
    val accountId = userId.toString()
    val restored = LocalChatHistoryStore.restoreConversationSeqIds(accountId)
    val ownerSession = sessionJob
    sessionScope.launch {
        if (GlobalAppState.currentUserId != userId || sessionJob !== ownerSession) return@launch
        restored.forEach { (conversation, seq) ->
            chatState.updateConversationSeqId(conversation, seq)
        }
    }
}

internal fun ChatViewModel.saveChatHistoryToLocal() {
    sessionScope.launch {
        val owner = GlobalAppState.currentUserId?.toString() ?: return@launch
        if (historyOwner != owner) {
            historyWriter?.close()
            historyWriter = null
            historyOwner = owner
        }
        if (historyWriter == null) {
            historyWriter =
                core.HistoryWriteQueue(
                    sessionScope,
                    SAVE_HISTORY_DEBOUNCE_MS,
                    merge = { older, newer ->
                        check(older.owner == newer.owner)
                        older.apply {
                            privateRows.putAll(newer.privateRows)
                            groupRows.putAll(newer.groupRows)
                        }
                    },
                    failed = { error ->
                        logger.error(error) { "Local history persistence failed" }
                    },
                    persist = { batch ->
                        LocalChatHistoryStore.save(
                            batch.owner,
                            batch.privateRows.values.toList(),
                            batch.groupRows.values.toList(),
                        )
                    },
                )
        }
        val changed = chatState.drainHistoryChanges()
        if (changed.privateRows.isNotEmpty() || changed.groupRows.isNotEmpty())
            historyWriter!!.request(
                ChatViewModel.HistoryBatch(
                    owner,
                    changed.privateRows.associateBy { it.messageId }.toMutableMap(),
                    changed.groupRows.associateBy { it.messageId }.toMutableMap(),
                )
            )
    }
}

suspend fun ChatViewModel.flushLocalHistory() {
    val writer =
        withContext(Dispatchers.Main.immediate) {
            saveChatHistoryToLocal()
            historyWriter
        }
    writer?.flush()
}

internal suspend fun ChatViewModel.applyRecentPage(
    owner: Int,
    token: String,
    rows: List<core.RecentHistoryMessage>,
    isCurrent: () -> Boolean,
) {
    if (!isCurrent()) return
    val privateMessages = rows.filterNot { it.isGroup }.map { it.toPrivateMessage(owner) }
    val groupMessages = rows.filter { it.isGroup }.map { it.toGroupMessage() }
    LocalChatHistoryStore.save(owner.toString(), privateMessages, groupMessages)
    if (isCurrent())
        core.confirmStoredMessages(owner, rows) { core.confirmOfflineMessages(token, it) }
    if (isCurrent())
        chatState.prependMessages(
            privateMessages,
            markAsUnread = false,
            isCurrentAccount = isCurrent,
        )
    if (isCurrent())
        chatState.prependGroupMessages(
            groupMessages,
            markAsUnread = false,
            isCurrentAccount = isCurrent,
        )
}

internal suspend fun ChatViewModel.restoreOfflinePage(owner: Int, token: String): Boolean {
    val generation = sessionJob
    fun current() =
        sessionJob === generation && generation.isActive && GlobalAppState.currentUserId == owner
    isFetchingOfflineMessages = true
    return try {
        core.recoverOperation(
            onFailure = { error ->
                AppLogger.w("离线消息恢复未完成，将保留服务端未确认记录")
                false
            }
        ) {
            core.drainOfflineHistory(
                owner,
                ::current,
                { core.getOfflineHistoryPage(token) },
                { rows ->
                    val messages = rows.map { it.toPrivateMessage(owner) }
                    LocalChatHistoryStore.save(owner.toString(), messages, emptyList())
                    if (current()) chatState.prependMessages(messages, markAsUnread = true)
                },
                { core.confirmOfflineMessages(token, it) },
            ) > 0
        }
    } finally {
        isFetchingOfflineMessages = false
        isLoadingMore = false
        if (current()) {
            pendingMessages.sortedBy { it.timestamp }.forEach { chatState.addMessage(it) }
            pendingGroupMessages.sortedBy { it.timestamp }.forEach { chatState.addGroupMessage(it) }
            pendingMessages.clear()
            pendingGroupMessages.clear()
        }
    }
}
