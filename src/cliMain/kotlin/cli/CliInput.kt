package cli

import java.net.URI
import java.io.InputStream
import java.io.ByteArrayOutputStream

/** Read exactly one UTF-8 line; do not prefetch subsequent piped commands. */
fun readCliPassword(input: InputStream): String {
    val bytes = ByteArrayOutputStream()
    while (true) {
        val value = input.read()
        if (value == -1 || value == 10) break
        require(bytes.size() < 4096) { "密码输入过长" }
        bytes.write(value)
    }
    return bytes.toString(Charsets.UTF_8).removeSuffix("\r").also {
        require(it.isNotEmpty()) { "标准输入密码不能为空" }
    }
}

data class CliOptions(
    val server: String? = null, val quicHost: String? = null, val quicPort: Int? = null,
    val username: String? = null, val passwordStdin: Boolean = false,
    val noAutoLogin: Boolean = false, val help: Boolean = false, val version: Boolean = false
) {
    companion object {
        fun server(value: String): String {
            val uri = URI(value)
            require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null
                && uri.path in listOf("", "/") && (uri.port == -1 || uri.port in 1..65535)) { "--server 需要不含 /api、凭证或查询参数的服务器根地址" }
            require(uri.scheme == "https" || uri.scheme == "http" && uri.host in setOf("127.0.0.1", "localhost", "[::1]")) { "服务器需要 HTTPS；仅本机调试允许 HTTP" }
            return URI(uri.scheme, null, uri.host.lowercase(), uri.port, null, null, null).toString()
        }
        fun parse(args: Array<String>): CliOptions {
            var result = CliOptions()
            var index = 0
            fun value(): String {
                require(index + 1 < args.size && !args[index + 1].startsWith("--")) { "${args[index]} 缺少参数" }
                return args[++index].also { require(it.isNotBlank()) { "参数不能为空" } }
            }
            while (index < args.size) {
                result = when (val argument = args[index]) {
                    "--server" -> result.copy(server = server(value()))
                    "--quic-host" -> result.copy(quicHost = value().also {
                        require(it.none { char -> char.isWhitespace() || char in "/?#@" }) { "QUIC host 不能包含 URL 路径或凭证" }
                    })
                    "--quic-port" -> result.copy(quicPort = value().toIntOrNull()?.takeIf { it in 1..65535 }
                        ?: throw IllegalArgumentException("QUIC 端口必须是 1..65535"))
                    "--username" -> result.copy(username = value())
                    "--password-stdin" -> result.copy(passwordStdin = true)
                    "--no-auto-login" -> result.copy(noAutoLogin = true)
                    "--help", "-h" -> result.copy(help = true)
                    "--version" -> result.copy(version = true)
                    "--password" -> throw IllegalArgumentException("不接受命令行明文密码，请使用交互输入或 --username <账号> --password-stdin")
                    else -> throw IllegalArgumentException("未知参数：$argument")
                }
                index++
            }
            require(!result.passwordStdin || result.username != null) { "--password-stdin 必须指定 --username" }
            return result
        }
    }
}

data class CliCommand(val name: String, val arguments: List<String> = emptyList())

object CliCommands {
    val usages = linkedMapOf(
        "ai" to "/ai <内容>", "msg" to "/msg <用户ID> <内容>",
        "friend list" to "/friend list", "friend add" to "/friend add <账号>",
        "friend accept" to "/friend accept <申请ID>", "friend reject" to "/friend reject <申请ID>",
        "group list" to "/group list", "group join" to "/group join <群ID>",
        "group send" to "/group send <群ID> <内容>", "history" to "/history <user|group> <ID> [条数1..100]",
        "status" to "/status", "pending" to "/pending", "discard" to "/discard <待发ID>",
        "sync" to "/sync", "cancel" to "/cancel", "help" to "/help [命令]", "exit" to "/exit", "quit" to "/quit"
    )
    private fun id(value: String) { require(value.toIntOrNull()?.let { it > 0 } == true) { "ID 必须是正整数" } }
    fun parse(input: String): CliCommand {
        val line = input.trim()
        require(line.isNotEmpty()) { "内容不能为空" }
        require(line.length <= 8000) { "输入不能超过 8000 字符" }
        if (!line.startsWith('/')) return CliCommand("ai", listOf(line))
        val first = line.substringBefore(' ').substringBefore('\t')
        if (first in listOf("/msg", "/ai") || Regex("^/group\\s+send(?:\\s|$)").containsMatchIn(line)) {
            val group = line.startsWith("/group")
            val name = if (group) "group send" else first.drop(1)
            val match = Regex(if (name == "ai") "^/ai\\s+([\\s\\S]+)$" else if (group)
                "^/group\\s+send\\s+(\\S+)\\s+([\\s\\S]+)$" else "^/msg\\s+(\\S+)\\s+([\\s\\S]+)$").matchEntire(line)
            require(match != null) { "用法：${usages[name]}" }
            val values = match.groupValues.drop(1).map { it.trim() }
            require(values.all { it.isNotBlank() }) { "内容不能为空" }
            if (name != "ai") id(values[0])
            return CliCommand(name, values)
        }
        val parts = line.drop(1).split(Regex("\\s+"))
        val name = if (parts[0] in listOf("friend", "group") && parts.size > 1) parts.take(2).joinToString(" ") else parts[0]
        require(name in usages) { "未知命令，请输入 /help；与 AI 对话请使用普通文本或 /ai" }
        val values = parts.drop(if (' ' in name) 2 else 1)
        when (name) {
            "discard" -> require(values.size == 1 && values[0].matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "用法：${usages[name]}" }
            "help" -> require(values.size <= 1) { "用法：${usages[name]}" }
            "history" -> {
                require(values.size in 2..3 && values[0] in listOf("user", "group")) { "用法：${usages[name]}" }
                id(values[1])
                require(values.size == 2 || values[2].toIntOrNull()?.let { it in 1..100 } == true) { "条数必须是 1..100" }
                return CliCommand(name, values.take(2) + (values.getOrNull(2) ?: "20"))
            }
            "friend add", "friend accept", "friend reject", "group join" -> {
                require(values.size == 1) { "用法：${usages[name]}" }
                if (name != "friend add") id(values[0])
            }
            else -> require(values.isEmpty()) { "用法：${usages[name]}" }
        }
        return CliCommand(name, values)
    }
}

/** Remote text is data, never terminal instructions (including OSC, CSI and C1 controls). */
fun terminalText(value: String): String = value.take(8192).filter {
    (it.code >= 32 && it.code !in 127..159) || it == '\n' || it == '\t'
}
