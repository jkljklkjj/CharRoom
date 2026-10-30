import cli.*
import core.*
import core.state.AuthState
import core.state.GlobalAppState
import data.datasource.local.CliFileProvider
import data.datasource.local.LocalDataSourceImpl
import data.datasource.local.fileProvider
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import kotlin.system.exitProcess
import kotlinx.coroutines.*
import org.jline.reader.UserInterruptException

fun main(args: Array<String>) {
    // Keep pipes and PowerShell output deterministic instead of using Windows' legacy code page.
    System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true, Charsets.UTF_8))
    System.setErr(PrintStream(FileOutputStream(FileDescriptor.err), true, Charsets.UTF_8))
    val code =
        try {
            runBlocking { ChatLiteCli(args).run() }
            0
        } catch (_: CancellationException) {
            130
        } catch (_: UserInterruptException) {
            130
        } catch (error: IllegalArgumentException) {
            System.err.println(terminalText(error.message ?: "参数错误"))
            2
        } catch (error: Exception) {
            System.err.println("CLI 未能完成操作（${error.javaClass.simpleName}），请检查本地配置、网络或文件权限")
            1
        }
    if (code != 0) exitProcess(code)
}

class ChatLiteCli(private val args: Array<String>) {
    suspend fun run() {
        val options = CliOptions.parse(args)
        if (options.help) {
            println(
                """轻聊 CLI ${AppConfig.VERSION_NAME}
用法：java -jar chatlite-cli.jar [参数]
  --server <HTTPS根地址>       REST 地址，不带 /api；本机可使用 HTTP
  --quic-host <主机>           实时入口，和 REST 分别设置
  --quic-port <1..65535>       QUIC 端口
  --username <账号>            显式登录指定账号，不回退到其他账号
  --password-stdin             从标准输入第一行读取密码，需指定账号
  --no-auto-login              跳过保存的登录凭证
  --help / --version           显示帮助或版本，不连接网络
交互：/help 查看命令；Ctrl+C 清空当前输入，Ctrl+D 或 /exit 退出。
密码和消息不会保存到终端历史文件。"""
            )
            return
        }
        if (options.version) {
            println("ChatLite CLI ${AppConfig.VERSION_NAME}")
            return
        }
        val config = CliConfig()
        val saved = config.load()
        val server = options.server ?: saved.server ?: "https://chatlite.xin"
        require(
            options.server == null ||
                options.server == (saved.server ?: "https://chatlite.xin") ||
                options.quicHost != null && options.quicPort != null
        ) {
            "切换 REST 服务器请同时指定 --quic-host 和 --quic-port，避免连接到旧环境"
        }
        val host = options.quicHost ?: saved.quicHost ?: ServerConfig.QUIC_HOST
        val port = options.quicPort ?: saved.quicPort ?: ServerConfig.QUIC_PORT
        // Endpoint selection and validation finish before any DI, token read or network request.
        ApiEndpoints.setBaseUrl("$server/api")
        ServerConfig.QUIC_HOST = host
        ServerConfig.QUIC_PORT = port
        ServerConfig.DEVICE_TYPE = "cli"
        val directory = config.directory(server)
        val passwordFromStdin = if (options.passwordStdin) readCliPassword(System.`in`) else null
        config.lock(directory).use {
            fileProvider = CliFileProvider(File(directory, "data"))
            LocalChatHistoryStore = FileLocalChatHistoryStore(File(directory, "history"))
            try {
                val transport = QuicClientImpl()
                Chat = transport
                try {
                    CliConsole().use { console ->
                        console.output(
                            "轻聊 CLI ${AppConfig.VERSION_NAME} · $server · QUIC $host:$port"
                        )
                        val auth = CliAuthentication(LocalDataSourceImpl())
                        if (!login(auth, options, passwordFromStdin, console)) {
                            throw IllegalStateException("认证未完成")
                        }
                        config.save(server, host, port)
                        runSession(transport, console, server, directory)
                    }
                } finally {
                    transport.stop()
                }
            } finally {
                httpClient.close()
            }
        }
    }

    private suspend fun login(
        auth: CliAuthentication,
        options: CliOptions,
        supplied: String?,
        console: CliConsole,
    ): Boolean {
        if (options.username == null && !options.noAutoLogin) {
            auth.init()
            if (GlobalAppState.authState.value is AuthState.Authenticated) return true
        }
        val account =
            options.username
                ?: console.line("账号: ")?.trim()?.takeIf { it.isNotBlank() }
                ?: return false
        val password = supplied ?: console.line("密码: ", secret = true) ?: return false
        val state = auth.login(account, password)
        if (state !is AuthState.Authenticated) console.output("登录失败，请检查账号或网络后重新启动；未切换到其他账号。")
        return state is AuthState.Authenticated
    }

