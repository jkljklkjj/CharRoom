package core

/** Raw IETF QUIC only. Implementations must verify the TLS chain and hostname. */
interface QuicDriver : AutoCloseable {
    fun connect(host: String, port: Int)
    /** Polls for at most timeoutMillis. A stream read is a byte chunk, not a message. */
    fun poll(timeoutMillis: Int): QuicEvent?
    /** Accepts all bytes into a bounded queue; retains partial writes until writable. */
    fun send(streamId: Long, bytes: ByteArray)
    override fun close()
}

sealed class QuicEvent {
    data object Connected : QuicEvent()
    data class Data(val streamId: Long, val bytes: ByteArray) : QuicEvent()
    data class StreamClosed(val streamId: Long) : QuicEvent()
    data class Closed(val reason: String) : QuicEvent()
}

data class QuicIdentity(val token: String, val userId: Int?, val deviceId: String)

data class QuicTransportTiming(
    val handshakeMillis: Long = 15_000,
    val loginMillis: Long = 10_000,
    val streamInitMillis: Long = 10_000,
    val heartbeatMillis: Long = 20_000,
    val idleMillis: Long = 60_000,
    val reconnectMillis: Long = 1_000
)
