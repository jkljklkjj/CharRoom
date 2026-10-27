package core

import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AtomicHistoryFileTest {
    @Test fun interruptedWritePreservesOriginalAndCleansTemporaryFile() {
        val directory = Files.createTempDirectory("atomic-history-test").toFile()
        try {
            val file = directory.resolve("private_2.json")
            AtomicHistoryFile.write(file, "[\"original\"]")
            assertFailsWith<IOException> {
                AtomicHistoryFile.write(file) { output ->
                    output.write("[\"partial".toByteArray())
                    throw IOException("simulated disk failure")
                }
            }
            assertEquals("[\"original\"]", file.readText())
            assertEquals(listOf("private_2.json"), directory.listFiles()!!.map { it.name })
            AtomicHistoryFile.write(file, "[\"新消息\"]")
            assertEquals("[\"新消息\"]", file.readText())
        } finally { directory.deleteRecursively() }
    }
}