    private suspend fun runSession(
        transport: ChatTransport,
        console: CliConsole,
        server: String,
        directory: File,
    ) = coroutineScope {
        val session = SupervisorJob(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + session + Dispatchers.IO)
        val valid = java.util.concurrent.atomic.AtomicBoolean(true)
        val account = checkNotNull(GlobalAppState.currentUserId).also { check(it > 0) }
        val outbox = CliOutbox(directory, account)
        val assistant =
            CliAssistantTask(
                scope,
                { text -> agentChat(checkNotNull(GlobalAppState.currentToken), text) },
                console::output,
            )
        val historyRequests =
            kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
        val listener =
            object : MessageReceiveListener {
                override fun onAckReceived(messageId: String, seqId: Long, conversationId: String) {
                    outbox.accepted(messageId)
                }

                override fun onAckRejected(messageId: String, reason: String) {
                    outbox.failed(messageId, rejected = true)
                }

                override fun onPrivateMessageReceived(
                    senderId: Int,
                    message: String,
                    timestamp: Long,
                    seqId: Long,
                    conversationId: String,
                ) {
                    console.output("[用户 $senderId] $message")
                }

                override fun onGroupMessageReceived(
                    groupId: Int,
                    senderId: Int,
                    senderName: String,
                    message: String,
                    timestamp: Long,
                    seqId: Long,
                    conversationId: String,
                ) {
                    console.output("[群 $groupId · $senderName] $message")
                }
            }
        val invalidated = AuthStateListener {
            valid.set(false)
            assistant.cancel()
            transport.stop()
            console.output("登录已失效，请退出后重新登录。")
        }
        val shutdown = Thread({ transport.stop() }, "cli-shutdown")
        transport.addMessageReceiveListener(listener)
        transport.addAuthStateListener(invalidated)
        Runtime.getRuntime().addShutdownHook(shutdown)
        try {
            scope.launch { runInterruptible { transport.start() } }
            // QUIC owns reconnection. Observe authenticated state without starting a second
            // reconnect loop.
            scope.launch {
                var connected = false
                while (isActive) {
                    val current = transport.isConnected()
                    if (current != connected) {
                        connected = current
                        if (current) historyRequests.trySend(Unit)
                        console.output(if (current) "实时连接已就绪" else "实时连接断开，正在等待传输层恢复")
                    }
                    delay(1000)
                }
            }
            console.output("登录成功。输入 /help 查看命令，普通文本会请求 AI 助手。")
            val api =
                CliApi() // Resolve the latest global token at each request, rather than capturing
            // login's token.
            val sender =
                CliMessageSender(
                    transport,
                    { wrapper, failed ->
                        if (wrapper.type == MsgType.GROUP_CHAT) {
                            val message =
                                wrapper.groupChat
                                    .localMessage()
                                    .copy(
                                        isSent = false,
                                        deliveryState =
                                            if (failed) model.MessageDeliveryState.FAILED
                                            else model.MessageDeliveryState.SENDING,
                                    )
                            LocalChatHistoryStore.save(
                                account.toString(),
                                emptyList(),
                                listOf(message),
                            )
                        } else {
                            val message =
                                wrapper.chat
                                    .localMessage(account)
                                    .copy(
                                        isSent = false,
                                        deliveryState =
                                            if (failed) model.MessageDeliveryState.FAILED
                                            else model.MessageDeliveryState.SENDING,
                                    )
                            LocalChatHistoryStore.save(
                                account.toString(),
                                listOf(message),
                                emptyList(),
                            )
                        }
                    },
                    { console.output("◌ 发送中，正在等待服务端确认…") },
                )
            val delivery = CliDeliveryQueue(outbox, sender, transport, console::output)
            scope.launch {
                try {
                    delivery.run()
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    console.output("待发处理已暂停，请检查磁盘和 /pending 后重新启动；原队列保留。")
                }
            }
            scope.launch {
                for (ignored in historyRequests) {
                    try {
                        val token = checkNotNull(GlobalAppState.currentToken)
                        val count =
                            syncCliHistory(
                                account,
                                { valid.get() && isActive },
                                { getRecentHistoryPage(token, it) },
                                { getOfflineHistoryPage(token) },
                                { rows ->
                                    LocalChatHistoryStore.save(
                                        account.toString(),
                                        rows
                                            .filterNot { it.isGroup }
                                            .map { it.toPrivateMessage(account) },
                                        rows.filter { it.isGroup }.map { it.toGroupMessage() },
                                    )
                                },
                                { confirmOfflineMessages(token, it) },
                            )
                        console.output("历史同步完成：$count 条已合并到本地；/history 查看。")
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        console.output("历史同步未完成，已落盘部分保留；使用 /sync 重试。")
                    }
                }
            }
            historyRequests.trySend(Unit)
            while (true) {
                val line =
                    try {
                        console.line()
                    } catch (_: UserInterruptException) {
                        continue
                    }
                if (line == null) break
                if (line.isBlank()) continue
                try {
                    val command = CliCommands.parse(line)
                    val values = command.arguments
                    if (command.name in listOf("exit", "quit")) break
                    if (command.name == "help") {
                        help(values.firstOrNull(), console)
                        continue
                    }
                    if (command.name == "status") {
                        console.output(
                            "服务器: $server · QUIC: ${if (transport.isConnected()) "已认证" else "未就绪"} · 账号: ${GlobalAppState.currentAccount} · 助手: ${if (assistant.isRunning()) "处理中" else "空闲"} · 待发: ${outbox.entries().size}"
                        )
                        continue
                    }
                    if (command.name == "cancel") {
                        console.output(
                            if (assistant.cancel()) "已停止等待助手；服务端可能仍在处理。" else "当前没有助手任务。"
                        )
                        continue
                    }
                    if (command.name == "pending") {
                        val entries = outbox.entries()
                        console.output("待发 ${entries.size} 条；REVIEW/REJECTED 需核实后 /discard <ID>。")
                        entries.forEach {
                            console.output("${it.id} · ${it.status} · 尝试 ${it.attempts} 次")
                        }
                        continue
                    }
                    if (command.name == "discard") {
                        outbox.accepted(values[0])
                        console.output("已从待发队列移除，不撤回服务端消息。")
                        continue
                    }
                    if (command.name == "sync") {
                        historyRequests.trySend(Unit)
                        console.output("已安排历史同步。")
                        continue
                    }
                    check(valid.get()) { "登录已失效，请退出后重新登录" }
                    withTimeout(35_000) {
                        when (command.name) {
                            "ai" ->
                                console.output(
                                    if (assistant.start(values[0])) "助手正在处理，可继续输入；/cancel 停止等待。"
                                    else "助手已有任务，可先 /cancel。"
                                )
                            "msg",
                            "group send" -> {
                                outbox.enqueue(
                                    sender.prepare(
                                        account,
                                        values[0].toInt(),
                                        values[1],
                                        command.name == "group send",
                                    )
                                )
                                delivery.request()
                                if (!transport.isConnected())
                                    console.output("已保存待发消息，认证连接恢复后使用原 ID 发送。")
                            }
                            "history" -> showHistory(account, values, console)
                            "friend list" ->
                                api.fetchFriends()
                                    .also { console.output("好友 ${it.size} 人") }
                                    .forEach { console.output("${it.id}  ${it.username}") }
                            "group list" ->
                                api.fetchGroups()
                                    .also { console.output("群组 ${it.size} 个") }
                                    .forEach { console.output("${it.id}  ${it.username}") }
                            "friend add" -> result(api.addFriend(values[0]), console)
                            "friend accept" -> result(api.acceptFriend(values[0]), console)
                            "friend reject" -> result(api.rejectFriend(values[0]), console)
                            "group join" -> {
                                val response = api.addGroupWithResponse(values[0])
                                if (response.code == GROUP_JOIN_PENDING_CODE)
                                    console.output("入群申请已提交，等待管理员审核。")
                                else result(response.isSuccess, console)
                            }
                        }
                    }
                } catch (_: TimeoutCancellationException) {
                    console.output("操作超时，服务端可能已处理，请核实后再决定是否重试。")
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    console.output(
                        if (error is IllegalArgumentException || error is IllegalStateException)
                            error.message ?: "操作失败"
                        else "操作失败（${error.javaClass.simpleName}），可以继续输入命令。"
                    )
                }
            }
        } finally {
            withContext(NonCancellable) {
                assistant.cancel()
                historyRequests.close()
                session.cancel()
                transport.stop()
                session.join()
                transport.removeMessageReceiveListener(listener)
                transport.removeAuthStateListener(invalidated)
                try {
                    transport.logoutAndDisconnect()
                } finally {
                    Runtime.getRuntime().removeShutdownHook(shutdown)
                }
            }
        }
        console.output("再见")
    }

    private fun showHistory(account: Int, args: List<String>, console: CliConsole) {
        val target = args[1].toInt()
        val count = args[2].toInt()
        val lines =
            if (args[0] == "group")
                LocalChatHistoryStore.getGroupMessagesPage(account.toString(), target, 0, count)
                    .sortedBy { it.timestamp }
                    .map { "[${it.senderId}] ${it.text}" }
            else
                LocalChatHistoryStore.getPrivateMessagesPage(account.toString(), target, 0, count)
                    .sortedBy { it.timestamp }
                    .map { "[${if (it.sender) "我" else it.senderId}] ${it.message}" }
        if (lines.isEmpty()) console.output("本地暂无记录；这不表示服务器没有历史消息。")
        lines.forEach(console::output)
    }

    private fun result(ok: Boolean, console: CliConsole) =
        console.output(if (ok) "操作完成" else "操作未完成，请核实状态。")

    private fun help(name: String?, console: CliConsole) {
        val key = name?.removePrefix("/")
        val commands =
            CliCommands.usages.filterKeys { key == null || it == key || it.startsWith("$key ") }
        if (commands.isEmpty()) console.output("没有此命令，请输入 /help")
        commands.values.forEach(console::output)
    }
}
