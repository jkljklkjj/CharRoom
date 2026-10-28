package core

import model.Message
import java.nio.file.Files
import kotlin.test.*

class DurableHistoryStorageTest {
    @Test fun onlyJournalHistoryIsReadAndUnchangedRecordsAreNotAppended() {
        val previous = System.getProperty("user.home")
        val folder = Files.createTempDirectory("history-migration").toFile()
        try {
            System.setProperty("user.home", folder.absolutePath)
            val legacy = java.io.File(folder, ".qingliao/history/42/private_1.json")
            legacy.parentFile.mkdirs()
            legacy.writeText("""[{"senderId":42,"receiverId":1,"content":"legacy","timestamp":1,"isSent":true,"messageId":"old","sender":true}]""")
            assertTrue(DesktopLocalChatHistoryStore.restore("42").privateMessages.isEmpty())
            assertTrue(legacy.exists())
            assertFalse(java.io.File(legacy.parentFile, "legacy").exists())
            val pending = Message(42, "new", true, 1, 2, false, "new", deliveryState = model.MessageDeliveryState.QUEUED)
            DesktopLocalChatHistoryStore.save("42", listOf(pending), emptyList())
            val journal = java.io.File(legacy.parentFile, "indexed/private_1.history")
            val size = journal.length()
            DesktopLocalChatHistoryStore.save("42", listOf(pending), emptyList())
            assertEquals(size, journal.length())
            val rows = DesktopLocalChatHistoryStore.getPrivateMessagesPage("42", 1, 0, 1)
            assertEquals("new", rows.single().messageId)
            assertEquals(model.MessageDeliveryState.QUEUED, rows.single().deliveryState)
            assertTrue(DesktopLocalChatHistoryStore.clearConversation("42", "1", false))
            assertTrue(DesktopLocalChatHistoryStore.restore("42").privateMessages.isEmpty())
        } finally { System.setProperty("user.home", previous); folder.deleteRecursively() }
    }

    @Test fun canonicalAckSurvivesALateSnapshotAndMergesHistoryWithoutDuplicates() {
        val previous = System.getProperty("user.home")
        val folder = Files.createTempDirectory("history-id-alias").toFile()
        try {
            System.setProperty("user.home", folder.absolutePath)
            val pending = Message(42, "fixture", sender = true, receiverId = 1,
                timestamp = 1, isSent = false, messageId = "local_fixture")
            // ACK may arrive before the debounced local snapshot has reached disk.
            val ack = com.chatlite.proto.MessageProtos.AckMessage.newBuilder().setMessageId(pending.messageId)
                .setServerMessageId("9007199254740999").setSeqId(17).setConversationId("1:42").build()
            DesktopLocalChatHistoryStore.confirmMessageId("42", ack)
            DesktopLocalChatHistoryStore.save("42", listOf(pending), emptyList())
            DesktopLocalChatHistoryStore.save("42", listOf(pending.copy(messageId = ack.serverMessageId,
                seqId = 17, conversationId = "1:42", isSent = true)), emptyList())
            DesktopLocalChatHistoryStore.save("42", listOf(pending), emptyList())
            val restored = DesktopLocalChatHistoryStore.restore("42").privateMessages
            assertEquals(1, restored.size)
            assertEquals(ack.serverMessageId, restored.single().messageId)
            assertEquals(17L, restored.single().seqId)
            assertTrue(restored.single().isSent)
            DesktopLocalChatHistoryStore.save("43", listOf(pending), emptyList())
            assertEquals("local_fixture", DesktopLocalChatHistoryStore.restore("43").privateMessages.single().messageId)
        } finally { System.setProperty("user.home", previous); folder.deleteRecursively() }
    }

    @Test fun cursorFilesAreAccountScopedAndDiskWriteFailureIsObservable() {
        val previous = System.getProperty("user.home")
        val folder = Files.createTempDirectory("history-durability").toFile()
        try {
            System.setProperty("user.home", folder.absolutePath)
            DesktopLocalChatHistoryStore.saveConversationSeqIds("42", mapOf("conversation" to 7))
            DesktopLocalChatHistoryStore.saveConversationSeqIds("43", mapOf("conversation" to 11))
            assertEquals(7L, DesktopLocalChatHistoryStore.restoreConversationSeqIds("42")["conversation"])
            assertEquals(11L, DesktopLocalChatHistoryStore.restoreConversationSeqIds("43")["conversation"])
            val obstacle = java.io.File(folder, ".qingliao/history/44")
            obstacle.parentFile.mkdirs(); obstacle.writeText("not a directory")
            assertFails { DesktopLocalChatHistoryStore.save("44", listOf(Message(1, "fixture", receiverId = 44,
                timestamp = 1, messageId = "fixture")), emptyList()) }
        } finally { System.setProperty("user.home", previous); folder.deleteRecursively() }
    }
}
