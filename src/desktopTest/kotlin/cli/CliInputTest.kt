package cli

import kotlin.test.*

class CliInputTest {
    @Test fun stdinPasswordIsUtf8AndDoesNotConsumeFollowingCommands() {
        val input = "口令-fixture\r\n/status\n".byteInputStream()
        assertEquals("口令-fixture", readCliPassword(input))
        assertEquals("/status\n", input.readBytes().toString(Charsets.UTF_8))
        assertFailsWith<IllegalArgumentException> { readCliPassword("".byteInputStream()) }
    }
    @Test fun malformedOptionsFailBeforeAuthentication() {
        for (args in listOf(arrayOf("--server"), arrayOf("--typo"), arrayOf("--quic-port", "70000"),
            arrayOf("--server", "https://user:secret@example.test"), arrayOf("--server", "https://example.test/api"),
            arrayOf("--password", "secret"), arrayOf("--password-stdin"))) {
            assertFailsWith<IllegalArgumentException> { CliOptions.parse(args) }
        }
    }
    @Test fun localServerAndExplicitQuicAreValidated() {
        val options = CliOptions.parse(arrayOf("--server", "http://127.0.0.1:8089/", "--quic-host", "127.0.0.1", "--quic-port", "8080"))
        assertEquals("http://127.0.0.1:8089", options.server)
        assertEquals(8080, options.quicPort)
        assertFailsWith<IllegalArgumentException> { CliOptions.parse(arrayOf("--server", "http://example.test")) }
    }
    @Test fun typosAndIncompleteCommandsNeverBecomeAiRequests() {
        for (line in listOf("/mesg 1 hello", "/msg 1", "/msg -1 hello", "/group send 0 hi", "/friend", "/ai")) {
            assertFailsWith<IllegalArgumentException> { CliCommands.parse(line) }
        }
        assertEquals(CliCommand("ai", listOf("普通提问")), CliCommands.parse("普通提问"))
    }
    @Test fun messageBodyAndTypedConversationArePreserved() {
        assertEquals(CliCommand("msg", listOf("42", "hello   world")), CliCommands.parse("/msg\t42 hello   world"))
        assertEquals(CliCommand("group send", listOf("42", "hello")), CliCommands.parse("/group send 42 hello"))
        assertEquals(CliCommand("history", listOf("group", "42", "20")), CliCommands.parse("/history group 42"))
        assertFailsWith<IllegalArgumentException> { CliCommands.parse("/history user 42 10000") }
    }
    @Test fun terminalContentCannotExecuteControlSequences() {
        val value = terminalText("hello\u001B[2J\u001B]0;title\u0007\rworld\u009B31m")
        assertFalse(value.any { it.code < 32 && it != '\n' && it != '\t' || it.code in 127..159 })
        assertTrue(value.startsWith("hello"))
        assertEquals(8192, terminalText("x".repeat(9000)).length)
    }
}
