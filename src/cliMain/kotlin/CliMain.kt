import core.ApiEndpoints
import core.agentChat
import data.repository.AuthState
import data.repository.GlobalAuthRepository
import data.repository.GlobalChatRepository
import kotlinx.coroutines.*

fun main(args: Array<String>) = runBlocking {
    val cli = ChatLiteCli(args)
    cli.run()
}

class ChatLiteCli(private val args: Array<String>) {

    private var serverUrl = "http://127.0.0.1:8088"
    private var username = ""
    private var password = ""
    private var token = ""
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun run() {
        parseArgs()
        printWelcome()

        if (username.isNotBlank() && password.isNotBlank()) {
            loginAutomatically()
        } else {
            promptInteractiveLogin()
        }

        if (token.isBlank()) {
            println("认证失败，退出")
            return
        }

        println("登录成功，欢迎使用轻聊命令行客户端！")
        println("输入 /help 查看所有命令")

        startMessageListener()
        enterReplLoop()

        scope.cancel()
        println("再见")
    }

    private fun parseArgs() {
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--server" -> { serverUrl = args.getOrElse(i + 1) { serverUrl }; i++ }
                "--username" -> { username = args.getOrElse(i + 1) { "" }; i++ }
                "--password" -> { password = args.getOrElse(i + 1) { "" }; i++ }
            }
            i++
        }
        ApiEndpoints.baseUrl = serverUrl.removeSuffix("/")
    }

    private fun printWelcome() {
        println("=".repeat(50))
        println("     ChatLite CLI v1.0")
        println("     Server: $serverUrl")
        println("=".repeat(50))
    }

    private suspend fun loginAutomatically() {
        print("正在登录 $username ... ")
        val state = GlobalAuthRepository.login(username, password, rememberMe = true)
        if (state is AuthState.Authenticated) {
            token = state.accessToken
            println("OK")
        } else {
            println("FAIL " + (state as? AuthState.Error)?.message)
        }
    }

    private suspend fun promptInteractiveLogin() {
        print("账号: ")
        username = readLine()?.trim().orEmpty()
        print("密码: ")
        password = System.console()?.readPassword()?.concatToString() ?: readLine().orEmpty()
        print("登录中... ")
        val state = GlobalAuthRepository.login(username, password, rememberMe = false)
        if (state is AuthState.Authenticated) {
            token = state.accessToken
            println("OK")
        } else {
            println("FAIL " + (state as? AuthState.Error)?.message)
        }
    }

    private fun startMessageListener() {
        println("CLI 模式使用纯 REST API")
    }

    private suspend fun enterReplLoop() {
        while (true) {
            print("> ")
            val line = readLine() ?: break
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue
            when {
                trimmed.startsWith("/ai ") -> handleAi(trimmed.removePrefix("/ai ").trim())
                trimmed == "/friend list" -> handleFriendList()
                trimmed.startsWith("/friend add ") -> handleAddFriend(trimmed.removePrefix("/friend add ").trim())
                trimmed.startsWith("/friend remove ") -> handleRemoveFriend(trimmed.removePrefix("/friend remove ").trim())
                trimmed == "/friend requests" -> handleFriendRequests()
                trimmed.startsWith("/friend accept ") -> handleAcceptFriend(trimmed.removePrefix("/friend accept ").trim())
                trimmed.startsWith("/friend reject ") -> handleRejectFriend(trimmed.removePrefix("/friend reject ").trim())
                trimmed == "/group list" -> handleGroupList()
                trimmed.startsWith("/group join ") -> handleJoinGroup(trimmed.removePrefix("/group join ").trim())
                trimmed.startsWith("/group leave ") -> handleLeaveGroup(trimmed.removePrefix("/group leave ").trim())
                trimmed == "/group requests" -> handleGroupRequests()
                trimmed.startsWith("/group accept ") -> handleAcceptGroup(trimmed.removePrefix("/group accept ").trim())
                trimmed.startsWith("/group reject ") -> handleRejectGroup(trimmed.removePrefix("/group reject ").trim())
                trimmed == "/help" -> printHelp()
                trimmed == "/exit" || trimmed == "/quit" -> break
                else -> handleAi(trimmed)
            }
        }
    }

    private suspend fun handleAi(content: String) {
        print("AI 思考中... ")
        val resp = agentChat(token, content, stream = false)
        println("\r$resp")
    }

    private suspend fun handleFriendList() {
        val friends = GlobalChatRepository.fetchFriends()
        println("好友列表 (共 ${friends.size}):")
        friends.forEachIndexed { idx, u ->
            println("  ${idx + 1}. id=${u.id} name=${u.nickname}")
        }
    }

    private suspend fun handleAddFriend(account: String) {
        print("添加好友 $account ... ")
        val ok = GlobalChatRepository.addFriend(token, account)
        if (ok) println("OK") else println("FAIL")
    }

    private suspend fun handleRemoveFriend(idStr: String) {
        print("删除好友 $idStr ... 注意: 后端暂留接口占位实现")
        println(" OK")
    }

    private suspend fun handleFriendRequests() {
        val reqs = GlobalChatRepository.fetchFriendRequests()
        println("好友申请列表 (共 ${reqs.size}):")
        reqs.forEachIndexed { idx, r ->
            println("  ${idx + 1}. reqId=${r.id} from=${r.senderName} msg=${r.message} status=${r.status}")
        }
    }

    private suspend fun handleAcceptFriend(reqIdStr: String) {
        val rid = reqIdStr.toIntOrNull() ?: return println("无效的申请ID")
        print("同意好友申请 $rid ... ")
        val ok = GlobalChatRepository.acceptFriend(token, rid.toString())
        println(if (ok) "OK" else "FAIL")
    }

    private suspend fun handleRejectFriend(reqIdStr: String) {
        val rid = reqIdStr.toIntOrNull() ?: return println("无效的申请ID")
        print("拒绝好友申请 $rid ... ")
        val ok = GlobalChatRepository.rejectFriend(token, rid.toString())
        println(if (ok) "OK" else "FAIL")
    }

    private suspend fun handleGroupList() {
        val groups = GlobalChatRepository.fetchGroups()
        println("群组列表 (共 ${groups.size}):")
        groups.forEachIndexed { idx, u ->
            println("  ${idx + 1}. id=${u.id} name=${u.nickname}")
        }
    }

    private suspend fun handleJoinGroup(groupIdStr: String) {
        print("加入群组 $groupIdStr ... ")
        val ok = GlobalChatRepository.addGroup(token, groupIdStr)
        println(if (ok) "OK" else "FAIL")
    }

    private suspend fun handleLeaveGroup(groupIdStr: String) {
        val gid = groupIdStr.toIntOrNull() ?: return println("无效的群组ID")
        print("退出群组 $gid ... 注意: 后端暂留接口占位实现")
        println(" OK")
    }

    private suspend fun handleGroupRequests() {
        val reqs = GlobalChatRepository.fetchGroupRequests()
        println("群聊申请列表 (共 ${reqs.size}):")
        reqs.forEachIndexed { idx, r ->
            println("  ${idx + 1}. reqId=${r.id} user=${r.senderName}")
        }
    }

    private suspend fun handleAcceptGroup(pair: String) {
        val parts = pair.split(" ").map { it.trim() }
        if (parts.size != 2) return println("用法: /group accept <groupId> <userId>")
        val ok = GlobalChatRepository.acceptGroupApplication(token, parts[0], parts[1])
        print("同意群申请 ... ")
        println(if (ok) "OK" else "FAIL")
    }

    private suspend fun handleRejectGroup(pair: String) {
        val parts = pair.split(" ").map { it.trim() }
        if (parts.size != 2) return println("用法: /group reject <groupId> <userId>")
        val ok = GlobalChatRepository.rejectGroupApplication(token, parts[0], parts[1])
        print("拒绝群申请 ... ")
        println(if (ok) "OK" else "FAIL")
    }

    private fun printHelp() {
        println("""
            完整命令清单:
              /ai <text>                 发送AI助手消息
              /friend list               列出所有好友
              /friend add <account>      按账号添加好友
              /friend remove <id>        删除好友
              /friend requests           查看收到的好友申请
              /friend accept <reqId>      同意好友申请
              /friend reject <reqId>     拒绝好友申请
              /group list                列出所有已加入群组
              /group join <groupId>      加入指定群组
              /group leave <groupId>     退出指定群组
              /group requests            查看收到的群聊申请
              /group accept <gid> <uid>  同意群聊申请
              /group reject <gid> <uid>  拒绝群聊申请
              /help                      显示本帮助
              /exit /quit                退出CLI客户端
        """.trimIndent())
    }
}
