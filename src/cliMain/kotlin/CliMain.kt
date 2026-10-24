import core.ApiEndpoints
import core.ServerConfig
import core.agentChat
import core.ApiService
import core.ChatTransport
import core.di.KoinInitializer
import core.di.desktopModule
import core.state.AuthState
import core.state.GlobalAppState
import data.datasource.local.LocalDataSourceImpl
import data.repository.GlobalAuthRepository
import data.repository.GlobalChatRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import model.Message
import java.io.File
import java.util.Properties

private val log = KotlinLogging.logger {}

// ── ANSI 彩色输出（终端 UI） ─────────────────────────────
private const val ANSI_RESET = "\u001B[0m"
private const val ANSI_GREEN = "\u001B[32m"
private const val ANSI_RED = "\u001B[31m"
private const val ANSI_YELLOW = "\u001B[33m"
private const val ANSI_CYAN = "\u001B[36m"
private const val ANSI_BOLD = "\u001B[1m"

private fun green(s: String) = "$ANSI_GREEN$s$ANSI_RESET"
private fun red(s: String) = "$ANSI_RED$s$ANSI_RESET"
private fun yellow(s: String) = "$ANSI_YELLOW$s$ANSI_RESET"
private fun cyan(s: String) = "$ANSI_CYAN$s$ANSI_RESET"
private fun bold(s: String) = "$ANSI_BOLD$s$ANSI_RESET"

fun main(args: Array<String>) = runBlocking {
    ChatLiteCli(args).run()
}

class ChatLiteCli(private val args: Array<String>) {

    private var serverUrl = "https://chatlite.xin"
    private var username = ""
    private var password = ""
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val localDataSource = LocalDataSourceImpl()
    private lateinit var transport: ChatTransport

    // ── 配置文件持久化（~/.qingliao/config.properties） ──
    private val configFile: File by lazy {
        File(System.getProperty("user.home"), ".qingliao/config.properties")
    }

    private fun loadConfig() {
        try {
            if (!configFile.exists()) return
            val props = Properties()
            configFile.inputStream().use { props.load(it) }
            props.getProperty("server")?.takeIf { it.isNotBlank() }?.let {
                serverUrl = it.removeSuffix("/")
                ApiEndpoints.setBaseUrl(serverUrl + "/api")
            }
            props.getProperty("quic.host")?.takeIf { it.isNotBlank() }?.let { ServerConfig.QUIC_HOST = it }
            props.getProperty("quic.port")?.toIntOrNull()?.let { ServerConfig.QUIC_PORT = it }
            props.getProperty("username")?.takeIf { it.isNotBlank() }?.let { username = it }
        } catch (_: Exception) { /* 配置损坏时忽略，不影响启动 */ }
    }

    private fun saveConfig() {
        try {
            configFile.parentFile?.mkdirs()
            val props = Properties()
            props.setProperty("server", serverUrl)
            props.setProperty("quic.host", ServerConfig.QUIC_HOST)
            props.setProperty("quic.port", ServerConfig.QUIC_PORT.toString())
            if (username.isNotBlank()) props.setProperty("username", username)
            configFile.outputStream().use { props.store(it, "ChatLite CLI config") }
        } catch (_: Exception) { /* 保存失败不影响主流程 */ }
    }
    private var isReconnecting = false
    private var reconnectDelay = 1000L
    private val maxReconnectDelay = 30000L

