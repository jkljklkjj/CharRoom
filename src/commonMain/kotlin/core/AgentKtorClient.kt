package core

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.call.*
import io.ktor.utils.io.*
import io.ktor.http.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

// Minimal Ktor client instance for commonMain streaming; platform engines are provided by gradle dependencies
private val ktorClient: HttpClient by lazy {
    HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        // keep default config small; engines are selected per-platform by Gradle
    }
}

suspend fun callAgentStreamKtor(
    input: String,
    token: String = ServerConfig.Token,
    onToken: ((String) -> Unit)? = null
): String = withContext(Dispatchers.IO) {
    val collected = StringBuilder()
    try {
        val bodyJson = Json.encodeToString(core.AgentRequestBody.serializer(), core.AgentRequestBody(input, null))
        val resp: HttpResponse = ktorClient.request {
            url(ApiEndpoints.url(ApiEndpoints.AGENT_NL_STREAM))
            method = HttpMethod.Post
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header(HttpHeaders.Accept, "text/event-stream")
            if (token.isNotEmpty()) header(HttpHeaders.Authorization, "Bearer $token")
            setBody(TextContent(bodyJson, ContentType.Application.Json))
            timeout { requestTimeoutMillis = 120_000 }
        }

        val channel: ByteReadChannel = resp.bodyAsChannel()
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            if (line.startsWith("event:")) {
                // ignore event type for now
            } else if (line.startsWith("data:")) {
                val data = line.removePrefix("data:").trimStart()
                if (data == "[DONE]") break
                collected.append(data)
                onToken?.invoke(data)
            } else if (line.isBlank()) {
                // event boundary
            }
        }
    } catch (e: Exception) {
        println("callAgentStreamKtor error: ${e.message}")
    }
    collected.toString()
}

