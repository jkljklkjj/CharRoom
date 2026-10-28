package core

import core.state.ChatState
import kotlinx.coroutines.runBlocking
import model.*
import kotlin.test.*

class IncrementalChatStateTest {
    @Test fun deletingLatestMessageRefreshesTheSummaryWithoutResavingDeletedText()=runBlocking {
        val state=ChatState()
        state.addMessage(Message(42,"before",receiverId=100,timestamp=1,messageId="one"))
        state.addMessage(Message(42,"latest",receiverId=100,timestamp=2,messageId="two"))
        state.deleteMessage("two")
        assertEquals("before",state.conversationStates.value[ConversationKey.privateChat(42)]?.previewText)
        assertEquals(listOf("one"),state.drainHistoryChanges().privateRows.map { it.messageId })
    }
    @Test fun agentSnapshotReplacesCumulativeTextAndAckCanFindProtectedBucketOutsideMirror() = runBlocking {
        val state=ChatState()
        state.applyAgentSnapshot(100,AgentSnapshot("agent","A",false))
        state.applyAgentSnapshot(100,AgentSnapshot("agent","AB",true))
        assertEquals("AB",state.messagesFor(0).value.single().message)
        state.selectChatTarget(User(42,"Selected"))
        state.addMessage(Message(100,"outgoing",sender=true,receiverId=42,timestamp=1,messageId="local"))
        repeat(250){state.addMessage(Message(99,"other",receiverId=100,timestamp=10L+it,messageId="other:$it"))}
        state.confirmMessageId("local","server",1,"user:100:42")
        assertEquals("server",state.messagesFor(42).value.single().messageId)
    }
    @Test fun globalBucketBudgetEvictsDataWithoutReplacingFlowAndKeepsSelectedRoom() = runBlocking {
        val state=ChatState()
        val old=state.messagesFor(2)
        state.selectChatTarget(User(1,"Selected"))
        repeat(100){peer ->
            repeat(60){ index -> state.addMessage(Message(peer+1,"body",receiverId=1000,timestamp=index.toLong(),messageId="$peer:$index")) }
            state.drainHistoryChanges()
        }
        assertSame(old,state.messagesFor(2));assertTrue(old.value.isEmpty())
        assertEquals(60,state.messagesFor(1).value.size)
        assertTrue((1..100).sumOf { state.messagesFor(it).value.size }<=4000)
    }
    @Test fun dirtyRowsContainOnlyChangesAndAreReleasedAfterDrain() = runBlocking {
        val state=ChatState()
        repeat(50) { state.addMessage(Message(42,"message $it",receiverId=100,timestamp=it.toLong(),messageId="$it")) }
        assertEquals(50,state.drainHistoryChanges().privateRows.size)
        assertTrue(state.drainHistoryChanges().privateRows.isEmpty())
        state.updateMessage(state.messages.value.last().copy(message="changed"))
        assertEquals(listOf("49"),state.drainHistoryChanges().privateRows.map { it.messageId })
        assertTrue(state.drainHistoryChanges().privateRows.isEmpty())
    }
    @Test fun previewSurvivesMessageEvictionAndGroupIsIndependent() = runBlocking {
        val state=ChatState()
        state.addMessage(Message(42,"last private",receiverId=100,timestamp=1,messageId="p"))
        state.addGroupMessage(GroupMessage(42,"Author","last group",43,2,messageId="g"))
        assertEquals("last private",state.conversationStates.value[ConversationKey.privateChat(42)]?.previewText)
        assertEquals("last group",state.conversationStates.value[ConversationKey.group(42)]?.previewText)
        repeat(250) { state.addMessage(Message(99,"other",receiverId=100,timestamp=10L+it,messageId="other-$it")) }
        assertEquals("last private",state.conversationStates.value[ConversationKey.privateChat(42)]?.previewText)
    }
}
