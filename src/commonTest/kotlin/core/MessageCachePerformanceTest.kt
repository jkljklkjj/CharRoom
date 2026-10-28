package core

import core.state.MessageLruCache
import kotlin.test.*

class MessageCachePerformanceTest {
    @Test fun groupSubscriptionUsesPublicGroupIdAndIgnoresOtherGroups() = kotlinx.coroutines.runBlocking {
        val state = core.state.ChatState()
        val selected = state.groupMessagesFor(42)
        state.addGroupMessage(model.GroupMessage(42,"Fixture","first",100,1,messageId="one"))
        assertEquals(listOf("one"),selected.value.map { it.messageId })
        val snapshot=selected.value
        state.addGroupMessage(model.GroupMessage(43,"Fixture","unrelated",100,2,messageId="two"))
        assertSame(snapshot,selected.value)
    }
    data class Row(val id: String, val time: Long, val content: String = "")
    @Test fun repeatedUpdatesUseIdIndexAndSnapshotsAreReused() {
        var reads = 0
        val cache = MessageLruCache<Row>(200, { reads++; it.id }, { it.time })
        repeat(100) { cache.add(Row("$it", it.toLong())) }
        assertSame(cache.snapshot, cache.snapshot)
        reads = 0
        cache.add(Row("90", 90, "updated"))
        assertTrue(reads < 5, "Updating an existing ID must not scan the list: $reads")
        assertEquals("updated", cache.snapshot[90].content)
        cache.remove("50");cache.add(Row("90", 90, "again"))
        assertEquals("again", cache.snapshot.first { it.id == "90" }.content)
    }
    @Test fun prependDeduplicatesAndEvictionKeepsIndexConsistent() {
        val cache = MessageLruCache<Row>(3, Row::id, Row::time)
        cache.prepend(listOf(Row("1",1),Row("1",1),Row("2",2)))
        assertEquals(listOf("1","2"), cache.snapshot.map(Row::id))
        cache.add(Row("3",3));cache.touch("1");cache.add(Row("4",4))
        assertEquals(listOf("1","3","4"), cache.snapshot.map(Row::id))
        cache.add(Row("3",3,"changed"));assertEquals(3,cache.size)
        cache.clear();cache.add(Row("3",3));assertEquals(1,cache.size)
    }
}
