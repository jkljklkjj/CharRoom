package core

import com.chatlite.proto.MessageProtos
import java.nio.charset.StandardCharsets
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * 解析Protobuf响应消息
 */
fun parseProtoResponse(bytes: ByteArray): ApiUnwrap {
    val mapper = jacksonObjectMapper()

    // 1) Try to parse as MessageWrapper (the common envelope you send)
    try {
        val wrapper = MessageProtos.MessageWrapper.parseFrom(bytes)
        val node = mapper.createObjectNode()
        node.put("type", wrapper.type)
        AppLog.i { "Parsed MessageWrapper with type: ${wrapper.type}" }

        when (wrapper.payloadCase) {
            MessageProtos.MessageWrapper.PayloadCase.LOGIN -> {
                val v = wrapper.login
                val payload = mapper.createObjectNode()
                payload.put("token", v.token)
                node.set<ObjectNode>("payload", payload)
            }
            MessageProtos.MessageWrapper.PayloadCase.CHAT -> {
                val v = wrapper.chat
                val payload = mapper.createObjectNode()
                payload.put("targetClientId", v.targetClientId)
                payload.put("content", v.content)
                payload.put("userId", v.userId)
                payload.put("timestamp", v.timestamp)
                node.set<ObjectNode>("payload", payload)
            }
            MessageProtos.MessageWrapper.PayloadCase.GROUPCHAT -> {
                val v = wrapper.groupChat
                val payload = mapper.createObjectNode()
                payload.put("targetClientId", v.targetClientId)
                payload.put("content", v.content)
                payload.put("userId", v.userId)
                node.set<ObjectNode>("payload", payload)
            }
            MessageProtos.MessageWrapper.PayloadCase.CHECK -> {
                val v = wrapper.check
                val payload = mapper.createObjectNode()
                payload.put("targetClientId", v.targetClientId)
                node.set<ObjectNode>("payload", payload)
            }
            MessageProtos.MessageWrapper.PayloadCase.HEARTBEAT -> {
                val v = wrapper.heartbeat
                val payload = mapper.createObjectNode()
                payload.put("timestamp", v.timestamp)
                node.set<ObjectNode>("payload", payload)
            }
            MessageProtos.MessageWrapper.PayloadCase.LOGOUT -> {
                val v = wrapper.logout
                val payload = mapper.createObjectNode()
                payload.put("userId", v.userId)
                node.set<ObjectNode>("payload", payload)
            }
            MessageProtos.MessageWrapper.PayloadCase.AGENTSTREAM -> {
                val v = wrapper.agentStream
                val payload = mapper.createObjectNode()
                payload.put("type", v.typeValue)  // 0=TEXT, 1=TOOL_CALL, 2=TOOL_RESULT, 3=USAGE, 4=DONE, 5=ERROR
                payload.put("requestId", v.requestId)
                payload.put("traceId", v.traceId)

                when (v.payloadCase) {
                    com.chatlite.proto.AgentStreamProtos.AgentStreamChunk.PayloadCase.TEXT -> {
                        payload.put("text", v.text)
                    }
                    com.chatlite.proto.AgentStreamProtos.AgentStreamChunk.PayloadCase.TOOL_CALL -> {
                        val tc = v.toolCall
                        val toolCallNode = mapper.createObjectNode()
                        toolCallNode.put("id", tc.id)
                        toolCallNode.put("name", tc.name)
                        toolCallNode.put("arguments", tc.arguments)
                        payload.set<ObjectNode>("toolCall", toolCallNode)
                    }
                    com.chatlite.proto.AgentStreamProtos.AgentStreamChunk.PayloadCase.TOOL_RESULT -> {
                        val tr = v.toolResult
                        val toolResultNode = mapper.createObjectNode()
                        toolResultNode.put("toolCallId", tr.toolCallId)
                        toolResultNode.put("result", tr.result)
                        toolResultNode.put("success", tr.success)
                        payload.set<ObjectNode>("toolResult", toolResultNode)
                    }
                    com.chatlite.proto.AgentStreamProtos.AgentStreamChunk.PayloadCase.USAGE -> {
                        val u = v.usage
                        val usageNode = mapper.createObjectNode()
                        usageNode.put("inputTokens", u.inputTokens)
                        usageNode.put("outputTokens", u.outputTokens)
                        usageNode.put("totalTokens", u.totalTokens)
                        usageNode.put("model", u.model)
                        payload.set<ObjectNode>("usage", usageNode)
                    }
                    com.chatlite.proto.AgentStreamProtos.AgentStreamChunk.PayloadCase.ERROR -> {
                        val e = v.error
                        val errorNode = mapper.createObjectNode()
                        errorNode.put("code", e.code)
                        errorNode.put("message", e.message)
                        payload.set<ObjectNode>("error", errorNode)
                    }
                    else -> {}
                }
                node.set<ObjectNode>("payload", payload)
            }
            MessageProtos.MessageWrapper.PayloadCase.PAYLOAD_NOT_SET, null -> {
                // no payload
            }
            else -> {
                // unknown payload, ignore
            }
        }

        val dataJson = mapper.writeValueAsString(node)
        return ApiUnwrap(hasEnvelope = false, success = true, dataJson = dataJson, message = null)
    } catch (_: Exception) {
        // not a MessageWrapper -> try ResponseMessage
    }

    // 2) Try to parse as ResponseMessage (ACK/response)
    try {
        val resp = MessageProtos.ResponseMessage.parseFrom(bytes)
        val success = resp.success
        val respNode = mapper.createObjectNode()
        respNode.put("message", resp.message)
        respNode.put("success", success)
        if (resp.clientId.isNotBlank()) respNode.put("clientId", resp.clientId)
        respNode.put("online", resp.online)
        val dataJsonStr = mapper.writeValueAsString(respNode)
        val message = if (!success) resp.message else null
        return ApiUnwrap(hasEnvelope = true, success = success, dataJson = dataJsonStr, message = message)
    } catch (_: Exception) {
        // fall through to plain text
    }

    // 3) fallback: treat as UTF-8 text
    return ApiUnwrap(hasEnvelope = false, success = true, dataJson = String(bytes, StandardCharsets.UTF_8), message = null)
}
