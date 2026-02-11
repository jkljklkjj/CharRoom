package core

import com.example.proto.MessageProtos
import java.nio.charset.StandardCharsets

actual fun parseProtoResponse(bytes: ByteArray): ApiUnwrap {
    return try {
        val resp = MessageProtos.ResponseMessage.parseFrom(bytes)
        val success = resp.success
        // Current generated proto (existing in build/generated) has 'message' as string and fields: success, clientId, online
        val dataJson: String? = if (resp.message.isNullOrEmpty()) null else resp.message
        val message = if (!success) resp.message else null
        ApiUnwrap(hasEnvelope = true, success = success, dataJson = dataJson, message = message)
    } catch (e: Exception) {
        ApiUnwrap(hasEnvelope = false, success = true, dataJson = String(bytes, StandardCharsets.UTF_8), message = null)
    }
}
