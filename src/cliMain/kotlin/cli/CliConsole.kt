package cli

import org.jline.reader.*
import org.jline.terminal.TerminalBuilder
import org.jline.terminal.Terminal

/** JLine owns input and redraws it when asynchronous messages arrive. No disk command history. */
class CliConsole(private val terminal: Terminal = TerminalBuilder.builder().system(true).encoding(Charsets.UTF_8).build()) : AutoCloseable {
    private val reader = LineReaderBuilder.builder().terminal(terminal)
        .variable(LineReader.HISTORY_SIZE, 100)
        .completer { _, line, candidates ->
            val words = line.words()
            val choices = when {
                line.wordIndex() == 0 -> CliCommands.usages.keys.map { "/${it.substringBefore(' ')}" }.distinct()
                line.wordIndex() == 1 && words[0] in listOf("/friend", "/group") ->
                    CliCommands.usages.keys.filter { it.startsWith(words[0].drop(1) + " ") }.map { it.substringAfter(' ') }
                line.wordIndex() == 1 && words[0] == "/history" -> listOf("user", "group")
                else -> emptyList()
            }
            choices.filter { it.startsWith(line.word()) }.forEach { candidates.add(Candidate(it)) }
        }.build()

    fun line(prompt: String = "> ", secret: Boolean = false): String? = try {
        require(!secret || !terminal.type.startsWith("dumb")) {
            "当前终端无法可靠隐藏密码，请使用 --username <账号> --password-stdin"
        }
        // Passwords must not enter JLine history, even in memory.
        reader.setVariable(LineReader.DISABLE_HISTORY, secret)
        if (secret) reader.readLine(prompt, '*') else reader.readLine(prompt)
    } catch (_: EndOfFileException) { null }

    fun output(text: String) { reader.printAbove(terminalText(text)) }
    override fun close() { terminal.close() }
}
