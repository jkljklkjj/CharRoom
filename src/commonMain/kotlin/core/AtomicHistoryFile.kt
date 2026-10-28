package core

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** Write beside the destination, flush, then atomically replace it. Never truncate the old file. */
internal object AtomicHistoryFile {
    fun write(file: File, content: String) = write(file) { output -> output.write(content.toByteArray(Charsets.UTF_8)) }

    fun write(file: File, writer: (FileOutputStream) -> Unit) {
        val target = file.toPath().toAbsolutePath()
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, ".history-", ".tmp")
        try {
            FileOutputStream(temporary.toFile()).use { output ->
                writer(output)
                output.fd.sync()
            }
            // If the filesystem cannot replace atomically, fail and preserve the original.
            Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
