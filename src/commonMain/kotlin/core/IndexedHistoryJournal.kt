package core

import core.HistoryJournalFormat.Prepared
import java.io.*
import java.util.PriorityQueue
import java.util.TreeMap

/** Incremental durable records; the index holds IDs/offsets, never decoded message bodies. */
class IndexedHistoryJournal
internal constructor(
    private val file: File,
    private val syncFile: (FileDescriptor) -> Unit = { it.sync() },
) {
    data class Row(val id: String, val timestamp: Long, val body: String?)

    data class Page(val rows: List<Row>, val before: HistoryCursor?, val hasMore: Boolean)

    private data class Key(val timestamp: Long, val id: String) : Comparable<Key> {
        override fun compareTo(other: Key): Int =
            compareValuesBy(this, other, Key::timestamp, Key::id)
    }

    private data class Ref(val key: Key, val offset: Long, val length: Int, val crc: Int) {
        val estimatedBytes
            get() = length.toLong() + key.id.length * 3L + 32

        val indexBytes
            get() = 160L + key.id.length * 2L
    }

    private data class Candidate(val journal: IndexedHistoryJournal, val ref: Ref, val source: Int)

    private val format = HistoryJournalFormat()
    private val ordered = TreeMap<Key, Ref>()
    private val ids = mutableMapOf<String, Ref>()
    private var liveBytes = 0L
    @Volatile private var indexWeight = 0L
    private var requiresSync = false
    private var indexedLength = -1L
    private var validEnd = 0L
    private val magic = 0x514C4831
    private val committed = 0x434D4954
    private val maxPayload = 16 * 1024 * 1024
    private val commits =
        DurableCommitQueue<Prepared>(
            weight = {
                64L +
                    it.row.id.length * 2L +
                    (it.bytes?.size ?: 0) +
                    (it.row.body?.length ?: 0) * 2L
            }
        ) {
            writePrepared(it)
        }

    companion object {
        private const val MAX_CACHED_JOURNALS = 64
        private val cache =
            WeightedJournalCache<String, IndexedHistoryJournal>(
                MAX_CACHED_JOURNALS,
                64L * 1024 * 1024,
            ) {
                it.indexWeight
            }

        private fun trimCache() = cache.trim()

        private val active =
            mutableMapOf<String, java.lang.ref.WeakReference<IndexedHistoryJournal>>()

        @Synchronized
        fun open(file: File): IndexedHistoryJournal {
            if (active.size > 128) active.entries.removeIf { it.value.get() == null }
            return cache.getOrPut(file.absolutePath) {
                active[file.absolutePath]?.get()
                    ?: IndexedHistoryJournal(file).also {
                        active[file.absolutePath] = java.lang.ref.WeakReference(it)
                    }
            }
        }

        fun conversation(directory: File, target: String, group: Boolean): File {
            require(target.toLongOrNull() != null) { "Invalid history target" }
            return File(
                File(directory, "indexed"),
                (if (group) "group_" else "private_") + target + ".history",
            )
        }

        fun page(directory: File, group: Boolean, page: Int, size: Int): List<Row> {
            require(page >= 0 && size in 1..10000)
            val skip = Math.multiplyExact(page, size)
            val through = Math.addExact(skip, size)
            val files =
                File(directory, "indexed").listFiles { f ->
                    f.name.startsWith(if (group) "group_" else "private_") &&
                        f.extension == "history"
                } ?: emptyArray()
            // A merge holds one live index per source. Bound that cost for accounts with many
            // conversations, and keep the small top-k path for shallow pages.
            val selected =
                if (skip >= size && files.size <= MAX_CACHED_JOURNALS) {
                    mergeReferences(files, skip, size, through)
                } else topReferences(files, skip, through)
            // Resolve IDs again under each journal's lock: another writer may have compacted it.
            val rows =
                selected
                    .groupBy { it.journal }
                    .mapValues { (journal, refs) ->
                        journal.readMany(refs.map { it.ref.key.id }).associateBy { it.id }
                    }
            return selected.mapNotNull { rows[it.journal]?.get(it.ref.key.id) }
        }

        private fun topReferences(files: Array<File>, skip: Int, through: Int): List<Candidate> {
            val order = compareBy<Candidate> { it.ref.key }.thenByDescending { it.source }
            val candidates = PriorityQueue(order)
            files.forEachIndexed { source, file ->
                val journal = open(file)
                journal.visitReferences(through) { ref ->
                    val candidate = Candidate(journal, ref, source)
                    if (candidates.size < through) candidates.add(candidate)
                    else if (order.compare(candidate, candidates.peek()) > 0) {
                        candidates.poll()
                        candidates.add(candidate)
                    } else return@visitReferences false
                    true
                }
            }
            return candidates.sortedWith(order.reversed()).drop(skip)
        }

        private fun mergeReferences(
            files: Array<File>,
            skip: Int,
            size: Int,
            through: Int,
        ): List<Candidate> {
            // Each journal is already ordered: keep only its next candidate in the frontier.
            // Equal keys retain file order, matching the stable ordering of the old merge.
            val order = compareBy<Candidate> { it.ref.key }.thenByDescending { it.source }
            val frontier = PriorityQueue(order.reversed())
            files.forEachIndexed { source, file ->
                val journal = open(file)
                journal.nextReference(null)?.let { frontier.add(Candidate(journal, it, source)) }
            }
            val selected = ArrayList<Candidate>(size)
            var visited = 0
            while (visited < through && frontier.isNotEmpty()) {
                val candidate = frontier.remove()
                if (visited >= skip) selected.add(candidate)
                visited++
                candidate.journal.nextReference(candidate.ref.key)?.let { next ->
                    frontier.add(Candidate(candidate.journal, next, candidate.source))
                }
            }
            return selected
        }
    }

    @Synchronized
    private fun visitReferences(limit: Int, visit: (Ref) -> Boolean) {
        loadIndex()
        for (ref in ordered.descendingMap().values.asSequence().take(limit)) if (!visit(ref)) break
    }

    @Synchronized
    private fun nextReference(before: Key?): Ref? {
        // Prime each index once. Same-process writes update this tree under the same lock;
        // do not issue a filesystem stat for every skipped reference of a deep page.
        if (before == null) loadIndex()
        return if (before == null) ordered.lastEntry()?.value else ordered.lowerEntry(before)?.value
    }

    /** One file handle for all selected payloads; missing IDs do not cause disk reads. */
    @Synchronized
    fun readMany(rowIds: Collection<String>): List<Row> {
        loadIndex()
        return payloads(rowIds.distinct().asSequence().mapNotNull { ids[it] })
    }

    @Synchronized
    fun size(): Int {
        loadIndex()
        return ids.size
    }

    @Synchronized fun read(id: String): Row? = readMany(listOf(id)).firstOrNull()

    @Synchronized
    fun page(skip: Int, limit: Int): List<Row> {
        require(skip >= 0 && limit > 0)
        loadIndex()
        return payloads(ordered.descendingMap().values.asSequence().drop(skip).take(limit))
    }

    /** Exclusive tuple seek: a deleted boundary still selects the same older interval. */
    @Synchronized
    fun pageBefore(before: HistoryCursor?, limit: Int): Page {
        require(limit in 1..10000)
        loadIndex()
        val view =
            if (before == null) ordered
            else ordered.headMap(Key(before.timestamp, before.id), false)
        val selected = view.descendingMap().values.asSequence().take(limit + 1).toList()
        val page = selected.take(limit)
        val cursor = page.lastOrNull()?.key?.let { HistoryCursor(it.timestamp, it.id) }
        return Page(payloads(page.asSequence()), cursor, selected.size > limit)
    }

    @Synchronized
    fun range(from: Long, through: Long): List<Row> {
        if (from > through) return emptyList()
        loadIndex()
        // Seek the lower bound in the tree; only visit matching references.
        return payloads(
            ordered.tailMap(Key(from, ""), true).values.asSequence().takeWhile {
                it.key.timestamp <= through
            }
        )
    }

    @Synchronized
    fun delete() {
        if (file.exists() && !file.delete()) throw IOException("History deletion failed")
        reset()
    }

    private fun reset() {
        ids.clear()
        ordered.clear()
        liveBytes = 0
        indexWeight = 0
        indexedLength = -1
        validEnd = 0
    }

    private fun removeRef(id: String) {
        ids.remove(id)?.let {
            ordered.remove(it.key)
            liveBytes -= it.estimatedBytes
            indexWeight -= it.indexBytes
        }
    }

    private fun putRef(ref: Ref) {
        ids[ref.key.id] = ref
        ordered[ref.key] = ref
        liveBytes += ref.estimatedBytes
        indexWeight += ref.indexBytes
    }

    fun write(rows: List<Row>) {
        if (rows.isEmpty()) return
        // Invalid input must fail before modifying the file, including earlier valid rows.
        val prepared = linkedMapOf<String, Prepared>()
        for (row in rows) {
            require(row.id.isNotBlank() && row.id.length <= 256)
            val bytes = row.body?.toByteArray(Charsets.UTF_8)
            require(bytes == null || bytes.size <= maxPayload)
            prepared[row.id] = Prepared(row, bytes)
        }
        commits.commit(prepared.values.toList())
    }

    @Synchronized
    private fun writePrepared(batch: List<Prepared>) {
        loadIndex()
        val changes = selectChanges(batch)
        val unchanged = changes.isEmpty() && indexedLength == validEnd
        if (unchanged && !requiresSync) return
        file.parentFile?.mkdirs()
        var succeeded = false
        try {
            val end =
                format.append(file, validEnd, changes, syncFile) { record ->
                    removeRef(record.id)
                    if (record.length >= 0)
                        putRef(
                            Ref(
                                Key(record.timestamp, record.id),
                                record.offset,
                                record.length,
                                record.crc,
                            )
                        )
                }
            requiresSync = false
            validEnd = end
            indexedLength = end
            compactIfNeeded()
            succeeded = true
        } finally {
            if (!succeeded) {
                requiresSync = true
                reset()
            }
            trimCache()
        }
    }

    private fun selectChanges(batch: List<Prepared>): List<Prepared> {
        val prepared = linkedMapOf<String, Prepared>()
        batch.forEach { prepared[it.row.id] = it }
        val needsRead =
            prepared.values.any { value ->
                value.bytes != null && ids[value.row.id]?.key?.timestamp == value.row.timestamp
            }
        fun changed(value: Prepared, input: RandomAccessFile?): Boolean {
            val previous = ids[value.row.id]
            return format.changed(value, previous?.key?.timestamp) {
                payload(input!!, previous!!).body
            }
        }
        // Comparisons share one read handle; unchanged batches never open a write handle.
        return if (needsRead)
            RandomAccessFile(file, "r").use { input ->
                prepared.values.filter { changed(it, input) }
            }
        else prepared.values.filter { changed(it, null) }
    }

    private fun loadIndex() {
        val length = if (file.exists()) file.length() else 0
        if (indexedLength == length) return
        reset()
        if (length < 4) {
            indexedLength = length
            trimCache()
            return
        }
        RandomAccessFile(file, "r").use { input ->
            require(input.readInt() == magic) { "Unsupported history journal" }
            validEnd = input.filePointer
            while (input.filePointer < length) {
                val record = format.read(input, length) ?: break
                removeRef(record.id)
                if (record.length >= 0)
                    putRef(
                        Ref(
                            Key(record.timestamp, record.id),
                            record.offset,
                            record.length,
                            record.crc,
                        )
                    )
                validEnd = record.end
            }
        }
        indexedLength = length
        trimCache()
    }

    private fun payloads(refs: Sequence<Ref>): List<Row> {
        val iterator = refs.iterator()
        if (!iterator.hasNext()) return emptyList()
        return RandomAccessFile(file, "r").use { input ->
            buildList { while (iterator.hasNext()) add(payload(input, iterator.next())) }
        }
    }

    private fun payload(input: RandomAccessFile, ref: Ref): Row =
        format.payload(input, ref.key.id, ref.key.timestamp, ref.offset, ref.length, ref.crc)

    private fun compactIfNeeded() {
        if (validEnd < 1024 * 1024 || validEnd <= liveBytes * 4) return
        AtomicHistoryFile.write(file) { stream ->
            val output = DataOutputStream(stream)
            output.writeInt(magic)
            RandomAccessFile(file, "r").use { input ->
                for (ref in ordered.values) {
                    val bytes = payload(input, ref).body!!.toByteArray(Charsets.UTF_8)
                    output.writeUTF(ref.key.id)
                    output.writeLong(ref.key.timestamp)
                    output.writeInt(bytes.size)
                    output.write(bytes)
                    output.writeInt(ref.crc)
                    output.writeInt(committed)
                }
            }
            output.flush()
        }
        reset()
        loadIndex()
    }
}
