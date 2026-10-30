package core.state

import kotlinx.coroutines.flow.*
import model.ConversationKey
import model.GroupMessage
import model.Message

internal class MessageBuckets(
    private val previews: ConversationPreviews,
    private val history: HistoryChangeBuffer,
    private val selectedKey: () -> ConversationKey?,
) {
    // 全量私聊消息（兼容 UserListScreen 等预览场景，限制总条数）
    val messageCache =
        MessageLruCache<Message>(
            ChatState.MAX_MESSAGES,
            id = { it.messageId },
            timestamp = { it.timestamp },
        )

    val messages: StateFlow<List<Message>> = messageCache.state

    // 全量群聊消息
    val groupMessageCache =
        MessageLruCache<GroupMessage>(
            ChatState.MAX_MESSAGES,
            id = { it.messageId },
            timestamp = { it.timestamp },
        )

    val groupMessages: StateFlow<List<GroupMessage>> = groupMessageCache.state

    // 按会话分桶的私聊消息（ChatScreen 按会话 O(1) 访问，不 filter 全量）
    val privateMessageCaches =
        java.util.concurrent.ConcurrentHashMap<Int, MessageLruCache<Message>>()

    // 按群组分桶的群聊消息
    val groupMessageCaches =
        java.util.concurrent.ConcurrentHashMap<Int, MessageLruCache<GroupMessage>>()

    val cacheBudget = ConversationCacheBudget()

    val bucketLock = Any()

    fun findPrivate(predicate: (Message) -> Boolean): Message? =
        messageCache.snapshot.firstOrNull(predicate)
            ?: privateMessageCaches.values.firstNotNullOfOrNull {
                it.snapshot.firstOrNull(predicate)
            }

    fun findGroup(predicate: (GroupMessage) -> Boolean): GroupMessage? =
        groupMessageCache.snapshot.firstOrNull(predicate)
            ?: groupMessageCaches.values.firstNotNullOfOrNull {
                it.snapshot.firstOrNull(predicate)
            }

    fun putPrivate(message: Message) {
        synchronized(history.dirtyLock) { history.dirtyPrivate[message.messageId] = message }
        messageCache.add(message)
        previews.privatePreview(message)
    }

    fun putGroup(message: GroupMessage) {
        synchronized(history.dirtyLock) { history.dirtyGroups[message.messageId] = message }
        groupMessageCache.add(message)
        previews.groupPreview(message)
    }

    fun getOrCreatePrivateCache(userId: Int): MessageLruCache<Message> =
        synchronized(bucketLock) {
            val key = ConversationKey.privateChat(userId)
            val cache =
                privateMessageCaches.getOrPut(userId) {
                    val value =
                        MessageLruCache<Message>(
                            ChatState.MAX_MESSAGES,
                            { it.messageId },
                            { it.timestamp },
                            { rows ->
                                cacheBudget.changed(
                                    key,
                                    rows.size,
                                    rows.sumOf { it.message.length.toLong() },
                                )
                            },
                        )
                    cacheBudget.register(
                        key,
                        {
                            value.hasSubscribers || selectedKey() == key
                        },
                        { value.clear() },
                    )
                    value
                }
            cacheBudget.touch(key)
            cache
        }

    fun getOrCreateGroupCache(groupId: Int): MessageLruCache<GroupMessage> =
        synchronized(bucketLock) {
            val key = ConversationKey.group(groupId)
            val cache =
                groupMessageCaches.getOrPut(groupId) {
                    val value =
                        MessageLruCache<GroupMessage>(
                            ChatState.MAX_MESSAGES,
                            { it.messageId },
                            { it.timestamp },
                            { rows ->
                                cacheBudget.changed(
                                    key,
                                    rows.size,
                                    rows.sumOf { it.text.length.toLong() },
                                )
                            },
                        )
                    cacheBudget.register(
                        key,
                        {
                            value.hasSubscribers || selectedKey() == key
                        },
                        { value.clear() },
                    )
                    value
                }
            cacheBudget.touch(key)
            cache
        }
}
