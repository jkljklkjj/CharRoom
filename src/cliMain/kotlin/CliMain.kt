import core.ApiEndpoints
import core.agentChat
import core.NettyWebSocketClient
import core.QuicClientImpl
import core.WebSocketClientProvider
import core.state.AuthState
import core.state.GlobalAppState
import data.datasource.local.LocalDataSourceImpl
import data.repository.GlobalAuthRepository
import data.repository.GlobalChatRepository
import kotlinx.coroutines.*

fun main(args: Array<String>) = runBlocking {
    ChatLiteCli(args).run()
}

class ChatLiteCli(private val args: Array<String>) {

    private var serverUrl = "https://chatlite.xin"
    private var username = ""
    private var password = ""
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val localDataSource = LocalDataSourceImpl()
    private var useQuic = true
    private var transport: WebSocketClientProvider = NettyWebSocketClient

    suspend fun run() {
        parseArgs()
        printWelcome()

        // 登录：优先命令行参数 → 自动登录（读 ~/.qingliao/auth.txt） → 交互式
        val loggedIn = loginWithArgs()
            || tryAutoLogin()
            || interactiveLogin()

        if (!loggedIn) { println("认证失败，退出"); scope.cancel(); return }

        val token = GlobalAppState.currentToken ?: run { println("认证状态异常"); scope.cancel(); return }

        println("登录成功，欢迎使用轻聊命令行客户端！输入 /help 查看所有命令")

        // 传输层选择: QUIC (默认) 或 WebSocket
        showWsStatus()
        if (token.isNotBlank()) {
            transport = if (useQuic) {
                println("[QUIC] 使用 QUIC 协议")
                QuicClientImpl()
            } else {
                println("[WS] 使用 WebSocket 协议")
                NettyWebSocketClient
            }
            transport.addMessageReceiveListener(object : core.MessageReceiveListener {
                override fun onPrivateMessageReceived(senderId: Int, msg: String, timestamp: Long) {
                    println("\n💬 [用户 $senderId]: $msg"); print("> ")
                }
                override fun onGroupMessageReceived(gid: Int, sid: Int, name: String, msg: String, ts: Long) {
                    println("\n👥 [群 $gid $name]: $msg"); print("> ")
                }
            })
            transport.addAuthStateListener { reason ->
                println("\n⚠️ 登录失效: $reason")
            }
            try {
                transport.start()
                println(if (useQuic) "[QUIC] 连接完成" else "[WS] 连接完成")
            } catch (e: Exception) { println(if (useQuic) "[QUIC] $e" else "[WS] $e") }
        }

        // 加载联系人
        try { println("已加载 ${GlobalChatRepository.fetchFriends().size} 个联系人") } catch (_: Exception) {}

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
                t == "/status" -> showWsStatus()
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

    private fun showWsStatus() {
        val proto = if (useQuic) "QUIC" else "WebSocket"
        println("服务器: $serverUrl | $proto: ${if (transport.isConnected()) "🟢已连接" else "🔴未连接"} | 用户: ${GlobalAppState.currentAccount ?: "-"}")
    }

    private fun printHelp() {
        println("""命令: /ai <text> | /friend list|add|accept|reject | /group list|join | /status | /help | /exit""".trimIndent())
    }

    private fun parseArgs() {
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--server" -> {
                    serverUrl = args.getOrElse(i + 1) { serverUrl }.removeSuffix("/")
                    ApiEndpoints.setBaseUrl(serverUrl + "/api")
                    // 从 URL 中提取 host 作为 QUIC 默认地址
                    val host = try { java.net.URI(serverUrl).host ?: serverUrl } catch (_: Exception) { serverUrl }
                    core.ServerConfig.SERVER_IP = host
                    core.ServerConfig.QUIC_PORT = 9443
                    i++
                }
                "--quic-host" -> {
                    val qhost = args.getOrElse(i + 1) { "" }
                    if (qhost.isNotBlank()) {
                        core.ServerConfig.SERVER_IP = qhost
                        core.ServerConfig.QUIC_PORT = 9443
                    }
                    i++
                }
                "--username" -> { username = args.getOrElse(i + 1) { "" }; i++ }
                "--password" -> { password = args.getOrElse(i + 1) { "" }; i++ }
                "--quic" -> { useQuic = true }
                "--no-quic" -> { useQuic = false }
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
