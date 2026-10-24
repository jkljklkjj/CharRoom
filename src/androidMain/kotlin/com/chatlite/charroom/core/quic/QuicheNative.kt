package com.chatlite.charroom.core.quic

/** Stable JNI boundary; no callbacks or JVM references are retained by native code. */
object QuicheNative {
    init { System.loadLibrary("chatlite_quic") }
    @JvmStatic external fun open(host: String, port: Int, caFile: String, alpn: String): Long
    @JvmStatic external fun poll(handle: Long, timeoutMillis: Int): ByteArray?
    @JvmStatic external fun send(handle: Long, streamId: Long, bytes: ByteArray)
    @JvmStatic external fun close(handle: Long)
}
