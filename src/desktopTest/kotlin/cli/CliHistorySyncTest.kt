package cli

import core.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class CliHistorySyncTest {
    @Test fun completeHistoryRetainsPrivateGroupAndSentRowsBeforeReceipts() = runBlocking {
        val incoming = RecentHistoryMessage("101", "7", "42", "incoming", 1, "user:7:42", "1")
        val group = incoming.copy(id = "102", receiverId = "7", conversationId = "group:7")
        val sent = incoming.copy(id = "103", senderId = "42", receiverId = "7")
        val rows = mutableListOf<RecentHistoryMessage>()
        val receipts = mutableListOf<String>()
        var page = 0
        syncCliHistory(42, { true }, { cursor ->
            if (page++ == 0) RecentHistoryPage(listOf(incoming), 10, 1, "101", true)
            else { assertEquals("101", cursor.afterId); RecentHistoryPage(listOf(group, sent), 10, hasMore = false) }
        }, { emptyList() }, { rows.addAll(it) }, { ids -> assertTrue(rows.isNotEmpty()); receipts.addAll(ids) })
        assertEquals(listOf(incoming, group, sent), rows)
        assertEquals(listOf("101"), receipts)
    }
}
