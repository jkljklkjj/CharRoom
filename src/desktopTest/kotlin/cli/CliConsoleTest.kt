package cli

import org.jline.terminal.TerminalBuilder
import org.jline.terminal.Attributes
import java.io.ByteArrayOutputStream
import kotlin.test.*

class CliConsoleTest {
    @Test fun maskedInputDoesNotEchoPasswordAndFollowingInputSurvives() {
        val output = ByteArrayOutputStream()
        val attributes = Attributes().apply { setLocalFlag(Attributes.LocalFlag.ECHO, false) }
        val terminal = TerminalBuilder.builder().system(false).type("xterm").attributes(attributes)
            .streams("fixture-secret\n/status\n".byteInputStream(), output).build()
        CliConsole(terminal).use { console ->
            assertEquals("fixture-secret", console.line("password: ", secret = true))
            assertEquals("/status", console.line())
        }
        assertFalse(output.toString().contains("fixture-secret"))
    }
    @Test fun unsupportedTerminalCannotFallBackToVisiblePasswordInput() {
        val terminal = TerminalBuilder.builder().system(false).type("dumb")
            .streams("".byteInputStream(), ByteArrayOutputStream()).build()
        CliConsole(terminal).use { console ->
            assertFailsWith<IllegalArgumentException> { console.line(secret = true) }
        }
    }
}
