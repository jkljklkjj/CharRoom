package cli

import core.buildChatPayload
import com.chatlite.proto.MessageProtos
import java.nio.file.Files
import kotlin.test.*

class CliOutboxTest {
    @Test fun retriesAndRestartsPreserveExactlyTheOriginalPayloadAndId() {
        val root = Files.createTempDirectory("cli-outbox").toFile()
        try {
            var now = 10L
            val outbox = CliOutbox(root, 42) { now }
            val wrapper = MessageProtos.MessageWrapper.parseFrom(buildChatPayload("7", "fixture", 42, 1, messageId = "local_test"))
            outbox.enqueue(wrapper)
            outbox.failed("local_test")
            assertTrue(outbox.due().isEmpty())
            now += 60_000
            val restored = CliOutbox(root, 42) { now }
            assertEquals(wrapper, restored.due().single().wrapper())
            restored.accepted("local_test")
            restored.failed("local_test") // Late failed result cannot resurrect an acknowledged row.
            assertTrue(CliOutbox(root, 42).entries().isEmpty())
            assertTrue(CliOutbox(root, 43).entries().isEmpty())
        } finally { root.deleteRecursively() }
    }
    @Test fun rejectedAndExpiredEntriesRequireReviewRatherThanBlindResend() {
        val root = Files.createTempDirectory("cli-outbox-state").toFile()
        try {
            var now = 10L
            val outbox = CliOutbox(root, 42) { now }
            fun wrapper(id: String) = MessageProtos.MessageWrapper.parseFrom(buildChatPayload("7", "fixture", 42, 1, messageId = id))
            outbox.enqueue(wrapper("rejected")); outbox.failed("rejected", true)
            outbox.enqueue(wrapper("expired")); now += 31 * 60_000
            assertTrue(outbox.due().isEmpty())
            assertEquals(setOf("REVIEW", "REJECTED"), outbox.entries().map { it.status }.toSet())
        } finally { root.deleteRecursively() }
    }
}
