package core

import java.io.File
import java.nio.file.Files
import model.Message
import kotlin.test.*

class HistoryBatchOperationsTest {
    @Test fun aliasesInOneHistoryBatchResolveEarlierPlaceholdersAndRemainDurable() {
        val root = Files.createTempDirectory("history-alias-batch").toFile()
        try {
            val store = FileLocalChatHistoryStore(root)
            val pending = Message(1, "fixture", true, 2, 1, false, "local_1")
            val server = pending.copy(messageId = "101", seqId = 7, conversationId = "user:1:2",
                clientMessageId = "local_1", isSent = true)
            store.save("1", listOf(pending, server), emptyList())
            assertEquals(listOf("101"), store.getPrivateMessagesPage("1", 2, 0, 20).map { it.messageId })
            // A later stale UI snapshot must keep the canonical ID after reopening storage.
            FileLocalChatHistoryStore(root).save("1", listOf(pending), emptyList())
            val restored = FileLocalChatHistoryStore(root).restore("1").privateMessages.single()
            assertEquals("101", restored.messageId)
            assertEquals(7, restored.seqId)
            assertTrue(restored.isSent)
        } finally { root.deleteRecursively() }
    }

    @Test fun deletionTouchesOnlyTheSelectedConversationAndAccount() {
        val root = Files.createTempDirectory("history-target-delete").toFile()
        try {
            val store = FileLocalChatHistoryStore(root)
            fun message(peer: Int) = Message(1, "fixture", true, peer, 1, true, "same-id")
            store.save("1", listOf(message(2), message(3)), emptyList())
            store.save("9", listOf(message(2)), emptyList())
            // An unrelated corrupt journal must not be opened by deleting a message in peer 2.
            File(root, "1/indexed/private_4.history").writeText("corrupt")
            val other = File(root, "1/indexed/private_3.history").readBytes()
            store.deleteMessage("1", "2", "same-id", false)
            assertTrue(store.getPrivateMessagesPage("1", 2, 0, 20).isEmpty())
            assertEquals(1, store.getPrivateMessagesPage("1", 3, 0, 20).size)
            assertEquals(1, store.getPrivateMessagesPage("9", 2, 0, 20).size)
            assertContentEquals(other, File(root, "1/indexed/private_3.history").readBytes())
        } finally { root.deleteRecursively() }
    }

    @Test fun aliasBatchesAreValidatedBeforeAnyMappingIsWritten() {
        val root = Files.createTempDirectory("alias-validation").toFile()
        try {
            assertFailsWith<IllegalArgumentException> {
                MessageIdAliases.recordAll(root, "2", false, linkedMapOf(
                    "valid" to MessageIdAliases.Entry("101", 1, "user:1:2"),
                    "bad" to MessageIdAliases.Entry("bad", 2, "user:1:2")))
            }
            assertNull(MessageIdAliases.resolve(root, "2", false, "valid"))
            MessageIdAliases.recordAll(root, "2", false, mapOf("valid" to MessageIdAliases.Entry("101", 1, "user:1:2")))
            assertEquals(setOf("valid"), MessageIdAliases.resolveAll(root, "2", false, listOf("missing", "valid")).keys)
        } finally { root.deleteRecursively() }
    }
}
