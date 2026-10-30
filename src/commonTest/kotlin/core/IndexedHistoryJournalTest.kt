package core

import java.nio.file.Files
import java.io.RandomAccessFile
import kotlin.test.*

class IndexedHistoryJournalTest {
    @Test fun retryAfterFailedSyncMustSyncEvenWhenTheAppendedBodyIsUnchanged() {
        val dir = Files.createTempDirectory("history-sync-retry").toFile()
        try {
            val file = java.io.File(dir, "fixture.history")
            var fail = true
            var syncs = 0
            val journal = IndexedHistoryJournal(file) { descriptor ->
                syncs++
                if (fail) throw java.io.IOException("fixture sync failure")
                descriptor.sync()
            }
            val row = IndexedHistoryJournal.Row("id", 1, "body")
            assertFailsWith<java.io.IOException> { journal.write(listOf(row)) }
            fail = false
            journal.write(listOf(row))
            assertEquals(2, syncs)
            assertEquals(row, IndexedHistoryJournal(file).read("id"))
            journal.write(listOf(row))
            assertEquals(2, syncs)
        } finally { dir.deleteRecursively() }
    }

    @Test fun manyConversationsKeepTheBoundedSelectionPathAndDeepPageOrder() {
        val dir = Files.createTempDirectory("history-many-sources").toFile()
        try {
            val all = (1..80).flatMap { target ->
                val rows = (1..8).map { n -> IndexedHistoryJournal.Row("$target-$n", n.toLong(), "body") }
                IndexedHistoryJournal.open(IndexedHistoryJournal.conversation(dir, "$target", false)).write(rows)
                rows
            }.sortedWith(compareByDescending<IndexedHistoryJournal.Row> { it.timestamp }.thenByDescending { it.id })
            for (page in listOf(0, 1, 10, 25)) {
                assertEquals(all.drop(page * 25).take(25), IndexedHistoryJournal.page(dir, false, page, 25))
            }
        } finally { dir.deleteRecursively() }
    }

    @Test fun globalDeepPagesMatchFullSortWithEmptyConversationsAndDuplicateKeys() {
        val dir = Files.createTempDirectory("history-merge-frontier").toFile()
        try {
            val files = (1..18).map { target ->
                val file = IndexedHistoryJournal.conversation(dir, "$target", false)
                val rows = if (target == 18) emptyList() else (1..180).map { n ->
                    IndexedHistoryJournal.Row("id-$n", n / 3L, "$target-$n")
                }
                // Empty file is a valid cold journal too.
                file.parentFile.mkdirs(); if (rows.isEmpty()) file.createNewFile()
                else IndexedHistoryJournal.open(file).write(rows)
                file
            }
            // Expected stable ties use the filesystem source order, as the existing API does.
            val sourceOrder = java.io.File(dir, "indexed").listFiles()!!.toList()
            val expected = sourceOrder.flatMap { file -> IndexedHistoryJournal.open(file).page(0, 1000) }
                .sortedWith(compareByDescending<IndexedHistoryJournal.Row> { it.timestamp }.thenByDescending { it.id })
            for (page in listOf(0, 1, 25, 60, 100)) {
                assertEquals(expected.drop(page * 43).take(43), IndexedHistoryJournal.page(dir, false, page, 43))
            }
            assertTrue(IndexedHistoryJournal.page(dir, true, 0, 43).isEmpty())
            assertEquals(18, files.size)
        } finally { dir.deleteRecursively() }
    }

