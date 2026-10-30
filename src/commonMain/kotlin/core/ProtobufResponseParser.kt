package core

import com.chatlite.proto.AgentStreamProtos.AgentStreamChunk
import com.chatlite.proto.MessageProtos
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.protobuf.InvalidProtocolBufferException
import java.nio.charset.StandardCharsets

/** Wrapper, response and plain-text fallback preserve the existing compatibility order. */
fun parseProtoResponse(bytes: ByteArray): ApiUnwrap =
    ProtobufResponseParser.wrapper(bytes)
        ?: ProtobufResponseParser.response(bytes)
        ?: ApiUnwrap(false, true, String(bytes, StandardCharsets.UTF_8), null)

private object ProtobufResponseParser {
    private val mapper = jacksonObjectMapper()

    fun wrapper(bytes: ByteArray): ApiUnwrap? =
        try {
            val wrapper = MessageProtos.MessageWrapper.parseFrom(bytes)
            val node = mapper.createObjectNode().put("type", wrapper.type.name)
            AppLog.i { "Parsed MessageWrapper with type: ${wrapper.type.name}" }
            payload(wrapper)?.let { node.set<ObjectNode>("payload", it) }
            ApiUnwrap(false, true, mapper.writeValueAsString(node), null)
        } catch (_: InvalidProtocolBufferException) {
            null
        } catch (_: JsonProcessingException) {
            null
        }

    fun response(bytes: ByteArray): ApiUnwrap? =
        try {
            val response = MessageProtos.ResponseMessage.parseFrom(bytes)
            val node =
                mapper
                    .createObjectNode()
                    .put("message", response.message)
                    .put("success", response.success)
                    .put("online", response.online)
            if (response.clientId.isNotBlank()) node.put("clientId", response.clientId)
            ApiUnwrap(
                true,
                response.success,
                mapper.writeValueAsString(node),
                if (response.success) null else response.message,
            )
        } catch (_: InvalidProtocolBufferException) {
            null
        } catch (_: JsonProcessingException) {
            null
        }

    private fun payload(wrapper: MessageProtos.MessageWrapper): ObjectNode? =
        when (wrapper.payloadCase) {
            MessageProtos.MessageWrapper.PayloadCase.LOGIN -> login(wrapper.login)
            MessageProtos.MessageWrapper.PayloadCase.CHAT -> chat(wrapper.chat)
            MessageProtos.MessageWrapper.PayloadCase.GROUPCHAT -> group(wrapper.groupChat)
            MessageProtos.MessageWrapper.PayloadCase.CHECK ->
                mapper.createObjectNode().put("targetClientId", wrapper.check.targetClientId)
            MessageProtos.MessageWrapper.PayloadCase.HEARTBEAT ->
                mapper.createObjectNode().put("timestamp", wrapper.heartbeat.timestamp)
            MessageProtos.MessageWrapper.PayloadCase.LOGOUT ->
                mapper.createObjectNode().put("userId", wrapper.logout.userId)
            MessageProtos.MessageWrapper.PayloadCase.AGENTSTREAM -> agent(wrapper.agentStream)
            else -> null
        }

    private fun login(value: MessageProtos.LoginMessage) =
        mapper.createObjectNode().put("token", value.token)

    private fun chat(value: MessageProtos.ChatMessage) =
        mapper
            .createObjectNode()
            .put("targetClientId", value.targetClientId)
            .put("content", value.content)
            .put("userId", value.userId)
            .put("timestamp", value.timestamp)

    private fun group(value: MessageProtos.GroupChatMessage) =
        mapper
            .createObjectNode()
            .put("targetClientId", value.targetClientId)
            .put("content", value.content)
            .put("userId", value.userId)

    private fun agent(value: AgentStreamChunk): ObjectNode {
        val payload =
            mapper
                .createObjectNode()
                .put("type", value.typeValue)
                .put("requestId", value.requestId)
                .put("traceId", value.traceId)
        when (value.payloadCase) {
            AgentStreamChunk.PayloadCase.TEXT -> payload.put("text", value.text)
            AgentStreamChunk.PayloadCase.TOOL_CALL ->
                payload.set<ObjectNode>("toolCall", toolCall(value))
            AgentStreamChunk.PayloadCase.TOOL_RESULT ->
                payload.set<ObjectNode>("toolResult", toolResult(value))
            AgentStreamChunk.PayloadCase.USAGE -> payload.set<ObjectNode>("usage", usage(value))
            AgentStreamChunk.PayloadCase.ERROR ->
                payload.set<ObjectNode>(
                    "error",
                    mapper
                        .createObjectNode()
                        .put("code", value.error.code)
                        .put("message", value.error.message),
                )
            else -> Unit
        }
        return payload
    }

    private fun toolCall(value: AgentStreamChunk) =
        mapper
            .createObjectNode()
            .put("id", value.toolCall.id)
            .put("name", value.toolCall.name)
            .put("arguments", value.toolCall.arguments)

    private fun toolResult(value: AgentStreamChunk) =
        mapper
            .createObjectNode()
            .put("toolCallId", value.toolResult.toolCallId)
            .put("result", value.toolResult.result)
            .put("success", value.toolResult.success)

    private fun usage(value: AgentStreamChunk) =
        mapper
            .createObjectNode()
            .put("inputTokens", value.usage.inputTokens)
            .put("outputTokens", value.usage.outputTokens)
            .put("totalTokens", value.usage.totalTokens)
            .put("model", value.usage.model)
}
