package core

import java.io.EOFException
import java.io.File
import java.io.FileDescriptor
import java.io.RandomAccessFile
import java.util.zip.CRC32

/** Binary record IO; index ownership, cache policy and commit admission stay in the journal. */
internal class HistoryJournalFormat {
    val magic = 0x514C4831
    private val committed = 0x434D4954
    private val maxPayload = 16 * 1024 * 1024

    data class Prepared(val row: IndexedHistoryJournal.Row, val bytes: ByteArray?)

    data class Record(
        val id: String,
        val timestamp: Long,
        val offset: Long,
        val length: Int,
        val crc: Int,
        val end: Long,
    )

    fun changed(value: Prepared, previousTimestamp: Long?, previousBody: () -> String?): Boolean {
        if (value.bytes == null) return previousTimestamp != null
        return previousTimestamp != value.row.timestamp || previousBody() != value.row.body
    }

    fun append(
        file: File,
        start: Long,
        changes: List<Prepared>,
        sync: (FileDescriptor) -> Unit,
        index: (Record) -> Unit,
    ): Long =
        RandomAccessFile(file, "rw").use { output ->
            output.setLength(start)
            if (start == 0L) output.writeInt(magic) else output.seek(start)
            for (change in changes) index(writeRecord(output, change))
            sync(output.fd)
            output.filePointer
        }

    private fun writeRecord(output: RandomAccessFile, change: Prepared): Record {
        val (row, bytes) = change
        output.writeUTF(row.id)
        output.writeLong(row.timestamp)
        output.writeInt(bytes?.size ?: -1)
        val offset = output.filePointer
        if (bytes != null) output.write(bytes)
        val crc = bytes?.let { CRC32().apply { update(it) }.value.toInt() } ?: 0
        output.writeInt(crc)
        output.writeInt(committed)
        return Record(row.id, row.timestamp, offset, bytes?.size ?: -1, crc, output.filePointer)
    }

    /**
     * An incomplete trailing record is expected after interruption; invalid complete records fail.
     */
    fun read(input: RandomAccessFile, fileLength: Long): Record? =
        try {
            val id = input.readUTF()
            val timestamp = input.readLong()
            val size = input.readInt()
            val validId = id.isNotBlank() && id.length <= 256
            require(validId && size in -1..maxPayload) { "Corrupt history record" }
            val offset = input.filePointer
            val end = offset + maxOf(0, size) + 8
            if (end > fileLength) null
            else {
                input.seek(offset + maxOf(0, size))
                val crc = input.readInt()
                require(input.readInt() == committed) { "Uncommitted history record" }
                Record(id, timestamp, offset, size, crc, end)
            }
        } catch (_: EOFException) {
            null
        }

    fun payload(
        input: RandomAccessFile,
        id: String,
        time: Long,
        offset: Long,
        size: Int,
        crc: Int,
    ): IndexedHistoryJournal.Row {
        input.seek(offset)
        val bytes = ByteArray(size)
        input.readFully(bytes)
        check(CRC32().apply { update(bytes) }.value.toInt() == crc) { "History checksum mismatch" }
        return IndexedHistoryJournal.Row(id, time, bytes.toString(Charsets.UTF_8))
    }
}
