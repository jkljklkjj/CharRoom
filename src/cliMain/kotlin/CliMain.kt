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

private val log = KotlinLogging.logger {}

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

    suspend fun run() {
        // 初始化 DI
        KoinInitializer.init(platformModules = listOf(desktopModule))
        transport = org.koin.core.context.GlobalContext.get().get<ChatTransport>()

        parseArgs()
        printWelcome()

        // 登录：优先命令行参数 → 自动登录（读 ~/.qingliao/auth.txt） → 交互式
        val loggedIn = loginWithArgs()
            || tryAutoLogin()
            || interactiveLogin()

        if (!loggedIn) { println("认证失败，退出"); scope.cancel(); return }

        val token = GlobalAppState.currentToken ?: run { println("认证状态异常"); scope.cancel(); return }

        println("登录成功，欢迎使用轻聊命令行客户端！输入 /help 查看所有命令")

        // QUIC 传输层初始化
        showConnectionStatus()
        if (token.isNotBlank()) {
            ServerConfig.DEVICE_TYPE = "cli"
            transport.addMessageReceiveListener(object : core.MessageReceiveListener {
                override fun onPrivateMessageReceived(senderId: Int, message: String, timestamp: Long) {
                    println("\n💬 [用户 $senderId]: $message"); print("> ")
                }
                override fun onGroupMessageReceived(groupId: Int, senderId: Int, senderName: String, message: String, timestamp: Long) {
                    println("\n👥 [群 $groupId $senderName]: $message"); print("> ")
                }
            })
            transport.addAuthStateListener { reason ->
                println("\n⚠️ 登录失效: $reason")
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
        while (true) {
            print("> ")
            val line = readLine() ?: break
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
                t == "/exit" || t == "/quit" -> break
                else -> handleAi(token, t)
            }
        }
    }

    private suspend fun handleAi(token: String, content: String) {
        print("AI 思考中... ")
        println(agentChat(token, content, stream = false))
    }

    private fun showConnectionStatus() {
        println("服务器: $serverUrl | QUIC: ${if (transport.isConnected()) "🟢已连接" else "🔴未连接"} | 用户: ${GlobalAppState.currentAccount ?: "-"}")
    }

    private fun printHelp() {
        println("""命令:
  /ai <text>               — AI 对话
  /msg <userId> <text>     — 发送私聊消息
  /friend list             — 好友列表
  /friend add <account>    — 添加好友
  /friend accept <id>      — 接受好友请求
  /friend reject <id>      — 拒绝好友请求
  /group list              — 群组列表
  /group join <id>         — 加入群组
  /group send <id> <text>  — 发送群聊消息
  /status                  — 连接状态
  /help                    — 此帮助
  /exit                    — 退出""".trimIndent())
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
