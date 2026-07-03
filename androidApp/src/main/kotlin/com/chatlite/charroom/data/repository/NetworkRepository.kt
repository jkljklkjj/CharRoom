package com.chatlite.charroom.data.repository

import model.Message
import model.User
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

}
