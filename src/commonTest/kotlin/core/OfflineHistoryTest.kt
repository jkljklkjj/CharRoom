package core

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class OfflineHistoryTest {
    private fun row(id: String) = RecentHistoryMessage(id, "1", "42", "fixture", 1, "user:1:42", id)
    @Test fun pageIsAcknowledgedOnlyAfterDurableCommit() = runBlocking {
        val events = mutableListOf<String>()
        var calls = 0
        assertEquals(1, drainOfflineHistory(42, { true }, { if (calls++ == 0) listOf(row("101")) else emptyList() },
            { events.add("commit") }, { events.add("ack:${it.single()}") }))
        assertEquals(listOf("commit", "ack:101"), events)
    }
    @Test fun failedWritesAndAccountChangesNeverAcknowledge() = runBlocking {
        var confirmations = 0
        assertFails { drainOfflineHistory(42, { true }, { listOf(row("101")) }, { error("disk") }, { confirmations++ }) }
        var current = true
        drainOfflineHistory(42, { current }, { listOf(row("101")) }, { current = false }, { confirmations++ })
        assertEquals(0, confirmations)
    }
    @Test fun repeatedUnconfirmedPagesCannotSpinForever() = runBlocking {
        assertFails { drainOfflineHistory(42, { true }, { listOf(row("101")) }, {}, {}) }
        Unit
    }
}
