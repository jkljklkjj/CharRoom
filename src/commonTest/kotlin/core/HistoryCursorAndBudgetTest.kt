package core

import java.nio.file.Files
import kotlin.test.*

class HistoryCursorAndBudgetTest {
    @Test fun cursorPagesSurviveDeletionAndNewerInsertionsWithEqualTimestampIds() {
        val root=Files.createTempDirectory("history-cursor").toFile()
        try {
            val journal=IndexedHistoryJournal(java.io.File(root,"fixture.history"))
            journal.write((1..120).map { IndexedHistoryJournal.Row("%03d".format(it),it/4L,"body") })
            val first=journal.pageBefore(null,25)
            assertEquals(25,first.rows.size); assertTrue(first.hasMore)
            val boundary=first.before!!
            journal.write(listOf(IndexedHistoryJournal.Row("newer",1000,"body"),
                IndexedHistoryJournal.Row(first.rows.first().id,0,null),IndexedHistoryJournal.Row(boundary.id,0,null)))
            val seen=first.rows.map { it.id }.toMutableList()
            var cursor:HistoryCursor?=boundary
            do {
                val page=journal.pageBefore(cursor,25)
                seen+=page.rows.map { it.id }; cursor=page.before
                if (!page.hasMore) break
            } while (true)
            assertEquals(120,seen.size)
            assertEquals(120,seen.toSet().size)
            assertFalse("newer" in seen)
        } finally { root.deleteRecursively() }
    }

    @Test fun fileStoreExposesBoundedOlderPagesWithoutPageNumbers() {
        val root=Files.createTempDirectory("history-store-cursor").toFile()
        try {
            val store=FileLocalChatHistoryStore(root)
            store.save("1",(1..125).map { model.Message(1,"fixture",true,2,it.toLong(),true,"$it") },emptyList())
            val first=store.getPrivateMessagesBefore("1",2,null,50)
            val second=store.getPrivateMessagesBefore("1",2,first.before,50)
            val last=store.getPrivateMessagesBefore("1",2,second.before,50)
            assertEquals(50,first.messages.size); assertEquals(50,second.messages.size); assertEquals(25,last.messages.size)
            assertEquals(125,(first.messages+second.messages+last.messages).map { it.messageId }.toSet().size)
            assertFalse(last.hasMore)
        } finally { root.deleteRecursively() }
    }

    @Test fun weightedCacheEvictsByRecencyAndBytesIncludingGrowingEntries() {
        data class Entry(var bytes:Long)
        val cache=WeightedJournalCache<String,Entry>(10,100) { it.bytes }
        cache.getOrPut("a") { Entry(40) }; cache.getOrPut("b") { Entry(40) }
        val a=cache.getOrPut("a") { error("must reuse") }
        cache.getOrPut("c") { Entry(40) }
        assertTrue(cache.retainedBytes()<=100)
        assertFalse(cache.contains("b")); assertTrue(cache.contains("a"))
        a.bytes=120; cache.trim()
        assertFalse(cache.contains("a")); assertTrue(cache.retainedBytes()<=100)
    }
}
