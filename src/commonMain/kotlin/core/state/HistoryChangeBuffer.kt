package core.state

import kotlinx.coroutines.flow.*
import model.GroupMessage
import model.Message

interface ChatHistoryChanges {
    fun queueHistoryChanges(privateRows: List<Message>, groupRows: List<GroupMessage>)

    fun drainHistoryChanges(): ChatState.HistoryChanges
}

internal class HistoryChangeBuffer : ChatHistoryChanges {
    val dirtyLock = Any()

    val dirtyPrivate = linkedMapOf<String, Message>()

    val dirtyGroups = linkedMapOf<String, GroupMessage>()

    override fun queueHistoryChanges(privateRows: List<Message>, groupRows: List<GroupMessage>) =
        synchronized(dirtyLock) {
            privateRows.forEach { dirtyPrivate[it.messageId] = it }
            groupRows.forEach { dirtyGroups[it.messageId] = it }
        }

    override fun drainHistoryChanges() =
        synchronized(dirtyLock) {
            ChatState.HistoryChanges(dirtyPrivate.values.toList(), dirtyGroups.values.toList())
                .also {
                    dirtyPrivate.clear()
                    dirtyGroups.clear()
                }
        }
}
