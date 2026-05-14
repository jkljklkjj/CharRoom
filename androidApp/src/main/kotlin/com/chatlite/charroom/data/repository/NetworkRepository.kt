package com.chatlite.charroom.data.repository

import model.Message
import model.User
import com.chatlite.charroom.data.network.AndroidWebSocketClient
import core.ApiEndpoints
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import org.json.JSONObject
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.util.concurrent.TimeUnit

class NetworkRepository private constructor() {
    // 单例实现和常量定义合并到同一个companion object
    companion object {
        @Volatile
        private var INSTANCE: NetworkRepository? = null

        // API路径常量
        private const val LOGIN_PATH = ApiEndpoints.LOGIN
        private const val REFRESH_TOKEN_PATH = ApiEndpoints.REFRESH_TOKEN
        private const val REGISTER_PATH = ApiEndpoints.REGISTER
        private const val VALIDATE_TOKEN_PATH = ApiEndpoints.VALIDATE_TOKEN
        private const val FRIEND_GET_PATH = ApiEndpoints.FRIEND_GET
        private const val GROUP_GET_PATH = ApiEndpoints.GROUP_GET
        private const val FRIEND_ADD_PATH = ApiEndpoints.FRIEND_ADD
        private const val GROUP_ADD_PATH = ApiEndpoints.GROUP_ADD
        private const val USER_DETAIL_PATH = ApiEndpoints.USER_DETAIL
        private const val GROUP_DETAIL_PATH = ApiEndpoints.GROUP_DETAIL
        private const val OFFLINE_MESSAGE_PATH = ApiEndpoints.OFFLINE

        fun getInstance(): NetworkRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkRepository().also { INSTANCE = it }
            }
        }

        /**
         * 初始化方法，推荐在Application中调用
         */
        fun init(): NetworkRepository {
            return getInstance()
        }
    }

    private val wsClient = AndroidWebSocketClient()
    private var scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 预配置的 OkHttpClient：启用连接池与 HTTP/2 优先
    private val preconfiguredOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val httpClient = HttpClient(OkHttp) {
        engine {
            preconfigured = preconfiguredOkHttpClient
        }
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
            requestTimeoutMillis = 10_000
        }
    }

    data class TokenBundle(
        val accessToken: String,
        val refreshToken: String
    )

    suspend fun login(account: String, password: String): TokenBundle? = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("account", account)
            put("password", password)
        }
        val response = sendRequest(LOGIN_PATH, "POST", body.toString(), null)
        parseLoginTokenBundle(response)
    }

    suspend fun refreshAccessToken(refreshToken: String): TokenBundle? = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("refreshToken", refreshToken)
        }
        val response = sendRequest(REFRESH_TOKEN_PATH, "POST", body.toString(), null)
        parseLoginTokenBundle(response)
    }

    suspend fun register(username: String, password: String): Int = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("username", username)
            put("password", password)
        }
        val response = sendRequest(REGISTER_PATH, "POST", body.toString(), null)
        parseIntData(response) ?: -1
    }

    suspend fun validateToken(token: String): TokenBundle? = withContext(Dispatchers.IO) {
        val response = sendRequest(VALIDATE_TOKEN_PATH, "GET", null, token)
        parseLoginTokenBundle(response)
    }

    suspend fun fetchFriendAndGroupList(token: String): List<User> = withContext(Dispatchers.IO) {
        val friends = fetchList(FRIEND_GET_PATH, token, "POST")
        val groups = fetchGroups(GROUP_GET_PATH, token)
        (friends + groups).distinctBy { it.id }
    }

    suspend fun addFriend(account: String, token: String): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().apply { put("account", account) }
        val response = sendRequest(FRIEND_ADD_PATH, "POST", body.toString(), token)
        interpretBoolean(response)
    }

    suspend fun addGroup(groupId: String, token: String): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().apply { put("groupId", groupId) }
        val response = sendRequest(GROUP_ADD_PATH, "POST", body.toString(), token)
        interpretBoolean(response)
    }

    suspend fun getUserDetail(userId: String, token: String): User? = withContext(Dispatchers.IO) {
        val response = sendRequest("$USER_DETAIL_PATH?id=$userId", "GET", null, token)
        parseUser(response)
    }

    suspend fun updateUserProfile(userId: String, token: String, username: String, phone: String, signature: String?): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("username", username)
            put("phone", phone)
            put("signature", signature ?: "")
        }
        val response = sendRequest(ApiEndpoints.USER_PROFILE_UPDATE, "POST", body.toString(), token)
        interpretBoolean(response)
    }

    suspend fun uploadAvatar(token: String, imageBytes: ByteArray, fileName: String): String? = withContext(Dispatchers.IO) {
        val response = httpClient.post(ApiEndpoints.url(ApiEndpoints.USER_AVATAR_UPLOAD)) {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            key = "file",
                            value = imageBytes,
                            headers = Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            }
                        )
                    }
                )
            )
        }.bodyAsText()

        return@withContext try {
            val root = JSONObject(response)
            if (root.optInt("code", -1) == 0) {
                root.optString("data")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getGroupDetail(groupId: String, token: String): User? = withContext(Dispatchers.IO) {
        val response = sendRequest("$GROUP_DETAIL_PATH?id=$groupId", "GET", null, token)
        parseUser(response)
    }

    suspend fun getOfflineMessages(token: String): List<Message> = withContext(Dispatchers.IO) {
        val response = sendRequest(OFFLINE_MESSAGE_PATH, "GET", null, token)
        parseMessages(response)
    }

    /**
     * 验证指定 URL 使用的底层协议（OKHTTP 可返回 HTTP/2 或 HTTP/1.1）
     * 用于排查服务器是否启用了 HTTP/2
     */
    suspend fun detectHttpProtocol(url: String): String = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .head()
                .build()
            preconfiguredOkHttpClient.newCall(req).execute().use { resp ->
                return@withContext resp.protocol.toString()
            }
        } catch (e: Exception) {
            return@withContext "UNKNOWN: ${e.message}"
        }
    }

    suspend fun connectWebSocket(
        token: String,
        ownUserId: Int,
        onMessage: (Message) -> Unit,
        onStatusUpdate: (clientId: String, online: Boolean) -> Unit,
        onAuthFailed: ((reason: String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val connectionListener = object : AndroidWebSocketClient.ConnectionListener {
            override fun onConnected() {
                isConnected = true
            }

            override fun onDisconnected() {
                isConnected = false
                // 非用户主动断开时自动重连
                if (currentToken != null && currentUserId != 0) {
                    Timber.i("WebSocket意外断开，启动自动重连")
                    reconnect()
                }
            }

            override fun onAuthFailed(reason: String) {
                isConnected = false
                onAuthFailed?.invoke(reason)
            }
        }

        val success = wsClient.connect(token, ownUserId, onMessage, onStatusUpdate, onAuthFailed, connectionListener)
        if (success) {
            saveConnectionInfo(token, ownUserId, onMessage, onStatusUpdate, onAuthFailed)
        }
        success
    }

    fun sendMessage(targetId: Int, content: String, senderId: Int = 0): Boolean {
        if (!isConnected) {
            ensureConnected()
        }
        return wsClient.sendChatText(targetId, content, senderId)
    }

    fun sendAgentMessage(targetId: Int, content: String, senderId: Int = 0): Boolean {
        if (!isConnected) {
            ensureConnected()
        }
        return wsClient.sendAgentText(targetId, content, senderId)
    }

    fun sendGroupMessage(targetId: Int, content: String, senderId: Int = 0): Boolean {
        if (!isConnected) {
            ensureConnected()
        }
        return wsClient.sendGroupText(targetId, content, senderId)
    }

    fun sendCheck(targetId: Int): Boolean {
        if (!isConnected) {
            ensureConnected()
        }
        return wsClient.sendCheck(targetId)
    }

    fun sendLogout(userId: String): Boolean {
        return wsClient.sendLogout(userId)
    }

    fun sendHeartbeat(): Boolean {
        return wsClient.sendHeartbeat()
    }

    fun disconnectWebSocket() {
        reconnectJob?.cancel() // 取消重连任务
        reconnectAttempts = 0 // 重置重连次数
        wsClient.disconnect()
        isConnected = false
    }

    private suspend fun fetchList(path: String, token: String, method: String = "GET"): List<User> {
        return try {
            val response = sendRequest(path, method, if (method == "POST") "{}" else null, token)
            parseUserList(response)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchGroups(path: String, token: String): List<User> {
        return try {
            val response = sendRequest(path, "GET", null, token)
            parseGroupList(response)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseLoginTokenBundle(response: String): TokenBundle? {
        return try {
            val root = JSONObject(response)
            if (root.optInt("code", -1) != 0) return null
            val data = root.opt("data")
            when (data) {
                is JSONObject -> {
                    val accessToken = data.optString("accessToken", "")
                    if (accessToken.isBlank()) {
                        null
                    } else {
                        TokenBundle(
                            accessToken = accessToken,
                            refreshToken = data.optString("refreshToken", "")
                        )
                    }
                }
                is String -> {
                    if (data.isBlank()) null else TokenBundle(accessToken = data, refreshToken = "")
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseIntData(response: String): Int? {
        return try {
            val root = JSONObject(response)
            if (root.optInt("code", -1) != 0) return null
            root.optInt("data", -1).takeIf { it >= 0 }
        } catch (e: Exception) {
            null
        }
    }

    private fun interpretBoolean(response: String): Boolean {
        return try {
            val root = JSONObject(response)
            val code = root.optInt("code", -1)
            if (code == 0) {
                if (root.has("data")) root.optBoolean("data", true) else true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun parseUserList(response: String): List<User> {
        return try {
            val root = JSONObject(response)
            if (root.optInt("code", -1) != 0) return emptyList()
            val data = root.optJSONArray("data") ?: return emptyList()
            buildList {
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val id = item.optInt("id", 0)
                    val username = item.optString("username", item.optString("name", "用户$id"))
                    val online = item.optBoolean("online", false)
                    val avatarUrl = item.optString("avatarUrl").takeIf { it.isNotBlank() && it != "null" }
                        ?: item.optString("avatar").takeIf { it.isNotBlank() && it != "null" }
                    val signature = item.optString("signature").takeIf { it.isNotBlank() && it != "null" }
                    val email = item.optString("email").takeIf { it.isNotBlank() && it != "null" }
                    val phone = item.optString("phone").takeIf { it.isNotBlank() && it != "null" }
                    add(
                        User(
                            id = id,
                            username = username,
                            email = email,
                            phone = phone,
                            signature = signature,
                            avatarUrl = avatarUrl,
                            online = online
                        )
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseGroupList(response: String): List<User> {
        return try {
            val root = JSONObject(response)
            if (root.optInt("code", -1) != 0) return emptyList()
            val data = root.optJSONArray("data") ?: return emptyList()
            buildList {
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val id = item.optInt("id", 0)
                    val username = item.optString("name", item.optString("groupName", "群组$id"))
                    val avatarUrl = item.optString("avatarUrl").takeIf { it.isNotBlank() && it != "null" }
                        ?: item.optString("avatar").takeIf { it.isNotBlank() && it != "null" }
                    // 群组用负ID存储，后续在AndroidRemoteDataSource中转换为Group对象
                    add(
                        User(
                            id = -id, // 群组ID用负值表示
                            username = username,
                            avatarUrl = avatarUrl,
                            online = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseUser(response: String): User? {
        return try {
            val root = JSONObject(response)
            val obj = if (root.has("code")) {
                if (root.optInt("code", -1) != 0) return null
                root.optJSONObject("data")
            } else {
                root
            }
            obj?.let {
                val id = it.optInt("id", 0)
                val username = it.optString("username", it.optString("name", "用户$id"))
                val avatarUrl = it.optString("avatarUrl").takeIf { it.isNotBlank() && it != "null" }
                    ?: it.optString("avatar").takeIf { it.isNotBlank() && it != "null" }
                val signature = it.optString("signature").takeIf { it.isNotBlank() && it != "null" }
                val email = it.optString("email").takeIf { it.isNotBlank() && it != "null" }
                val phone = it.optString("phone").takeIf { it.isNotBlank() && it != "null" }
                User(
                    id = id,
                    username = username,
                    email = email,
                    phone = phone,
                    signature = signature,
                    avatarUrl = avatarUrl,
                    online = it.optBoolean("online", false)
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseMessages(response: String): List<Message> {
        return emptyList()
    }

    private suspend fun sendRequest(path: String, method: String, body: String?, token: String?): String {
        return httpClient.request(ApiEndpoints.url(path)) {
            this.method = HttpMethod.parse(method)
            accept(ContentType.Application.Json)
            if (!token.isNullOrBlank()) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (!body.isNullOrBlank()) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }.bodyAsText()
    }

    // -------------------------- 网络状态管理 --------------------------

    private var currentToken: String? = null
    private var currentUserId: Int = 0
    private var isConnected = false
    private var onMessageCallback: ((Message) -> Unit)? = null
    private var onStatusCallback: ((clientId: String, online: Boolean) -> Unit)? = null
    private var onAuthFailedCallback: ((reason: String) -> Unit)? = null

    // 重连配置
    private val maxReconnectAttempts = 5 // 最大重连次数
    private val initialReconnectDelay = 1000L // 初始重连延迟1秒
    private val maxReconnectDelay = 30000L // 最大重连延迟30秒
    private var reconnectAttempts = 0 // 当前重连尝试次数
    private var reconnectJob: kotlinx.coroutines.Job? = null // 重连任务

    /**
     * 网络恢复时自动重连（带指数退避）
     */
    fun reconnect() {
        if (currentToken == null || currentUserId == 0 || isConnected) {
            return
        }

        // 取消之前的重连任务
        reconnectJob?.cancel()

        reconnectJob = scope.launch {
            while (reconnectAttempts < maxReconnectAttempts && !isConnected) {
                try {
                    Timber.i("尝试重连WebSocket，第 ${reconnectAttempts + 1}/$maxReconnectAttempts 次")

                    val success = connectWebSocket(
                        token = currentToken!!,
                        ownUserId = currentUserId,
                        onMessage = onMessageCallback ?: {},
                        onStatusUpdate = onStatusCallback ?: { _, _ -> },
                        onAuthFailed = onAuthFailedCallback
                    )

                    if (success) {
                        Timber.i("WebSocket重连成功")
                        reconnectAttempts = 0 // 重连成功，重置重试次数
                        return@launch
                    } else {
                        Timber.w("WebSocket重连失败，等待重试")
                        reconnectAttempts++
                        val delay = minOf(initialReconnectDelay * (1 shl reconnectAttempts), maxReconnectDelay)
                        delay(delay)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "WebSocket重连异常")
                    reconnectAttempts++
                    val delay = minOf(initialReconnectDelay * (1 shl reconnectAttempts), maxReconnectDelay)
                    delay(delay)
                }
            }

            if (reconnectAttempts >= maxReconnectAttempts) {
                Timber.e("WebSocket重连次数超过上限，停止重连")
                // 可以通知上层重连失败
                onAuthFailedCallback?.invoke("网络连接失败，请检查网络后重试")
            }
        }
    }

    /**
     * 网络断开时通知
     */
    fun onNetworkDisconnected() {
        isConnected = false
        reconnectAttempts = 0 // 重置重连次数
        reconnectJob?.cancel() // 取消正在进行的重连任务
        // 可以通知UI层网络已断开
    }

    /**
     * 确保WebSocket连接正常
     */
    fun ensureConnected() {
        if (!isConnected) {
            reconnect()
        }
    }

    /**
     * 保存连接信息，用于自动重连
     */
    fun saveConnectionInfo(token: String, userId: Int,
                           onMessage: (Message) -> Unit,
                           onStatus: (clientId: String, online: Boolean) -> Unit,
                           onAuthFailed: ((reason: String) -> Unit)? = null) {
        currentToken = token
        currentUserId = userId
        onMessageCallback = onMessage
        onStatusCallback = onStatus
        onAuthFailedCallback = onAuthFailed
    }

    /**
     * 应用完全退出时调用：发送登出消息后断开连接，保留登录凭证
     */
    suspend fun onAppQuit() {
        timber.log.Timber.i("应用完全退出，执行退出清理流程")
        if (isConnected && currentUserId != 0) {
            timber.log.Timber.i("正在发送登出消息通知服务器，用户ID: $currentUserId")
            // 先发送登出消息通知服务器
            val sent = sendLogout(currentUserId.toString())
            if (sent) {
                timber.log.Timber.i("登出消息发送成功")
            } else {
                timber.log.Timber.w("登出消息发送失败")
            }
            // 短暂延迟确保消息发送完成
            kotlinx.coroutines.delay(100)
        } else {
            timber.log.Timber.i("未连接或未登录，无需发送登出消息")
        }
        timber.log.Timber.i("断开WebSocket连接")
        disconnectWebSocket()
        timber.log.Timber.i("应用退出清理完成")
    }

    /**
     * 清除连接信息（登出时调用）
     */
    fun clearConnectionInfo() {
        reconnectJob?.cancel() // 取消重连任务
        reconnectAttempts = 0 // 重置重连次数
        currentToken = null
        currentUserId = 0
        onMessageCallback = null
        onStatusCallback = null
        onAuthFailedCallback = null
        isConnected = false
    }
}
