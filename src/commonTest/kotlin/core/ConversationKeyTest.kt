package core

import kotlin.test.*
import kotlinx.coroutines.runBlocking
import model.*
import core.state.ChatState

class ConversationKeyTest {
    @Test fun sameNumericIdKeepsDistinctContactAndUnreadState() = runBlocking {
        val state=ChatState()
        val friend=User(42,"Friend")
        val group=Group(42,"Group").toUiUser()
        state.mergeUsers(listOf(friend,group))
        assertEquals(2,state.users.value.size)
        assertNotEquals(friend.conversationKey,group.conversationKey)
        state.addMessage(Message(42,"private",receiverId=100,timestamp=1,messageId="p"))
        state.addGroupMessage(GroupMessage(42,"Author","group",42,1,messageId="g"))
        state.selectChatTarget(group)
        assertEquals(1,state.conversationStates.value[friend.conversationKey]?.unreadCount)
        assertEquals(0,state.conversationStates.value[group.conversationKey]?.unreadCount)
        assertEquals("g",state.groupMessagesFor(42).value.single().messageId)
        state.updateGroupMessageSentStatus("g",true)
        state.confirmMessageId("g","server-g",1,"group:42")
        assertEquals("server-g",state.groupMessagesFor(42).value.single().messageId)
        state.deleteGroupMessage("server-g")
        assertTrue(state.groupMessagesFor(42).value.isEmpty())
    }
    @Test fun groupKeysRejectNegativeIdsAndAssistantRemainsPrivate() {
        assertFailsWith<IllegalArgumentException>{ConversationKey.group(-42)}
        assertEquals("user:0",ConversationKey.privateChat(0).value)
        assertEquals("group:42",ConversationKey.group(42).value)
    }
}
