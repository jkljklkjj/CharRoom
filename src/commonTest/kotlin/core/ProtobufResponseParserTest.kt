package core

import com.chatlite.proto.AgentStreamProtos
import com.chatlite.proto.MessageProtos
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProtobufResponseParserTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun privatePayloadKeepsNumericIdsTimestampAndEscapedContent() {
        val chat =
            MessageProtos.ChatMessage.newBuilder()
                .setTargetClientId(42)
                .setUserId(7)
                .setTimestamp(1800000000123)
                .setContent("fixture\n\"quoted\"")
                .build()
        val result =
            parseProtoResponse(
                MessageProtos.MessageWrapper.newBuilder()
                    .setType(MsgType.CHAT)
                    .setChat(chat)
                    .build()
                    .toByteArray()
            )
        val node = mapper.readTree(result.dataJson)
        assertFalse(result.hasEnvelope)
        assertTrue(result.success)
        assertEquals("CHAT", node["type"].asText())
        assertEquals(42L, node["payload"]["targetClientId"].asLong())
        assertEquals(7L, node["payload"]["userId"].asLong())
        assertEquals(1800000000123L, node["payload"]["timestamp"].asLong())
        assertEquals(chat.content, node["payload"]["content"].asText())
    }

    @Test
    fun agentToolResultKeepsNestedDataAndRequestIdentity() {
        val chunk =
            AgentStreamProtos.AgentStreamChunk.newBuilder()
                .setRequestId(1234567890123)
                .setTraceId("fixture-trace")
                .setType(AgentStreamProtos.AgentStreamType.STREAM_TOOL_RESULT)
                .setToolResult(
                    AgentStreamProtos.ToolResult.newBuilder()
                        .setToolCallId("fixture-call")
                        .setResult("{\"ok\":true}")
                        .setSuccess(true)
                )
        val result =
            parseProtoResponse(
                MessageProtos.MessageWrapper.newBuilder()
                    .setType(MsgType.AGENT_CHAT_STREAM)
                    .setAgentStream(chunk)
                    .build()
                    .toByteArray()
            )
        val payload = mapper.readTree(result.dataJson)["payload"]
        assertEquals(1234567890123L, payload["requestId"].asLong())
        assertEquals("fixture-trace", payload["traceId"].asText())
        assertEquals("fixture-call", payload["toolResult"]["toolCallId"].asText())
        assertEquals("{\"ok\":true}", payload["toolResult"]["result"].asText())
        assertTrue(payload["toolResult"]["success"].asBoolean())
    }

    @Test
    fun legacyResponseFallbackRetainsFailureAndClientIdentity() {
        val response =
            MessageProtos.ResponseMessage.newBuilder()
                .setMessage("fixture-error")
                .setSuccess(false)
                .setClientId("fixture-client")
                .setOnline(true)
                .build()
        val result = parseProtoResponse(response.toByteArray())
        assertTrue(result.hasEnvelope)
        assertFalse(result.success)
        assertEquals("fixture-error", result.message)
        val node = mapper.readTree(result.dataJson)
        assertEquals("fixture-client", node["clientId"].asText())
        assertTrue(node["online"].asBoolean())
    }

    @Test
    fun nonProtobufTextRemainsUtf8AndDoesNotBecomeAnEnvelope() {
        val text = "fixture: 中文 \n malformed protobuf"
        val result = parseProtoResponse(text.toByteArray(Charsets.UTF_8))
        assertFalse(result.hasEnvelope)
        assertTrue(result.success)
        assertNull(result.message)
        assertEquals(text, result.dataJson)
    }
}
