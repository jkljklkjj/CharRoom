package cli

import com.sun.net.httpserver.HttpServer
import core.*
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.*

class CliOfflineContractTest {
    @Test fun numericStringIdsAndMetadataSurviveCommitBeforeTheExplicitHttpReceipt() = runBlocking {
        val previous = ApiEndpoints.url("")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var committed = false
        var acknowledged = false
        server.createContext("/api/messages/offline") { exchange ->
            val payload = if (exchange.requestURI.path.endsWith("/ack")) {
                assertTrue(committed)
                assertTrue(exchange.requestBody.bufferedReader().readText().contains("\"9007199254740993\""))
                acknowledged = true
                """{"code":0,"data":null}"""
            } else if (acknowledged) """{"code":0,"data":[]}""" else
                """{"code":0,"data":[{"id":"9007199254740993","senderId":"7","receiverId":"42","message":"fixture","timestamp":123,"conversationId":"user:7:42","seqId":"10","fileName":"fixture.txt","messageType":2}]}"""
            val bytes = payload.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            ApiEndpoints.setBaseUrl("http://127.0.0.1:${server.address.port}/api")
            val count = drainOfflineHistory(42, { true }, { getOfflineHistoryPage("fixture") }, { rows ->
                assertEquals("9007199254740993", rows.single().id)
                assertEquals("fixture.txt", rows.single().fileName)
                assertFalse(acknowledged); committed = true
            }, { confirmOfflineMessages("fixture", it) })
            assertEquals(1, count); assertTrue(acknowledged)
        } finally { server.stop(0); ApiEndpoints.setBaseUrl(previous) }
    }
}
