package com.chatlite.charroom

import core.ApiEndpoints
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class NetworkRepository private constructor() {
    // 单例实现和常量定义合并到同一个companion object
    companion object {
        @Volatile
        private var INSTANCE: NetworkRepository? = null

        // API路径常量
        private const val LOGIN_PATH = ApiEndpoints.LOGIN
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

    suspend fun login(account: String, password: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("account", account)
            put("password", password)
        }
        val response = sendRequest(LOGIN_PATH, "POST", body.toString(), null)
        parseStringData(response)
    }

    suspend fun register(username: String, password: String): Int = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("username", username)
            put("password", password)
        }
        val response = sendRequest(REGISTER_PATH, "POST", body.toString(), null)
        parseIntData(response) ?: -1
    }

    suspend fun validateToken(token: String): Boolean = withContext(Dispatchers.IO) {
        val response = sendRequest(VALIDATE_TOKEN_PATH, "GET", null, token)
        interpretBoolean(response)
    }

    suspend fun fetchFriendAndGroupList(token: String): List<LocalUser> = withContext(Dispatchers.IO) {
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

    suspend fun getUserDetail(userId: String, token: String): LocalUser? = withContext(Dispatchers.IO) {
        val response = sendRequest("$USER_DETAIL_PATH?id=$userId", "GET", null, token)
        parseUser(response)
    }

    suspend fun getGroupDetail(groupId: String, token: String): LocalUser? = withContext(Dispatchers.IO) {
        val response = sendRequest("$GROUP_DETAIL_PATH?id=$groupId", "GET", null, token)
        parseUser(response)
    }

    suspend fun getOfflineMessages(token: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        val response = sendRequest(OFFLINE_MESSAGE_PATH, "GET", null, token)
        parseChatMessages(response)
    }

    suspend fun connectWebSocket(
        token: String,
        ownUserId: Int,
        onMessage: (ChatMessage) -> Unit,
        onStatusUpdate: (clientId: String, online: Boolean) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val success = wsClient.connect(token, ownUserId, onMessage, onStatusUpdate)
        if (success) {
            isConnected = true
            saveConnectionInfo(token, ownUserId, onMessage, onStatusUpdate)
        }
        success
    }

    fun sendChatMessage(targetId: Int, content: String, senderId: Int = 0): Boolean {
        return wsClient.sendChatText(targetId, content, senderId)
    }

    fun sendAgentMessage(targetId: Int, content: String, senderId: Int = 0): Boolean {
        return wsClient.sendAgentText(targetId, content, senderId)
    }

    fun sendGroupMessage(targetId: Int, content: String, senderId: Int = 0): Boolean {
        return wsClient.sendGroupText(targetId, content, senderId)
    }

    fun sendCheck(targetId: Int): Boolean {
        return wsClient.sendCheck(targetId)
    }

    fun sendLogout(userId: String): Boolean {
        return wsClient.sendLogout(userId)
    }

    fun disconnectWebSocket() {
        wsClient.disconnect()
        isConnected = false
    }

    private fun fetchList(path: String, token: String, method: String = "GET"): List<LocalUser> {
        return try {
            val response = sendRequest(path, method, if (method == "POST") "{}" else null, token)
            parseUserList(response)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fetchGroups(path: String, token: String): List<LocalUser> {
        return try {
            val response = sendRequest(path, "GET", null, token)
            parseGroupList(response)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseStringData(response: String): String {
        return try {
            val root = JSONObject(response)
            if (root.optInt("code", -1) != 0) return ""
            root.optString("data", "")
        } catch (e: Exception) {
            ""
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

    private fun parseUserList(response: String): List<LocalUser> {
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
                    add(LocalUser(id, username, online))
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseGroupList(response: String): List<LocalUser> {
        return try {
            val root = JSONObject(response)
            if (root.optInt("code", -1) != 0) return emptyList()
            val data = root.optJSONArray("data") ?: return emptyList()
            buildList {
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val id = item.optInt("id", 0)
                    val username = item.optString("name", item.optString("groupName", "群组$id"))
                    add(LocalUser(-id, username, false))
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseUser(response: String): LocalUser? {
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
                LocalUser(id, username, it.optBoolean("online", false))
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseChatMessages(response: String): List<ChatMessage> {
        return emptyList()
    }

    private fun sendRequest(path: String, method: String, body: String?, token: String?): String {
        val url = URL(ApiEndpoints.url(path))
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.requestMethod = method
        conn.setRequestProperty("Accept", "application/json")
        if (!token.isNullOrBlank()) {
            conn.setRequestProperty("Authorization", "Bearer $token")
        }
        if (!body.isNullOrBlank()) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            BufferedWriter(OutputStreamWriter(conn.outputStream, Charsets.UTF_8)).use {
                it.write(body)
                it.flush()
            }
        }
        val responseCode = conn.responseCode
        val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }

    // -------------------------- 网络状态管理 --------------------------

    private var currentToken: String? = null
    private var currentUserId: Int = 0
    private var isConnected = false
    private var onMessageCallback: ((ChatMessage) -> Unit)? = null
    private var onStatusCallback: ((clientId: String, online: Boolean) -> Unit)? = null

    /**
     * 网络恢复时自动重连
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun reconnect() {
        if (currentToken != null && currentUserId != 0 && !isConnected) {
            // 使用IO协程重连
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    connectWebSocket(
                        token = currentToken!!,
                        ownUserId = currentUserId,
                        onMessage = onMessageCallback ?: {},
                        onStatusUpdate = onStatusCallback ?: { _, _ -> }
                    )
                } catch (e: Exception) {
                    // 重连失败，稍后再试
                }
            }
        }
    }

    /**
     * 网络断开时通知
     */
    fun onNetworkDisconnected() {
        isConnected = false
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
                           onMessage: (ChatMessage) -> Unit,
                           onStatus: (clientId: String, online: Boolean) -> Unit) {
        currentToken = token
        currentUserId = userId
        onMessageCallback = onMessage
        onStatusCallback = onStatus
    }

    /**
     * 清除连接信息（登出时调用）
     */
    fun clearConnectionInfo() {
        currentToken = null
        currentUserId = 0
        onMessageCallback = null
        onStatusCallback = null
        isConnected = false
    }
}
