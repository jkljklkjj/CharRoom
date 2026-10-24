package core

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QuicStreamProtocolTest {
    @Test
    fun splitHeadersAndPayloadsSurviveAcrossReadCallbacks() {
        val payload = buildChatPayload("42", "fragmented", timestamp = 123L)
        val framed = QuicStreamProtocol.encodeFrame(payload)
        val decoder = QuicStreamProtocol.Decoder()
        val decoded = mutableListOf<ByteArray>()
        for (byte in framed) decoded += decoder.decode(byteArrayOf(byte))
        assertEquals(1, decoded.size)
        assertContentEquals(payload, decoded.single())
    }

    @Test
    fun coalescedFramesAndATrailingPartialFrameAreNotLost() {
        val first = byteArrayOf(1, 2, 3)
        val second = byteArrayOf(4, 5, 6, 7)
        val secondFrame = QuicStreamProtocol.encodeFrame(second)
        val decoder = QuicStreamProtocol.Decoder()
        val initial = decoder.decode(QuicStreamProtocol.encodeFrame(first) + secondFrame.copyOfRange(0, 5))
        assertEquals(1, initial.size)
        assertContentEquals(first, initial.single())
        assertContentEquals(second, decoder.decode(secondFrame.copyOfRange(5, secondFrame.size)).single())
    }

    @Test
    fun cronetBufferPositionIsConvertedBeforeReadingData() {
        val payload = byteArrayOf(7, 8, 9)
        val buffer = ByteBuffer.allocateDirect(128).put(QuicStreamProtocol.encodeFrame(payload))
        assertContentEquals(payload, QuicStreamProtocol.Decoder().decodeReadBuffer(buffer).single())
    }

    @Test
    fun malformedLengthsAreRejectedAndStreamResetDropsPartialData() {
        val decoder = QuicStreamProtocol.Decoder()
        for (length in listOf(-1, 0, QuicStreamProtocol.MAX_FRAME_SIZE + 1)) {
            assertFailsWith<IllegalArgumentException> { decoder.decode(ByteBuffer.allocate(4).putInt(length).array()) }
            decoder.reset()
        }
        assertTrue(decoder.decode(byteArrayOf(0, 0)).isEmpty())
        decoder.reset()
        assertContentEquals(byteArrayOf(1), decoder.decode(QuicStreamProtocol.encodeFrame(byteArrayOf(1))).single())
    }
}
