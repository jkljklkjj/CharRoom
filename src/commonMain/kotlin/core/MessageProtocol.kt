package core

import com.chatlite.proto.MessageProtos

/** Protocol identities come from generated protobuf enums, never a second string/number table. */
typealias MsgType = MessageProtos.MessageWrapperType

object MessageProtocol {
    const val ACK_TIMEOUT_MILLIS = 16_000L
    const val RECENT_MESSAGE_LIMIT = 256
    // QuicFrameCodec.parseWrapper delegates to safeParseProtobuf(payload, 65536).
    const val QUIC_SERVER_MAX_PAYLOAD_BYTES = 65_536
}

internal fun MessageProtos.MessageWrapper.deliveryMessageId(): String? = when (type) {
    MsgType.CHAT -> if (hasChat()) chat.messageId else ""
    MsgType.GROUP_CHAT -> if (hasGroupChat()) groupChat.messageId else ""
    else -> null
}