    @Test fun unchangedBatchesPerformNoAdditionalDiskSyncAndChangedBatchesSyncOnce() {
        val dir = Files.createTempDirectory("history-sync-budget").toFile()
        try {
            var syncs = 0
            val journal = IndexedHistoryJournal(java.io.File(dir, "fixture.history")) { descriptor ->
                syncs++; descriptor.sync()
            }
            val rows = (1..100).map { IndexedHistoryJournal.Row("$it", it.toLong(), "body-$it") }
            journal.write(rows)
            assertEquals(1, syncs)
            repeat(10) { journal.write(rows) }
            assertEquals(1, syncs)
            journal.write(listOf(rows.first().copy(body = "changed"), rows.last().copy(body = "changed")))
            assertEquals(2, syncs)
            assertEquals("changed", journal.read("1")?.body)
        } finally { dir.deleteRecursively() }
    }

    @Test fun noopWriteRepairsAnIncompleteTailAndRepeatedIdsKeepTheFinalValue() {
        val dir = Files.createTempDirectory("history-noop-tail").toFile()
        try {
            val file = java.io.File(dir, "fixture.history")
            val journal = IndexedHistoryJournal(file)
            val row = IndexedHistoryJournal.Row("id", 1, "body")
            journal.write(listOf(row))
            val committedLength = file.length()
            RandomAccessFile(file, "rw").use { it.seek(it.length()); it.writeUTF("partial") }
            journal.write(listOf(row))
            assertEquals(committedLength, file.length())
            journal.write(listOf(row.copy(body = "intermediate"), row.copy(body = "final")))
            assertEquals("final", IndexedHistoryJournal(file).read("id")?.body)
        } finally { dir.deleteRecursively() }
    }

    @Test fun invalidLaterRowDoesNotLeaveEarlierRowsOnDisk() {
        val dir = Files.createTempDirectory("history-invalid-batch").toFile()
        try {
            val file = java.io.File(dir, "fixture.history")
            val journal = IndexedHistoryJournal(file)
            journal.write(listOf(IndexedHistoryJournal.Row("original", 1, "body")))
            val before = file.readBytes()
            assertFailsWith<IllegalArgumentException> {
                journal.write(listOf(IndexedHistoryJournal.Row("new", 2, "must not survive"),
                    IndexedHistoryJournal.Row("", 3, "invalid")))
            }
            assertContentEquals(before, file.readBytes())
            assertEquals(listOf("original"), IndexedHistoryJournal(file).page(0, 10).map { it.id })
        } finally { dir.deleteRecursively() }
    }

    @Test fun deletingAMissingRowDoesNotCreateAFileAndNoopBatchesKeepBytesUnchanged() {
        val dir = Files.createTempDirectory("history-noop").toFile()
        try {
            val file = java.io.File(dir, "fixture.history")
            val journal = IndexedHistoryJournal(file)
            journal.write(listOf(IndexedHistoryJournal.Row("missing", 0, null)))
            assertFalse(file.exists())
            val row = IndexedHistoryJournal.Row("id", 1, "body")
            journal.write(listOf(row))
            val before = file.readBytes()
            journal.write(listOf(row, IndexedHistoryJournal.Row("missing", 0, null)))
            assertContentEquals(before, file.readBytes())
        } finally { dir.deleteRecursively() }
    }

    @Test fun batchLookupReadsOnlyRequestedIdsAndKeepsTheirOrderAfterReopening() {
        val dir = Files.createTempDirectory("history-batch-read").toFile()
        try {
            val file = java.io.File(dir, "fixture.history")
            IndexedHistoryJournal.open(file).write(listOf(
                IndexedHistoryJournal.Row("unrelated", 1, "body"),
                IndexedHistoryJournal.Row("a", 2, "first"),
                IndexedHistoryJournal.Row("b", 3, "second")))
            RandomAccessFile(file, "rw").use { raw ->
                raw.readInt(); raw.readUTF(); raw.readLong(); raw.readInt(); raw.writeByte('X'.code)
            }
            val reopened = IndexedHistoryJournal(file)
            assertEquals(listOf("b", "a"), reopened.readMany(listOf("b", "missing", "a", "b")).map { it.id })
            assertTrue(reopened.readMany(listOf("missing")).isEmpty())
        } finally { dir.deleteRecursively() }
    }

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
