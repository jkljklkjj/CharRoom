package core

// 本文件由 ApiClient.kt 拆分而来：AI Agent API。
// 共享基础设施（httpClient / sendRequest / json / ApiResponse 等）见 ApiClient.kt。

import core.model.AppVersionInfo
import core.model.VersionCheckResult
import core.state.GlobalAppState
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import model.Group
import model.Message
import model.MessageType
import model.User

suspend fun agentChat(token: String, message: String, stream: Boolean = false): String {
    val requestBody = buildJsonObject {
        put("message", message)
        put("stream", stream)
    }

    return try {
        val response = httpClient.request(ApiEndpoints.url(ApiEndpoints.AGENT_NL)) {
            method = HttpMethod.Post
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(requestBody)
            timeout {
                requestTimeoutMillis = 30000
            }
        }
        response.bodyAsText()
    } catch (e: Exception) {
        ""
    }
}


