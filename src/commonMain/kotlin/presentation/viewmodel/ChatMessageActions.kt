package presentation.viewmodel

import core.AppLogger
import core.Chat
import core.LocalChatHistoryStore
import core.MsgType
import core.buildChatPayload
import core.buildGroupChatPayload
import core.state.GlobalAppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import model.GroupMessage
import model.Message
import model.User

/** 前置添加私聊消息（用于加载历史消息） */
fun ChatViewModel.prependMessages(newMessages: List<Message>) {
    sessionScope.launch {
        chatState.prependMessages(newMessages)
        chatState.queueHistoryChanges(newMessages, emptyList())
        // 异步保存到本地存储
        saveChatHistoryToLocal()
    }
}

/** 前置添加群聊消息（用于加载历史消息） */
fun ChatViewModel.prependGroupMessages(newMessages: List<GroupMessage>) {
    sessionScope.launch {
        chatState.prependGroupMessages(newMessages)
        chatState.queueHistoryChanges(emptyList(), newMessages)
        // 异步保存到本地存储
        saveChatHistoryToLocal()
    }
}

/** 添加新的私聊消息 */
fun ChatViewModel.addMessage(message: Message) {
    sessionScope.launch {
        // 离线消息拉取中，先缓存到临时队列，避免顺序混乱
        if (isFetchingOfflineMessages && !message.sender) {
            pendingMessages.add(message)
            AppLogger.d(" 离线消息拉取中，私聊消息已缓存: messageId=${message.messageId}")
            return@launch
        }

        ensureSenderContact(message)

        chatState.addMessage(message)
        // 异步保存到本地存储
        saveChatHistoryToLocal()
    }
}

/** 添加新的群聊消息 */
fun ChatViewModel.addGroupMessage(message: GroupMessage) {
    sessionScope.launch {
        chatState.addGroupMessage(message)
        // 异步保存到本地存储
        saveChatHistoryToLocal()
    }
}

/** 更新私聊消息发送状态 */
internal fun ChatViewModel.updateDeliveryState(id: String, state: model.MessageDeliveryState) {
    sessionScope.launch {
        chatState.updateDeliveryState(id, state)
        saveChatHistoryToLocal()
    }
}

fun ChatViewModel.confirmMessageId(
    clientId: String,
    serverId: String,
    seq: Long,
    conversation: String,
) {
    sessionScope.launch { chatState.confirmMessageId(clientId, serverId, seq, conversation) }
}

fun ChatViewModel.updateMessageSentStatus(messageId: String, isSent: Boolean) {
    sessionScope.launch {
        chatState.updateMessageSentStatus(messageId, isSent)
        // 异步保存到本地存储
        saveChatHistoryToLocal()
    }
}

/** 重试发送失败的消息 */
fun ChatViewModel.retryMessage(message: Message) {
    if (message.deliveryState != model.MessageDeliveryState.FAILED) return
    val owner = GlobalAppState.currentUserId
    val sendingSession = sessionJob
    sessionScope.launch {
        val targetId = if (message.sender) message.receiverId else message.senderId
        val user = chatState.users.value.find { !it.isGroup && it.id == targetId }
        if (user == null) {
            logger.warn { "retryMessage: 找不到用户 $targetId" }
            return@launch
        }

        val timestamp = message.timestamp
        val messageId = message.messageId

        val payload =
            buildChatPayload(
                targetClientId = user.id.toString(),
                content = message.message,
                timestamp = timestamp,
                replyToMessageId = message.replyToMessageId,
                replyToContent = message.replyToContent,
                replyToSender = message.replyToSender,
                messageType = message.messageType.ordinal,
                attachment =
                    core.MessageAttachment(
                        fileUrl = message.fileUrl,
                        fileName = message.fileName,
                        fileSize = message.fileSize,
                    ),
                messageId = messageId,
            )

        updateDeliveryState(messageId, model.MessageDeliveryState.SENDING)
        Chat.send(
            payload = payload,
            type = MsgType.CHAT,
            targetClientId = user.id.toString(),
            expectedResponses = 1,
        ) { success, _ ->
            if (owner == GlobalAppState.currentUserId && sendingSession === sessionJob)
                sessionScope.launch {
                    updateMessageSentStatus(messageId, success)
                }
        }
    }
}

