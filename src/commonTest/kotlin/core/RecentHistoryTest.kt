package core

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class RecentHistoryTest {
    @Test fun restHistoryRetainsTheClientIdentityForPrivateAndGroupRows() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val row = json.decodeFromString<RecentHistoryMessage>("""{
            "id":"9007199254740993","senderId":"1","receiverId":"2","message":"fixture",
            "timestamp":1000,"conversationId":"user:1:2","seqId":"7","clientMessageId":"local_lost"
        }""")
        assertEquals("local_lost", row.privateMessage(1).clientMessageId)
        assertEquals("local_lost", row.copy(conversationId = "group:2").groupMessage().clientMessageId)
        val legacy = json.decodeFromString<RecentHistoryMessage>("""{
            "id":"101","senderId":"1","receiverId":"2","message":"fixture","timestamp":1000
        }""")
        assertNull(legacy.privateMessage(1).clientMessageId)
    }

    private fun message(id: String, sender: String = "2", receiver: String = "1", conversation: String = "user:1:2") =
        RecentHistoryMessage(id, sender, receiver, "fixture", 1000, conversation, "9007199254740993")

    @Test fun newDeviceRestoresBothDirectionsAndGroupsAcrossSameTimestampBoundary() = runBlocking {
        val received = message("9007199254740993")
        val sent = message("9007199254740994", "1", "2")
        val group = message("9007199254740995", receiver = "group:7", conversation = "group:7")
        val requests = mutableListOf<RecentHistoryCursor>()
        val applied = mutableListOf<RecentHistoryMessage>()
        restoreRecentHistory({ true }, { cursor ->
            requests += cursor
            if (requests.size == 1) RecentHistoryPage(listOf(received, sent), 2000, 1000, sent.id, true)
            else RecentHistoryPage(listOf(group), 2000, 1000, group.id, false)
        }) { applied += it }
        assertEquals(listOf(received, sent, group), applied)
        assertEquals(sent.id, requests[1].afterId)
        assertEquals(2000L, requests[1].windowEnd)
        assertFalse(received.privateMessage(1).sender)
        assertTrue(sent.privateMessage(1).sender)
        assertEquals(7, group.groupMessage().groupId)
        assertEquals(9007199254740993L, sent.privateMessage(1).seqId)
    }

    @Test fun accountSwitchDiscardsPendingResponse() = runBlocking {
        var owner = true
        var applied = false
        restoreRecentHistory({ owner }, {
            owner = false
            RecentHistoryPage(listOf(message("1")), 2000)
        }) { applied = true }
        assertFalse(applied)
    }

    @Test fun failingPageDoesNotBecomeEmptySuccess() = runBlocking {
        var count = 0
        assertFailsWith<IllegalStateException> {
            restoreRecentHistory({ true }, {
                if (++count == 1) RecentHistoryPage(listOf(message("1")), 2000, 1000, "1", true)
                else error("offline")
            }) { }
        }
        assertEquals(2, count)
    }

    @Test fun stalledCursorStopsInsteadOfLoopingForever() = runBlocking {
        var requests = 0
        assertFailsWith<IllegalStateException> {
            restoreRecentHistory({ true }, {
                requests++
                RecentHistoryPage(listOf(message("1")), 2000, 1000, "1", true)
            }) { }
        }
        assertEquals(2, requests)
    }
    @Test fun historyInsertionDoesNotAdvanceCursorsOrUnreadAndRejectsStaleAccounts() = runBlocking {
        val state = core.state.ChatState()
        val received = message("1").privateMessage(1)
        state.prependMessages(listOf(received), markAsUnread = false, isCurrentAccount = { false })
        assertTrue(state.messages.value.isEmpty())
        state.prependMessages(listOf(received), markAsUnread = false, isCurrentAccount = { true })
        state.prependMessages(listOf(received), markAsUnread = false)
        assertEquals(1, state.messages.value.size)
        assertTrue(state.conversationSeqIds.value.isEmpty())
        assertTrue(state.conversationStates.value.values.all { it.unreadCount == 0 })
    }

}
