package cli

import core.FileLocalChatHistoryStore
import data.datasource.local.CliFileProvider
import model.Message
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class CliStorageTest {
    @Test fun endpointsAreAtomicAndDoNotContainCredentials() {
        val root = Files.createTempDirectory("cli-config-").toFile()
        try {
            val config = CliConfig(root)
            config.save("https://example.test", "quic.example.test", 443)
            val saved = config.load()
            assertEquals("https://example.test", saved.server)
            assertNull(saved.username)
            assertFalse(File(root, "cli/config.properties").readText().contains("password"))
            assertEquals(config.directory("https://EXAMPLE.test/"), config.directory("https://example.test"))
            assertNotEquals(config.directory("https://example.test"), config.directory("http://localhost:8089"))
        } finally { root.deleteRecursively() }
    }
    @Test fun twoCliProcessesCannotWriteTheSameServerJournal() {
        val root = Files.createTempDirectory("cli-lock-").toFile()
        try {
            val config = CliConfig(root)
            val directory = config.directory("https://example.test")
            config.lock(directory).use {
                assertFailsWith<IllegalStateException> { config.lock(directory) }
                config.lock(config.directory("https://other.test")).close()
            }
            config.lock(directory).close()
        } finally { root.deleteRecursively() }
    }
    @Test fun accountIdsOnDifferentServersDoNotShareMessages() {
        val root = Files.createTempDirectory("cli-isolation-").toFile()
        try {
            val first = FileLocalChatHistoryStore(File(root, "first/history"))
            val second = FileLocalChatHistoryStore(File(root, "second/history"))
            first.save("7", listOf(Message(42, "fixture", timestamp = 1, messageId = "test", receiverId = 7)), emptyList())
            assertEquals("fixture", first.getPrivateMessagesPage("7", 42).single().message)
            assertTrue(second.getPrivateMessagesPage("7", 42).isEmpty())
            assertTrue(first.getPrivateMessagesPage("8", 42).isEmpty())
            val provider = CliFileProvider(File(root, "data"))
            val file = File(provider.getAppDataDir(), "fixture").path
            provider.writeFile(file, byteArrayOf(1, 2)); provider.writeFile(file, byteArrayOf(3))
            assertContentEquals(byteArrayOf(3), provider.readFile(file))
        } finally { root.deleteRecursively() }
    }
}
