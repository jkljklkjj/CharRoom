package core

import java.nio.file.Files
import model.Message
import kotlin.test.*

class RecentHistoryStorageTest {
    @Test fun pagesSurviveUiSnapshotsReloadAndDeletionWithoutTruncatingAt500() {
        val previous = System.getProperty("user.home")
        val directory = Files.createTempDirectory("history-test").toFile()
        System.setProperty("user.home", directory.absolutePath)
        try {
            val rows = (1..650).map { Message(senderId = if (it % 2 == 0) 1 else 2, receiverId = if (it % 2 == 0) 2 else 1, sender = it % 2 == 0, message = "fixture", timestamp = it.toLong(), messageId = it.toString(), seqId = it.toLong(), conversationId = "user:1:2") }
            rows.chunked(100).forEach { DesktopLocalChatHistoryStore.save("1", it, emptyList()) }
            DesktopLocalChatHistoryStore.save("1", rows.takeLast(10), emptyList())
            DesktopLocalChatHistoryStore.save("3", rows.take(1), emptyList())
            assertEquals(650, DesktopLocalChatHistoryStore.getPrivateMessagesPage("1", 2, 0, 1000).size)
            DesktopLocalChatHistoryStore.deleteMessage("1", "650", false)
            val restored = DesktopLocalChatHistoryStore.getPrivateMessagesPage("1", 2, 0, 1000)
            assertEquals(649, restored.size)
            assertEquals(649L, restored.last().seqId)
            assertEquals("user:1:2", restored.last().conversationId)
            assertFalse(restored.any { it.messageId == "650" })
            assertEquals(1, DesktopLocalChatHistoryStore.getPrivateMessagesPage("3", 2, 0, 1000).size)
        } finally {
            System.setProperty("user.home", previous)
            directory.deleteRecursively()
        }
    }
}
