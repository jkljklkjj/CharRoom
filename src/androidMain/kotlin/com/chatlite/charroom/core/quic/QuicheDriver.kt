package com.chatlite.charroom.core.quic

import core.QuicDriver
import core.QuicEvent
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class QuicheDriver(private val caFile: String) : QuicDriver {
    private var handle = 0L
    override fun connect(host: String, port: Int) {
        check(handle == 0L) { "QUIC driver is already open" }
        handle = QuicheNative.open(host, port, caFile, "custom")
        check(handle != 0L) { "Cannot create QUIC connection" }
    }
    override fun send(streamId: Long, bytes: ByteArray) {
        check(handle != 0L) { "QUIC driver is closed" }
        QuicheNative.send(handle, streamId, bytes)
    }
    override fun poll(timeoutMillis: Int): QuicEvent? {
        check(handle != 0L) { "QUIC driver is closed" }
        val bytes = QuicheNative.poll(handle, timeoutMillis) ?: return null
        if (bytes.isEmpty()) throw IOException("Empty QUIC native event")
        return when (bytes[0].toInt()) {
            1 -> QuicEvent.Connected
            2, 3 -> {
                if (bytes.size < 9) throw IOException("Truncated QUIC native event")
                val id = ByteBuffer.wrap(bytes, 1, 8).order(ByteOrder.BIG_ENDIAN).long
                if (bytes[0].toInt() == 2) QuicEvent.Data(id, bytes.copyOfRange(9, bytes.size)) else QuicEvent.StreamClosed(id)
            }
            4 -> QuicEvent.Closed(bytes.copyOfRange(1, bytes.size).toString(Charsets.UTF_8))
            else -> throw IOException("Unknown QUIC native event")
        }
    }
    override fun close() {
        val owned = handle
        handle = 0L
        if (owned != 0L) QuicheNative.close(owned)
    }
}
