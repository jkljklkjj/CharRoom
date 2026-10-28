package cli

import com.sun.net.httpserver.HttpServer
import core.ApiEndpoints
import core.agentChat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.net.InetSocketAddress
import kotlin.test.*

class CliAgentContractTest {
    @Test fun assistantUsesInputContractAndDisplaysOnlyResponseData() = runBlocking {
        val previous = ApiEndpoints.url("")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var body = ""
        var auth = ""
        server.createContext("/api/agent/nl") { exchange ->
            body = exchange.requestBody.bufferedReader().readText()
            auth = exchange.requestHeaders.getFirst("Authorization")
            val response = """{"code":0,"data":"fixture reply"}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            ApiEndpoints.setBaseUrl("http://127.0.0.1:${server.address.port}/api")
            assertEquals("fixture reply", agentChat("fixture", "hello"))
            val request = Json.parseToJsonElement(body).jsonObject
            assertEquals("hello", request["input"]?.jsonPrimitive?.content)
            assertFalse("message" in request)
            assertEquals("Bearer fixture", auth)
        } finally { server.stop(0); ApiEndpoints.setBaseUrl(previous) }
    }
}
