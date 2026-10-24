package core

import com.chatlite.proto.MessageProtos
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/** Platform-independent custom-ALPN session, backed by an injected raw QUIC driver. */
class NativeQuicChatTransport(
    private val driverFactory: () -> QuicDriver,
    private val identity: () -> QuicIdentity,
    private val onAuthenticated: (Int) -> Unit = {},
    private val onFailure: (Exception) -> Unit = {},
    private val timing: QuicTransportTiming = QuicTransportTiming()
) : ChatTransport {
    private class AuthenticationFailure(message: String) : IOException(message)
    private data class Outgoing(val payload: ByteArray, val type: MsgType, val target: String,
                                val callback: (Boolean, List<ByteArray>) -> Unit) {
        val completed = AtomicBoolean(false)
        var owner: Run? = null
    }
    private class Run(val host: String, val port: Int, @Volatile var account: Int?) {
        val stopped = AtomicBoolean(false)
        val commands = ArrayBlockingQueue<Outgoing>(256)
        @Volatile var online = false
        var queuedBytes = 0
        lateinit var thread: Thread
    }
    private data class Timer(val deadline: Long, val cancelled: AtomicBoolean, val action: () -> Unit)
    private data class Session(val stream: Long, val key: String, val waiting: MutableList<Outgoing> = mutableListOf(),
                               var ready: Boolean = false, var cancelTimeout: (() -> Unit)? = null)
    private val listeners = CopyOnWriteArrayList<MessageReceiveListener>()
    private val authListeners = CopyOnWriteArrayList<AuthStateListener>()
    @Volatile override var agentStreamHandler: AgentStreamHandler? = null
    @Volatile private var current: Run? = null
    private var lastHost = ServerConfig.QUIC_HOST
    private var lastPort = ServerConfig.QUIC_PORT

    @Synchronized override fun start(host: String?, port: Int?) {
        stop()
        lastHost = host ?: ServerConfig.QUIC_HOST
        lastPort = port ?: ServerConfig.QUIC_PORT
        val run = Run(lastHost, lastPort, identity().userId)
        current = run
        run.thread = Thread({ supervise(run) }, "chatlite-quic").apply { isDaemon = true }
        run.thread.start()
    }
    @Synchronized override fun stop() {
        val run = current ?: return
        current = null
        synchronized(run) { run.online = false; run.stopped.set(true) }
        run.thread.interrupt()
    }
    override fun reconnect() { start(lastHost, lastPort) }
    override fun isConnected() = current?.let { it.online && !it.stopped.get() } == true
    override val isServerConnected get() = isConnected()
    override fun sendText(content: String, callback: (Boolean) -> Unit) = callback(false)
    override fun addMessageReceiveListener(listener: MessageReceiveListener) { listeners.addIfAbsent(listener) }
    override fun removeMessageReceiveListener(listener: MessageReceiveListener) { listeners.remove(listener) }
    override fun addAuthStateListener(listener: AuthStateListener) { authListeners.addIfAbsent(listener) }
    override fun removeAuthStateListener(listener: AuthStateListener) { authListeners.remove(listener) }

    override fun send(payload: ByteArray, type: MsgType, targetClientId: String, expectedResponses: Int,
                      callback: (Boolean, List<ByteArray>) -> Unit) {
        if (payload.isEmpty() || payload.size > MessageProtocol.QUIC_SERVER_MAX_PAYLOAD_BYTES) {
            callback(false, emptyList())
            return
        }
        val outgoing = Outgoing(payload.copyOf(), type, targetClientId, callback)
        val run = current
        val accepted = run != null && synchronized(run) {
            if (!active(run) || !run.online || payload.size > 4 * 1024 * 1024 - run.queuedBytes) false
            else {
                outgoing.owner = run
                run.queuedBytes += payload.size
                run.commands.offer(outgoing)
            }
        }
        if (!accepted) complete(outgoing, false)
    }
    override fun logoutAndDisconnect() {
        val run = current ?: return
        val userId = identity().userId
        if (userId != null) {
            // The worker sends and closes; never block the Android UI thread.
            val sent = synchronized(run) {
                active(run) && run.online && run.commands.offer(Outgoing(buildLogoutPayload(userId.toString()), MsgType.LOGOUT, "") { _, _ -> })
            }
            if (sent) return
        }
        stop()
    }
    private fun active(run: Run) = current === run && !run.stopped.get()
    private fun now() = System.nanoTime() / 1_000_000
    private fun complete(outgoing: Outgoing, success: Boolean, responses: List<ByteArray> = emptyList()) {
        if (!outgoing.completed.compareAndSet(false, true)) return
        outgoing.owner?.let { run -> synchronized(run) { run.queuedBytes -= outgoing.payload.size } }
        try { outgoing.callback(success, responses) } catch (error: Exception) {
            if (error is java.util.concurrent.CancellationException) throw error
            onFailure(error)
        }
    }
    private fun failQueued(run: Run) {
        while (true) complete(run.commands.poll() ?: break, false)
    }
    private fun supervise(run: Run) {
        var retry = timing.reconnectMillis
        try {
            while (active(run)) {
                try {
                    val credentials = identity()
                    if (credentials.token.isBlank() || credentials.userId != run.account) throw AuthenticationFailure("Authentication changed")
                    driverFactory().use { driver -> Attempt(run, driver, credentials).execute() }
                } catch (error: AuthenticationFailure) {
                    run.stopped.set(true)
                    if (current === run) authListeners.forEach { listener ->
                        try { listener.onAuthInvalidated(error.message ?: "Authentication failed") } catch (failure: Exception) {
                            if (failure is java.util.concurrent.CancellationException) throw failure
                            onFailure(failure)
                        }
                    }
                } catch (_: InterruptedException) {
                    if (active(run)) throw IOException("QUIC worker interrupted")
                } catch (error: Exception) {
                    if (error is java.util.concurrent.CancellationException) {
                        run.stopped.set(true)
                        throw error
                    }
                    if (active(run)) onFailure(error)
                } catch (error: LinkageError) {
                    // A missing/wrong-ABI native library must fail visibly instead of retrying forever.
                    run.stopped.set(true)
                    onFailure(IOException("Cannot load the Android QUIC native library", error))
                } finally {
                    run.online = false
                    failQueued(run)
                }
                if (active(run)) {
                    Thread.sleep(retry)
                    retry = (retry * 2).coerceAtMost(30_000)
                }
            }
        } catch (_: InterruptedException) {
            // stop() interrupts backoff/poll; the owning attempt releases its driver in finally.
        } finally { run.online = false; failQueued(run) }
    }

    private inner class Attempt(val run: Run, val driver: QuicDriver, val credentials: QuicIdentity) {
        private val timers = mutableListOf<Timer>()
        private val decoders = mutableMapOf<Long, QuicStreamProtocol.Decoder>()
        private val sessions = mutableMapOf<String, Session>()
        private val byStream = mutableMapOf<Long, Session>()
        private var nextStream = 4L
        private var handshaken = false
        private var lastReceived = now()
        private var heartbeatAt = now()
        private val acks = PendingMessageAcks(schedule = ::schedule, onCallbackFailure = onFailure)
        private val dispatcher = ProtobufMessageDispatcher(
            listeners = { if (active(run)) listeners.toList() else emptyList() },
            agentHandler = { if (active(run)) agentStreamHandler else null },
            onAcknowledgement = { ack, bytes -> acks.acknowledge(ack, bytes) }, onFailure = onFailure
        )
        private fun schedule(delay: Long, action: () -> Unit): () -> Unit {
            val cancelled = AtomicBoolean(false)
            timers += Timer(now() + delay, cancelled, action)
            return { cancelled.set(true) }
        }
        fun execute() {
            val cancelHandshake = schedule(timing.handshakeMillis) { throw IOException("QUIC handshake timed out") }
            try {
                driver.connect(run.host, run.port)
                while (active(run)) {
                    val due = timers.filter { it.deadline <= now() && !it.cancelled.get() }
                    timers.removeAll { it.deadline <= now() || it.cancelled.get() }
                    due.forEach { it.action() }
                    if (run.online) {
                        var count = 0
                        while (count++ < 32) sendCommand(run.commands.poll() ?: break)
                        if (!active(run)) break
                        if (now() - lastReceived > timing.idleMillis) throw IOException("QUIC heartbeat timed out")
                        if (now() >= heartbeatAt) {
                            driver.send(0, QuicStreamProtocol.encodeFrame(buildHeartbeatPayload()))
                            heartbeatAt = now() + timing.heartbeatMillis
                        }
                    }
                    when (val event = driver.poll(20)) {
                        QuicEvent.Connected -> {
                            check(!handshaken) { "Duplicate QUIC handshake event" }
                            handshaken = true
                            cancelHandshake()
                            schedule(timing.loginMillis) { if (!run.online) throw IOException("QUIC login timed out") }
                            driver.send(0, QuicStreamProtocol.encodeFrame(buildLoginPayload(credentials.token, "mobile", credentials.deviceId)))
                        }
                        is QuicEvent.Data -> {
                            if (!active(run)) break
                            val decoder = decoders.getOrPut(event.streamId) { QuicStreamProtocol.Decoder() }
                            decoder.decode(event.bytes).forEach { receive(event.streamId, it) }
                        }
                        is QuicEvent.StreamClosed -> {
                            decoders.remove(event.streamId)
                            if (event.streamId == 0L) throw IOException("QUIC control stream closed")
                            byStream.remove(event.streamId)?.let { session ->
                                sessions.remove(session.key)
                                session.cancelTimeout?.invoke()
                                session.waiting.forEach { complete(it, false) }
                                session.waiting.clear()
                            }
                        }
                        is QuicEvent.Closed -> throw IOException(event.reason)
                        null -> {}
                    }
                }
            } finally {
                run.online = false
                acks.failAll()
                sessions.values.forEach { session -> session.waiting.forEach { complete(it, false) }; session.waiting.clear() }
                dispatcher.clear()
                timers.clear()
            }
        }
        private fun receive(stream: Long, bytes: ByteArray) {
            if (!active(run)) return
            val wrapper = MessageProtos.MessageWrapper.parseFrom(bytes)
            lastReceived = now()
            if (!run.online) {
                if (!handshaken || stream != 0L || wrapper.type != MsgType.RESPONSE || !wrapper.hasResponse()) return
                val response = wrapper.response
                val id = response.clientId.toIntOrNull()
                if (!response.success || id == null || id <= 0 || (credentials.userId != null && id != credentials.userId)) {
                    throw AuthenticationFailure("QUIC login rejected")
                }
                if (!active(run)) return
                synchronized(run) {
                    if (!active(run)) return
                    run.account = id
                    onAuthenticated(id)
                    run.online = true
                }
                heartbeatAt = now() + timing.heartbeatMillis
                return
            }
            if (wrapper.type == MsgType.STREAM_INIT_ACK) {
                val session = byStream[stream] ?: return
                if (session.ready) return
                check(wrapper.hasResponse() && wrapper.response.success) { "QUIC stream initialization rejected" }
                session.ready = true
                session.cancelTimeout?.invoke()
                while (session.waiting.isNotEmpty() && active(run)) {
                    val outgoing = session.waiting.removeAt(0)
                    try { write(session.stream, outgoing) } catch (error: Exception) {
                        complete(outgoing, false)
                        throw error
                    }
                }
                return
            }
            if (wrapper.type == MsgType.LOGOUT) throw AuthenticationFailure("QUIC session invalidated")
            dispatcher.dispatch(bytes) { messageId, conversationId ->
                val ack = MessageProtos.MessageWrapper.newBuilder().setType(MsgType.ACK)
                    .setAck(MessageProtos.AckMessage.newBuilder().setMessageId(messageId).setSuccess(true).setConversationId(conversationId)).build()
                driver.send(0, QuicStreamProtocol.encodeFrame(ack.toByteArray()))
            }
        }
        private fun sendCommand(outgoing: Outgoing) {
            try {
                val wrapper = MessageProtos.MessageWrapper.parseFrom(outgoing.payload)
                require(wrapper.type == outgoing.type) { "Message type and payload disagree" }
                wrapper.deliveryMessageId()?.let { require(it.isNotBlank()) { "A messageId is required" } }
                val conversationType = when (outgoing.type) {
                    MsgType.CHAT -> MessageProtos.ConversationType.PRIVATE
                    MsgType.GROUP_CHAT -> MessageProtos.ConversationType.GROUP
                    MsgType.AGENT_CHAT -> MessageProtos.ConversationType.AGENT
                    else -> null
                }
                if (conversationType == null) {
                    write(0, outgoing)
                    if (outgoing.type == MsgType.LOGOUT) run.stopped.set(true)
                    return
                }
                val target = outgoing.target.toLong()
                val key = "${conversationType.name}:$target"
                val existing = sessions[key]
                if (existing?.ready == true) { write(existing.stream, outgoing); return }
                if (existing != null) {
                    require(existing.waiting.size < 128) { "QUIC stream send queue is full" }
                    existing.waiting += outgoing
                    return
                }
                require(sessions.size < 256) { "QUIC conversation stream limit reached" }
                val session = Session(nextStream, key)
                nextStream += 4
                sessions[key] = session
                byStream[session.stream] = session
                session.waiting += outgoing
                session.cancelTimeout = schedule(timing.streamInitMillis) {
                    if (!session.ready) {
                        // Reset the connection so a silent peer cannot exhaust its open-stream credit.
                        throw IOException("QUIC stream initialization timed out")
                    }
                }
                val init = MessageProtos.MessageWrapper.newBuilder().setType(MsgType.CHECK)
                    .setCheck(MessageProtos.CheckMessage.newBuilder().setConversationType(conversationType).setTargetClientId(target)).build()
                driver.send(session.stream, QuicStreamProtocol.encodeFrame(init.toByteArray()))
            } catch (error: Exception) {
                if (error is java.util.concurrent.CancellationException) throw error
                // If the command is waiting for init it is owned by that session now.
                sessions.values.forEach { it.waiting.remove(outgoing) }
                complete(outgoing, false)
                if (error is IOException) throw error
                onFailure(error)
            }
        }
        private fun write(stream: Long, outgoing: Outgoing) {
            val id = MessageProtos.MessageWrapper.parseFrom(outgoing.payload).deliveryMessageId()
            if (id != null) acks.await(id) { success, responses -> complete(outgoing, success, responses) }
            try {
                driver.send(stream, QuicStreamProtocol.encodeFrame(outgoing.payload))
                if (id == null) complete(outgoing, true)
            } catch (error: Exception) {
                if (id != null) acks.fail(id) else complete(outgoing, false)
                throw error
            }
        }
    }
}
