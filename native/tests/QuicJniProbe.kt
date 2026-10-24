import com.chatlite.charroom.core.quic.QuicheDriver
import core.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList

/** Exercises the exact Kotlin/JNI transport used by Android, without Android UI or real accounts. */
fun main(args: Array<String>) {
    require(args.size == 3) { "HOST PORT TRUST_ROOTS_PEM" }
    val host = args[0]; val port = args[1].toInt(); val roots = File(args[2])
    val failures = CopyOnWriteArrayList<Exception>()
    val trace = CopyOnWriteArrayList<String>()
    val content = "synthetic-probe:" + "x".repeat(48 * 1024)
    fun client(id: Int) = NativeQuicChatTransport(
        driverFactory = {
            val driver = QuicheDriver(roots.absolutePath)
            object : QuicDriver by driver {
                override fun send(streamId: Long, bytes: ByteArray) {
                    val frame = QuicStreamProtocol.decodeFrame(bytes)!!
                    trace += "$id send $streamId ${com.chatlite.proto.MessageProtos.MessageWrapper.parseFrom(frame.first).type}"
                    driver.send(streamId, bytes)
                }
                override fun poll(timeoutMillis: Int): QuicEvent? = driver.poll(timeoutMillis).also { event ->
                    if (trace.size < 200 && event != null) trace += "$id receive " + when (event) {
                        is QuicEvent.Data -> "${event.streamId} ${event.bytes.size} bytes"
                        else -> event.toString()
                    }
                }
            }
        },
        identity = { QuicIdentity("fixture-$id", id, "probe-$id") },
        onFailure = { failures += it }
    )
    val first = client(42); val second = client(7)
    val received = CountDownLatch(2); val acknowledged = CountDownLatch(2)
    fun listener(expected: Int) = object : MessageReceiveListener {
        override fun onPrivateMessageReceived(senderId: Int, message: String, timestamp: Long, seqId: Long, conversationId: String) {
            check(senderId == expected && message == content && seqId == 1L)
            received.countDown()
        }
        override fun onGroupMessageReceived(groupId: Int, senderId: Int, senderName: String, message: String, timestamp: Long, seqId: Long, conversationId: String) {}
    }
    first.addMessageReceiveListener(listener(7)); second.addMessageReceiveListener(listener(42))
    try {
        first.start(host, port); second.start(host, port)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while ((!first.isConnected() || !second.isConnected()) && System.nanoTime() < deadline) Thread.sleep(10)
        check(first.isConnected() && second.isConnected()) { "Native login failed: ${failures.map { it.message }}" }
        fun send(client: ChatTransport, target: String, id: String) {
            client.send(buildChatPayload(target, content, timestamp = 1, messageId = id), MsgType.CHAT, target, 1) { ok, _ ->
                check(ok) { "Business ACK was rejected" }; acknowledged.countDown()
            }
        }
        send(first, "7", "probe-42-to-7"); send(second, "42", "probe-7-to-42")
        check(acknowledged.await(10, TimeUnit.SECONDS)) { "Matching business ACK was not received: ${failures.map { it.message }}; $trace" }
        check(received.await(10, TimeUnit.SECONDS)) { "Bidirectional delivery failed: ${failures.map { it.message }}" }
        check(failures.isEmpty()) { "Native transport reported an error: ${failures.map { it.message }}" }
        println("PASS: JNI custom-ALPN login, stream initialization, matching ACK and two-account 48 KiB delivery")
    } finally { first.stop(); second.stop(); Thread.sleep(100) }
}