    suspend fun run() {
        // 初始化 DI
        KoinInitializer.init(platformModules = listOf(desktopModule))
        transport = org.koin.core.context.GlobalContext.get().get<ChatTransport>()

        // 先读配置文件（~/.qingliao/config.properties），命令行参数再覆盖
        loadConfig()
        parseArgs()
        saveConfig()
        printWelcome()

        // 登录：优先命令行参数 → 自动登录（读 ~/.qingliao/auth.txt） → 交互式
        val loggedIn = loginWithArgs()
            || tryAutoLogin()
            || interactiveLogin()

        if (!loggedIn) { println(red("认证失败，退出")); scope.cancel(); return }

        val token = GlobalAppState.currentToken ?: run { println(red("认证状态异常")); scope.cancel(); return }

        println(green("登录成功") + "，欢迎使用轻聊命令行客户端！输入 " + yellow("/help") + " 查看所有命令")

        // QUIC 传输层初始化
        showConnectionStatus()
        if (token.isNotBlank()) {
            ServerConfig.DEVICE_TYPE = "cli"
            transport.addMessageReceiveListener(object : core.MessageReceiveListener {
                override fun onPrivateMessageReceived(senderId: Int, message: String, timestamp: Long, seqId: Long, conversationId: String) {
                    println("\n💬 [用户 $senderId]: $message"); print("> ")
                }
                override fun onGroupMessageReceived(groupId: Int, senderId: Int, senderName: String, message: String, timestamp: Long, seqId: Long, conversationId: String) {
                    println("\n👥 [群 $groupId $senderName]: $message"); print("> ")
                }
            })
            transport.addAuthStateListener { reason ->
                println("\n⚠️ 登录失效: $reason")
            }

            // 启动连接状态监控
            scope.launch {
                monitorConnection(token)
            }
            try {
                transport.start()
                log.info { "QUIC 连接完成" }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                log.error(e) { "QUIC 连接失败" }
            }
        }

        // 加载联系人
        try { println("已加载 ${GlobalChatRepository.fetchFriends().size} 个联系人") } catch (_: Exception) {}

        // 登录后拉取离线消息
        fetchOfflineMessages(token)

        // 命令循环
        enterReplLoop(token)
        transport.logoutAndDisconnect()
        scope.cancel()
        println("再见")
    }

    // ── 连接监控与自动重连 ──

    private suspend fun monitorConnection(token: String) {
        while (true) {
            delay(5000) // 每5秒检查一次连接状态
            if (!transport.isConnected() && !isReconnecting) {
                println("\n🔌 连接断开，尝试重连...")
                attemptReconnect(token)
            }
        }
    }

    private suspend fun attemptReconnect(token: String) {
        isReconnecting = true
        var attempts = 0

        while (isReconnecting && attempts < 10) {
            attempts++
            println("🔄 重连尝试 $attempts/10 (等待 ${reconnectDelay}ms)...")

            try {
                delay(reconnectDelay)
                transport.start()
                println("✅ 重连成功")
                isReconnecting = false
                reconnectDelay = 1000L // 重置延迟
                return
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                println("❌ 重连失败: ${e.message}")
                // 指数退避
                reconnectDelay = (reconnectDelay * 2).coerceAtMost(maxReconnectDelay)
            }
        }

        println("❌ 重连失败次数过多，停止重连")
        isReconnecting = false
        reconnectDelay = 1000L
    }

    private suspend fun loginWithArgs(): Boolean {
        if (username.isBlank() || password.isBlank()) return false
        print("登录 $username ... ")
        val r = GlobalAuthRepository.login(username, password, rememberMe = true)
        val ok = r is AuthState.Authenticated
        println(if (ok) "OK" else "FAIL")
        return ok
    }

    private suspend fun tryAutoLogin(): Boolean {
        val acct = localDataSource.getSavedAccount() ?: return false
        print("自动登录 $acct ... ")
        GlobalAuthRepository.init()
        val ok = GlobalAppState.authState.value is AuthState.Authenticated
        println(if (ok) "OK" else "FAIL（清除旧凭证）")
        if (!ok) localDataSource.clearAuth()
        return ok
    }

    private suspend fun interactiveLogin(): Boolean {
        print("账号: "); username = readLine()?.trim().orEmpty()
        if (username.isBlank()) return false
        print("密码: "); password = System.console()?.readPassword()?.concatToString() ?: readLine().orEmpty()
        return loginWithArgs()
    }

    // ── 离线消息 ──

    private suspend fun fetchOfflineMessages(token: String) {
        try {
            val msgs = ApiService(token).getOfflineMessages()
            if (msgs.isEmpty()) return
            println("\n📩 ${msgs.size} 条离线消息:")
            msgs.forEach { msg: Message ->
                println("  💬 [${msg.senderId}]: ${msg.message.take(80)}")
            }
        } catch (_: Exception) { /* 拉取离线消息失败，不影响主流程 */ }
    }

    // ── 发送私聊消息 ──

    private suspend fun sendPrivateMessage(token: String, receiverId: String, content: String) {
        val api = ApiService(token)
        val ok = api.sendPrivateMessage(receiverId = receiverId, content = content, messageType = "text")
        println(if (ok) "✅ 已发送" else "❌ 发送失败")
    }

