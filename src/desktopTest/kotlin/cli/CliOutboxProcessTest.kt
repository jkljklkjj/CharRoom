package cli

import core.buildChatPayload
import com.chatlite.proto.MessageProtos
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Child process has an empty static journal cache: recovery must come from committed bytes. */
object CliOutboxProbe {
    @JvmStatic fun main(args: Array<String>) {
        val outbox = CliOutbox(File(args[0]), 42)
        val row = outbox.entries().single()
        check(row.id == "local_cross_process" && row.wrapper().chat.content == "fixture")
        outbox.accepted(row.id)
    }
}

class CliOutboxProcessTest {
    @Test fun anotherJvmCanRecoverAndAcknowledgeThePersistedEntry() {
        val root = Files.createTempDirectory("cli-process-").toFile()
        try {
            val outbox = CliOutbox(root, 42)
            outbox.enqueue(MessageProtos.MessageWrapper.parseFrom(buildChatPayload("7", "fixture", 42, 1, messageId = "local_cross_process")))
            val java = File(System.getProperty("java.home"), "bin/java" + if (System.getProperty("os.name").startsWith("Windows")) ".exe" else "")
            val process = ProcessBuilder(java.path, "-cp", checkNotNull(System.getProperty("cli.test.classpath")),
                "cli.CliOutboxProbe", root.path).redirectErrorStream(true).start()
            assertTrue(process.waitFor(20, TimeUnit.SECONDS).also { if (!it) process.destroyForcibly() })
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.exitValue(), output)
            assertTrue(outbox.entries().isEmpty())
        } finally { root.deleteRecursively() }
    }
}