fun ChatViewModel.retryGroupMessage(message: GroupMessage) {
    if (message.deliveryState != model.MessageDeliveryState.FAILED) return
    val owner = GlobalAppState.currentUserId
    val sendingSession = sessionJob
    val payload =
        buildGroupChatPayload(
            message.groupId.toString(),
            message.text,
            replyToMessageId = message.replyToMessageId,
            replyToContent = message.replyToContent,
            replyToSender = message.replyToSender,
            messageType = message.messageType.ordinal,
            attachment =
                core.MessageAttachment(
                    fileUrl = message.fileUrl,
                    fileName = message.fileName,
                    fileSize = message.fileSize,
                ),
            messageId = message.messageId,
            timestamp = message.timestamp,
        )
    updateDeliveryState(message.messageId, model.MessageDeliveryState.SENDING)
    Chat.send(payload, MsgType.GROUP_CHAT, message.groupId.toString(), 1) { success, _ ->
        if (owner == GlobalAppState.currentUserId && sendingSession === sessionJob)
            updateGroupMessageSentStatus(message.messageId, success)
    }
}

/** 原位更新私聊消息内容（用于流式输出） */
fun ChatViewModel.updateMessage(updatedMessage: Message) {
    sessionScope.launch {
        chatState.updateMessage(updatedMessage)
        // 异步保存到本地存储
        saveChatHistoryToLocal()
    }
}

fun ChatViewModel.upsertAgentStreamMessage(
    messageId: String,
    fullContent: String,
    done: Boolean = false,
    error: Boolean = false,
) {
    if (streamSession !== sessionJob) {
        streamUpdates?.clear()
        streamSession = sessionJob
        streamUpdates =
            core.ConflatedStreamUpdates(sessionScope) { snapshot ->
                val owner = GlobalAppState.currentUserId ?: return@ConflatedStreamUpdates
                chatState.applyAgentSnapshot(owner, snapshot)
                saveChatHistoryToLocal()
            }
    }
    streamUpdates!!.submit(core.AgentSnapshot(messageId, fullContent, done || error))
}

/** 更新群聊消息发送状态 */
fun ChatViewModel.updateGroupMessageSentStatus(messageId: String, isSent: Boolean) {
    sessionScope.launch {
        chatState.updateGroupMessageSentStatus(messageId, isSent)
        // 异步保存到本地存储
        saveChatHistoryToLocal()
    }
}

/** 删除私聊消息 */
fun ChatViewModel.deleteMessage(message: Message) {
    val owner = GlobalAppState.currentUserId ?: return
    val target = if (message.senderId == owner) message.receiverId else message.senderId
    sessionScope.launch {
        flushLocalHistory()
        chatState.deleteMessage(message.messageId)
        withContext(Dispatchers.IO) {
            LocalChatHistoryStore.deleteMessage(
                owner.toString(),
                target.toString(),
                message.messageId,
                false,
            )
        }
        // 异步保存到本地存储
        saveChatHistoryToLocal()
    }
}

/** 删除群聊消息 */
fun ChatViewModel.deleteGroupMessage(message: GroupMessage) {
    val owner = GlobalAppState.currentUserId ?: return
    sessionScope.launch {
        flushLocalHistory()
        chatState.deleteGroupMessage(message.messageId)
        withContext(Dispatchers.IO) {
            LocalChatHistoryStore.deleteMessage(
                owner.toString(),
                message.groupId.toString(),
                message.messageId,
                true,
            )
        }
        // 异步保存到本地存储
        saveChatHistoryToLocal()
    }
}

private suspend fun ChatViewModel.ensureSenderContact(message: Message) {
    // 收到陌生人的第一条私聊消息时，先补进联系人列表，避免必须重启才能看到
    val incoming = !message.sender && message.senderId != (GlobalAppState.currentUserId ?: -1)
    if (incoming && message.senderId != AGENT_ASSISTANT_ID) {
        val currentUsers = chatState.users.value
        if (currentUsers.none { !it.isGroup && it.id == message.senderId }) {
            val senderUser =
                core.recoverOperation(onFailure = { null }) {
                    chatRepository.getUserDetail(message.senderId.toString())
                }
                    ?: User(
                        id = message.senderId,
                        username = "用户${message.senderId}",
                        online = true,
                    )
            chatState.upsertUser(senderUser)
        }
    }
}
