package core

import java.nio.file.Files
import java.io.RandomAccessFile
import kotlin.test.*

class IndexedHistoryJournalTest {
    @Test fun deletingMostRowsCompactsTheRemainingPayloadAndReopenedIndexCanAppend() {
        val dir = Files.createTempDirectory("history-delete-compaction").toFile()
        try {
            val file = java.io.File(dir, "fixture.history")
            val journal = IndexedHistoryJournal.open(file)
            journal.write((1..50).map { IndexedHistoryJournal.Row("$it", it.toLong(), "x".repeat(30000)) })
            assertTrue(file.length() > 1000000)
            journal.write((1..49).map { IndexedHistoryJournal.Row("$it", 0, null) })
            assertEquals(1, journal.size())
            assertTrue(file.length() < 100000)
            val reopened = IndexedHistoryJournal(file)
            reopened.write(listOf(IndexedHistoryJournal.Row("51", 51, "new")))
            assertEquals(listOf("51", "50"), reopened.page(0, 10).map { it.id })
        } finally { dir.deleteRecursively() }
    }

    @Test fun timeRangesIncludeAllBoundaryIdsAndSkipUnrelatedPayloads() {
        val dir = Files.createTempDirectory("history-range").toFile()
        try {
            val file = java.io.File(dir, "fixture.history")
            val journal = IndexedHistoryJournal.open(file)
            journal.write(listOf(
                IndexedHistoryJournal.Row("old", -1, "outside"),
                IndexedHistoryJournal.Row("a", 10, "first"),
                IndexedHistoryJournal.Row("z", 10, "second"),
                IndexedHistoryJournal.Row("last", Long.MAX_VALUE, "last")
            ))
            RandomAccessFile(file, "rw").use { raw ->
                raw.readInt(); raw.readUTF(); raw.readLong(); raw.readInt(); raw.writeByte('X'.code)
            }
            assertEquals(listOf("a", "z"), journal.range(10, 10).map { it.id })
            assertEquals(listOf("a", "z", "last"), journal.range(10, Long.MAX_VALUE).map { it.id })
            assertTrue(journal.range(11, 9).isEmpty())
            assertTrue(journal.range(11, 20).isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun globalPagesMergeConversationIndexesWithStableTiesAndSeparateGroups() {
        val dir = Files.createTempDirectory("history-global-page").toFile()
        try {
            val expected = (1..12).flatMap { target ->
                val rows = (1..30).map { n ->
                    IndexedHistoryJournal.Row("$target-$n", n.toLong(), "body-$target-$n")
                }
                IndexedHistoryJournal.open(IndexedHistoryJournal.conversation(dir, "$target", false)).write(rows)
                rows
            }.sortedWith(compareByDescending<IndexedHistoryJournal.Row> { it.timestamp }.thenByDescending { it.id })
            IndexedHistoryJournal.open(IndexedHistoryJournal.conversation(dir, "1", true))
                .write(listOf(IndexedHistoryJournal.Row("group", 1000, "group")))
            for (page in listOf(0, 1, 8, 9, 10)) {
                assertEquals(expected.drop(page * 40).take(40), IndexedHistoryJournal.page(dir, false, page, 40))
            }
            assertEquals("group", IndexedHistoryJournal.page(dir, true, 0, 40).single().id)
            assertFailsWith<IllegalArgumentException> { IndexedHistoryJournal.page(dir, false, -1, 40) }
        } finally { dir.deleteRecursively() }
    }

    @Test fun oldPayloadsAreNotDecodedWhileReadingTheNewestPage() {
        val dir = Files.createTempDirectory("history-page-io").toFile()
        try {
            val file = java.io.File(dir, "fixture.history")
            val journal = IndexedHistoryJournal.open(file)
            journal.write(listOf(IndexedHistoryJournal.Row("old", 1, "older"), IndexedHistoryJournal.Row("new", 2, "newer")))
            RandomAccessFile(file, "rw").use { raw ->
                raw.readInt(); raw.readUTF(); raw.readLong(); raw.readInt()
                raw.writeByte('X'.code)
            }
            assertEquals("newer", journal.page(0, 1).single().body)
            assertFailsWith<IllegalStateException> { journal.read("old") }
        } finally { dir.deleteRecursively() }
    }
    @Test fun supersededRecordsAreCompactedAndAnActiveJournalSurvivesCacheEviction() {
        val dir = Files.createTempDirectory("history-compaction").toFile()
        try {
            val file = java.io.File(dir, "fixture.history")
            val journal = IndexedHistoryJournal.open(file)
            repeat(80) { journal.write(listOf(IndexedHistoryJournal.Row("id", 1, "version-$it" + "x".repeat(30000)))) }
            assertEquals(1, journal.size())
            assertTrue(file.length() < 1300000)
            repeat(100) { IndexedHistoryJournal.open(java.io.File(dir, "other-$it.history")) }
            assertSame(journal, IndexedHistoryJournal.open(file))
        } finally { dir.deleteRecursively() }
    }

    @Test fun pagesDecodeOnlySelectedPayloadsAndUpsertsDoNotDuplicateIds() {
        val dir = Files.createTempDirectory("indexed-history").toFile()
        try {
            val journal = IndexedHistoryJournal.open(java.io.File(dir, "fixture.history"))
            journal.write((1..2000).map { IndexedHistoryJournal.Row(it.toString(), it.toLong(), "payload-$it") })
            assertEquals(listOf("payload-2000", "payload-1999"), journal.page(0, 2).map { it.body })
            journal.write(listOf(IndexedHistoryJournal.Row("2000", 2000, "updated")))
            assertEquals(2000, journal.size())
            assertEquals("updated", journal.read("2000")?.body)
            journal.write(listOf(IndexedHistoryJournal.Row("2000", 0, null)))
            assertEquals("1999", journal.page(0, 1).single().id)
        } finally { dir.deleteRecursively() }
    }
    @Test fun incompleteTailDoesNotHideCommittedRecordsOrPoisonTheNextAppend() {
        val dir = Files.createTempDirectory("history-tail").toFile()
        try {
            val file = java.io.File(dir, "fixture.history")
            IndexedHistoryJournal.open(file).write(listOf(IndexedHistoryJournal.Row("1", 1, "committed")))
            RandomAccessFile(file, "rw").use { it.seek(it.length()); it.writeUTF("partial") }
            val journal = IndexedHistoryJournal(file)
            assertEquals("committed", journal.read("1")?.body)
            journal.write(listOf(IndexedHistoryJournal.Row("2", 2, "next")))
            assertEquals(2, IndexedHistoryJournal(file).size())
        } finally { dir.deleteRecursively() }
    }
}
