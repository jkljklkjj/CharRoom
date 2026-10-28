package core

import java.io.*
import java.util.TreeMap
import java.util.PriorityQueue
import java.util.zip.CRC32

/** Incremental durable records; the index holds IDs/offsets, never decoded message bodies. */
class IndexedHistoryJournal(private val file: File) {
    data class Row(val id: String, val timestamp: Long, val body: String?)
    private data class Key(val timestamp: Long, val id: String) : Comparable<Key> {
        override fun compareTo(other: Key): Int = compareValuesBy(this, other, Key::timestamp, Key::id)
    }
    private data class Ref(val key: Key, val offset: Long, val length: Int, val crc: Int)
    private data class Candidate(val journal: IndexedHistoryJournal, val ref: Ref, val source: Int)
    private val ordered = TreeMap<Key, Ref>()
    private val ids = mutableMapOf<String, Ref>()
    private var liveBytes = 0L
    private var indexedLength = -1L
    private var validEnd = 0L
    private val magic = 0x514C4831
    private val committed = 0x434D4954
    private val maxPayload = 16 * 1024 * 1024

    companion object {
        private val cache = object : LinkedHashMap<String, IndexedHistoryJournal>(16, .75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, IndexedHistoryJournal>?) = size > 64
        }
        private val active = mutableMapOf<String, java.lang.ref.WeakReference<IndexedHistoryJournal>>()
        @Synchronized fun open(file: File): IndexedHistoryJournal {
            if (active.size > 128) active.entries.removeIf { it.value.get() == null }
            return cache.getOrPut(file.absolutePath) {
                active[file.absolutePath]?.get() ?: IndexedHistoryJournal(file).also {
                    active[file.absolutePath] = java.lang.ref.WeakReference(it)
                }
            }
        }
        fun conversation(directory: File, target: String, group: Boolean): File {
            require(target.toLongOrNull() != null) { "Invalid history target" }
            return File(File(directory, "indexed"), (if (group) "group_" else "private_") + target + ".history")
        }
        fun page(directory: File, group: Boolean, page: Int, size: Int): List<Row> {
            require(page >= 0 && size in 1..10000)
            val skip = Math.multiplyExact(page, size)
            val through = Math.addExact(skip, size)
            val files = File(directory, "indexed").listFiles { f -> f.name.startsWith(if (group) "group_" else "private_") && f.extension == "history" } ?: emptyArray()
            // Keep only the best skip + size references across all conversations.
            // Equal keys retain file order, matching the stable ordering of the old merge.
            val order = compareBy<Candidate> { it.ref.key }.thenByDescending { it.source }
            val candidates = PriorityQueue(order)
            files.forEachIndexed { source, file ->
                val journal = open(file)
                journal.visitReferences(through) { ref ->
                    val candidate = Candidate(journal, ref, source)
                    if (candidates.size < through) candidates.add(candidate)
                    else if (order.compare(candidate, candidates.peek()) > 0) {
                        candidates.poll(); candidates.add(candidate)
                    } else return@visitReferences false // Remaining entries in this journal are older.
                    true
                }
            }
            val selected = candidates.sortedWith(order.reversed()).drop(skip)
            // Resolve IDs again under each journal's lock: another writer may have compacted it.
            val rows = selected.groupBy { it.journal }.mapValues { (journal, refs) ->
                journal.readRows(refs.map { it.ref.key.id }).associateBy { it.id }
            }
            return selected.mapNotNull { rows[it.journal]?.get(it.ref.key.id) }
        }
    }

    @Synchronized private fun visitReferences(limit: Int, visit: (Ref) -> Boolean) {
        loadIndex()
        for (ref in ordered.descendingMap().values.asSequence().take(limit)) if (!visit(ref)) break
    }
    @Synchronized private fun readRows(rowIds: List<String>): List<Row> {
        loadIndex()
        return payloads(rowIds.asSequence().mapNotNull { ids[it] })
    }
    @Synchronized fun size(): Int { loadIndex(); return ids.size }
    @Synchronized fun read(id: String): Row? { loadIndex(); return ids[id]?.let { payload(it) } }
    @Synchronized fun page(skip: Int, limit: Int): List<Row> {
        require(skip >= 0 && limit > 0)
        loadIndex()
        return payloads(ordered.descendingMap().values.asSequence().drop(skip).take(limit))
    }
    @Synchronized fun range(from: Long, through: Long): List<Row> {
        if (from > through) return emptyList()
        loadIndex()
        // Seek the lower bound in the tree; only visit matching references.
        return payloads(ordered.tailMap(Key(from, ""), true).values.asSequence().takeWhile { it.key.timestamp <= through })
    }
    @Synchronized fun delete() { if (file.exists() && !file.delete()) throw IOException("History deletion failed"); reset() }
    private fun reset() { ids.clear(); ordered.clear(); liveBytes = 0; indexedLength = -1; validEnd = 0 }
    private fun estimatedBytes(ref: Ref): Long = ref.length.toLong() + ref.key.id.length * 3L + 32
    private fun removeRef(id: String) {
        ids.remove(id)?.let { ordered.remove(it.key); liveBytes -= estimatedBytes(it) }
    }
    private fun putRef(ref: Ref) {
        ids[ref.key.id] = ref; ordered[ref.key] = ref; liveBytes += estimatedBytes(ref)
    }

    @Synchronized fun write(rows: List<Row>) {
        if (rows.isEmpty()) return
        loadIndex()
        file.parentFile.mkdirs()
        try {
            RandomAccessFile(file, "rw").use { output ->
                output.setLength(validEnd)
                if (validEnd == 0L) { output.writeInt(magic); validEnd = output.filePointer }
                output.seek(validEnd)
                for (row in rows) {
                    require(row.id.isNotBlank() && row.id.length <= 256)
                    val previous = ids[row.id]
                    if (previous != null && row.body != null && previous.key.timestamp == row.timestamp && payload(previous).body == row.body) continue
                    if (previous == null && row.body == null) continue
                    val bytes = row.body?.toByteArray(Charsets.UTF_8)
                    require(bytes == null || bytes.size <= maxPayload)
                    output.writeUTF(row.id); output.writeLong(row.timestamp); output.writeInt(bytes?.size ?: -1)
                    val offset = output.filePointer
                    if (bytes != null) output.write(bytes)
                    val crc = bytes?.let { CRC32().apply { update(it) }.value.toInt() } ?: 0
                    output.writeInt(crc); output.writeInt(committed)
                    removeRef(row.id)
                    if (bytes != null) putRef(Ref(Key(row.timestamp, row.id), offset, bytes.size, crc))
                }
                output.fd.sync()
                validEnd = output.filePointer; indexedLength = validEnd
            }
            compactIfNeeded()
        } catch (failure: Exception) { reset(); throw failure }
    }

    private fun loadIndex() {
        val length = if (file.exists()) file.length() else 0
        if (indexedLength == length) return
        reset()
        if (length < 4) { indexedLength = length; return }
        RandomAccessFile(file, "r").use { input ->
            require(input.readInt() == magic) { "Unsupported history journal" }
            validEnd = input.filePointer
            while (input.filePointer < length) {
                try {
                    val id = input.readUTF(); val timestamp = input.readLong(); val size = input.readInt()
                    require(id.isNotBlank() && id.length <= 256 && size in -1..maxPayload) { "Corrupt history record" }
                    val offset = input.filePointer; val end = offset + maxOf(0, size) + 8
                    if (end > length) break
                    input.seek(offset + maxOf(0, size)); val crc = input.readInt()
                    require(input.readInt() == committed) { "Uncommitted history record" }
                    removeRef(id)
                    if (size >= 0) putRef(Ref(Key(timestamp, id), offset, size, crc))
                    validEnd = end
                } catch (partial: EOFException) { break }
            }
        }
        indexedLength = length
    }
    private fun payloads(refs: Sequence<Ref>): List<Row> {
        val iterator = refs.iterator()
        if (!iterator.hasNext()) return emptyList()
        return RandomAccessFile(file, "r").use { input ->
            buildList { while (iterator.hasNext()) add(payload(input, iterator.next())) }
        }
    }
    private fun payload(ref: Ref): Row = RandomAccessFile(file, "r").use { payload(it, ref) }
    private fun payload(input: RandomAccessFile, ref: Ref): Row {
        input.seek(ref.offset)
        val bytes = ByteArray(ref.length); input.readFully(bytes)
        check(CRC32().apply { update(bytes) }.value.toInt() == ref.crc) { "History checksum mismatch" }
        return Row(ref.key.id, ref.key.timestamp, bytes.toString(Charsets.UTF_8))
    }
    private fun compactIfNeeded() {
        if (validEnd < 1024 * 1024 || validEnd <= liveBytes * 4) return
        AtomicHistoryFile.write(file) { stream ->
            val output = DataOutputStream(stream); output.writeInt(magic)
            RandomAccessFile(file, "r").use { input ->
                for (ref in ordered.values) {
                    val bytes = payload(input, ref).body!!.toByteArray(Charsets.UTF_8)
                    output.writeUTF(ref.key.id); output.writeLong(ref.key.timestamp); output.writeInt(bytes.size)
                    output.write(bytes); output.writeInt(ref.crc); output.writeInt(committed)
                }
            }
            output.flush()
        }
        reset(); loadIndex()
    }
}