    // ── 发送群聊消息 ──

    private suspend fun sendGroupMessage(token: String, groupId: String, content: String) {
        val api = ApiService(token)
        val ok = api.sendGroupMessage(groupId = groupId, content = content, messageType = "text")
        println(if (ok) "✅ 已发送" else "❌ 发送失败")
    }

    // ---- 以下为 CLI 特有的命令处理 ----

    private fun enterReplLoop(token: String) = runBlocking {
        // jline3：命令历史（~/.qingliao/history.txt）+ Tab 补全
        val terminal = org.jline.terminal.TerminalBuilder.builder().system(true).build()
        val historyFile = java.io.File(System.getProperty("user.home"), ".qingliao/history.txt")
        val history = org.jline.reader.impl.history.DefaultHistory()
        try { history.read(historyFile.toPath(), false) } catch (_: Exception) { /* 历史文件不存在/损坏时忽略 */ }
        val completer = org.jline.reader.Completer { _reader, line, candidates ->
            val buf = line.line().substring(0, line.cursor()).lowercase()
            CLI_COMMANDS.filter { it.startsWith(buf) }.forEach { candidates.add(org.jline.reader.Candidate(it)) }
        }
        val reader = org.jline.reader.LineReaderBuilder.builder()
            .terminal(terminal)
            .history(history)
            .completer(completer)
            .option(org.jline.reader.LineReader.Option.HISTORY_IGNORE_SPACE, true)
            .build()

        while (true) {
            val line = reader.readLine("> ") ?: break
            val t = line.trim(); if (t.isBlank()) continue
            when {
                t.startsWith("/ai ") -> handleAi(token, t.removePrefix("/ai ").trim())
                t == "/friend list" -> { val f = GlobalChatRepository.fetchFriends(); println("好友(${f.size}): ${f.joinToString { "${it.id}:${it.username}" }}") }
                t.startsWith("/friend add ") -> { val ok = GlobalChatRepository.addFriend(t.removePrefix("/friend add ").trim()); println(if (ok) "OK" else "FAIL") }
                t.startsWith("/friend accept ") -> { val ok = GlobalChatRepository.acceptFriend(t.removePrefix("/friend accept ").trim()); println(if (ok) "OK" else "FAIL") }
                t.startsWith("/friend reject ") -> { val ok = GlobalChatRepository.rejectFriend(t.removePrefix("/friend reject ").trim()); println(if (ok) "OK" else "FAIL") }
                t == "/group list" -> { val g = GlobalChatRepository.fetchGroups(); println("群组(${g.size}): ${g.joinToString { "${it.id}:${it.username}" }}") }
                t.startsWith("/group join ") -> { val ok = GlobalChatRepository.addGroup(t.removePrefix("/group join ").trim()); println(if (ok) "OK" else "FAIL") }
                t.startsWith("/msg ") -> {
                    // /msg <userId> <text>
                    val rest = t.removePrefix("/msg ").trim()
                    val spaceIdx = rest.indexOf(' ')
                    if (spaceIdx > 0) {
                        val targetId = rest.substring(0, spaceIdx)
                        val text = rest.substring(spaceIdx + 1).trim()
                        sendPrivateMessage(token, targetId, text)
                    } else println("用法: /msg <用户ID> <消息内容>")
                }
                t.startsWith("/group send ") -> {
                    // /group send <groupId> <text>
                    val rest = t.removePrefix("/group send ").trim()
                    val spaceIdx = rest.indexOf(' ')
                    if (spaceIdx > 0) {
                        val groupId = rest.substring(0, spaceIdx)
                        val text = rest.substring(spaceIdx + 1).trim()
                        sendGroupMessage(token, groupId, text)
                    } else println("用法: /group send <群组ID> <消息内容>")
                }
                t == "/status" -> showConnectionStatus()
                t == "/help" -> printHelp()
                t.startsWith("/help ") -> printHelp(t.removePrefix("/help ").trim())
                t == "/exit" || t == "/quit" -> break
                else -> handleAi(token, t)
            }
        }
        // 持久化历史 + 释放终端
        try { history.write(historyFile.toPath(), false) } catch (_: Exception) {}
        try { terminal.close() } catch (_: Exception) {}
    }

