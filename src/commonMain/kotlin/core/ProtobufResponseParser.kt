package core

// Parse protobuf ResponseMessage bytes into ApiUnwrap (platform-specific actual implementation)
expect fun parseProtoResponse(bytes: ByteArray): ApiUnwrap

