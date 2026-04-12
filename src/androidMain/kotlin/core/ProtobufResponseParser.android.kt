package core

import com.example.proto.MessageProtos
import java.nio.charset.StandardCharsets
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode

actual fun parseProtoResponse(bytes: ByteArray): ApiUnwrap {
    val mapper = jacksonObjectMapper()
    // 1) Try to parse as MessageWrapper
    try {
        val wrapper = MessageProtos.MessageWrapper.parseFrom(bytes)
        val node = mapper.createObjectNode()
        node.put("type", wrapper.type)
        when (wrapper.payloadCase) {
            MessageProtos.MessageWrapper.PayloadCase.LOGIN -> {
                val v = wrapper.login
                val payload = mapper.createObjectNode()
                payload.put("targetClientId", v.targetClientId)
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
            MessageProtos.MessageWrapper.PayloadCase.PAYLOAD_NOT_SET, null -> {
                // nothing
            }
            else -> {
                // unknown payload
            }
        }
        val dataJson = mapper.writeValueAsString(node)
        return ApiUnwrap(hasEnvelope = false, success = true, dataJson = dataJson, message = null)
    } catch (_: Exception) {
        // not a MessageWrapper, fall through
    }

    // 2) Try ResponseMessage
    try {
        val resp = MessageProtos.ResponseMessage.parseFrom(bytes)
        val success = resp.success
        val dataJson: String? = if (resp.message.isBlank()) null else resp.message
        val message = if (!success) resp.message else null
        return ApiUnwrap(hasEnvelope = true, success = success, dataJson = dataJson, message = message)
    } catch (_: Exception) {
        // fall through
    }

    // 3) Fallback to UTF-8 string
    return ApiUnwrap(hasEnvelope = false, success = true, dataJson = String(bytes, StandardCharsets.UTF_8), message = null)
}