    private companion object {
        /** Tab 补全的命令列表 */
        val CLI_COMMANDS = listOf(
            "/ai", "/msg", "/friend list", "/friend add", "/friend accept", "/friend reject",
            "/group list", "/group join", "/group send", "/status", "/help", "/exit", "/quit"
        )
    }

    private suspend fun handleAi(token: String, content: String) {
        print("AI 思考中... ")
        println(agentChat(token, content, stream = false))
    }

    private fun showConnectionStatus() {
        val status = if (transport.isConnected()) green("🟢已连接") else red("🔴未连接")
        println("服务器: ${cyan(serverUrl)} | QUIC: $status | 用户: ${cyan(GlobalAppState.currentAccount ?: "-")}")
    }

    private fun printHelp(command: String? = null) {
        val all = mapOf(
            "/ai" to "AI 对话",
            "/msg" to "发送私聊消息",
            "/friend" to "好友管理（list / add / accept / reject）",
            "/group" to "群组管理（list / join / send）",
            "/status" to "连接状态",
            "/help" to "此帮助（/help <命令> 查看详细）",
            "/exit" to "退出"
        )
        if (command != null) {
            val entry = all.entries.firstOrNull { command == it.key }
            if (entry == null) {
                println(yellow("未知命令: $command （输入 /help 查看全部命令）"))
                return
            }
            when (command) {
                "/ai" -> println("""${cyan("/ai <text>")} — 与 AI 助手对话
  示例: /ai 帮我写一段介绍
  说明: 普通文本输入也会自动走 AI 对话""".trimIndent())
                "/msg" -> println("""${cyan("/msg <userId> <text>")} — 发送私聊消息
  示例: /msg 10001 你好
  说明: userId 为对方账号 ID""".trimIndent())
                "/friend" -> println("""${cyan("/friend <操作> <参数>")} — 好友管理
  /friend list                — 好友列表
  /friend add <account>       — 添加好友
  /friend accept <id>         — 接受好友请求
  /friend reject <id>         — 拒绝好友请求""".trimIndent())
                "/group" -> println("""${cyan("/group <操作> <参数>")} — 群组管理
  /group list                 — 群组列表
  /group join <id>            — 加入群组
  /group send <id> <text>     — 发送群聊消息""".trimIndent())
                "/status" -> println("""${cyan("/status")} — 显示服务器地址、QUIC 连接状态与当前用户""".trimIndent())
                "/help" -> println("""${cyan("/help [<命令>]")} — 不带参数显示全部命令；带参数显示指定命令的详细帮助
  示例: /help /msg""".trimIndent())
                "/exit" -> println("""${cyan("/exit")}（或 /quit）— 退出客户端""".trimIndent())
            }
            return
        }
        println(bold("命令："))
        all.forEach { (cmd, desc) -> println("  ${yellow(cmd.padEnd(20))}$desc") }
        println()
        println(bold("特性："))
        println("  • 自动重连：连接断开后自动尝试重连（指数退避，最多10次）")
        println("  • 离线消息：登录后自动拉取离线消息")
        println("  • 安全输入：密码输入不回显（使用 readPassword）")
        println("  • 配置持久化：服务器/QUIC 地址保存在 ~/.qingliao/config.properties")
    }

    private fun parseArgs() {
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--server" -> {
                    serverUrl = args.getOrElse(i + 1) { serverUrl }.removeSuffix("/")
                    ApiEndpoints.setBaseUrl(serverUrl + "/api")
                    i++
                }
                "--quic-host" -> {
                    val qhost = args.getOrElse(i + 1) { "" }
                    if (qhost.isNotBlank()) core.ServerConfig.QUIC_HOST = qhost
                    i++
                }
                "--quic-port" -> {
                    core.ServerConfig.QUIC_PORT = args.getOrElse(i + 1) { "443" }.toIntOrNull() ?: 443
                    i++
                }
                "--username" -> { username = args.getOrElse(i + 1) { "" }; i++ }
                "--password" -> { password = args.getOrElse(i + 1) { "" }; i++ }
            }
            i++
        }
    }

    private fun printWelcome() {
        println("=".repeat(50))
        println("     ChatLite CLI v1.0 — Server: $serverUrl")
        println("=".repeat(50))
    }
}
