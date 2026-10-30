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
    private val timing: QuicTransportTiming = QuicTransportTiming(),
    private val persistIncoming: (Int, MessageProtos.MessageWrapper) -> Unit = { _, _ -> },
    private val endpointResolver: (QuicIdentity, String, Int) -> Pair<String, Int> =
        { _, host, port ->
            host to port
        },
) : ChatTransport {
    private class AuthenticationFailure(message: String) : IOException(message)

    private data class Outgoing(
        val payload: ByteArray,
        val type: MsgType,
        val target: String,
        val callback: (Boolean, List<ByteArray>) -> Unit,
    ) {
        val completed = AtomicBoolean(false)
        var owner: Run? = null
    }

    private class Run(val host: String, val port: Int, @Volatile var account: Int?) {
        val stopped = AtomicBoolean(false)
        val commands = ArrayBlockingQueue<Outgoing>(256)
        @Volatile var online = false
        var retryNotBefore = 0L
        var queuedBytes = 0
        lateinit var thread: Thread
    }

    private data class Timer(
        val deadline: Long,
        val cancelled: AtomicBoolean,
        val action: () -> Unit,
    )

    private data class Session(
        val stream: Long,
        val key: String,
        val waiting: MutableList<Outgoing> = mutableListOf(),
        var ready: Boolean = false,
        var cancelTimeout: (() -> Unit)? = null,
    )

    private val listeners = CopyOnWriteArrayList<MessageReceiveListener>()
    private val authListeners = CopyOnWriteArrayList<AuthStateListener>()
    @Volatile override var agentStreamHandler: AgentStreamHandler? = null
    @Volatile private var current: Run? = null
    private var lastHost = ServerConfig.QUIC_HOST
    private var lastPort = ServerConfig.QUIC_PORT

    @Synchronized
    override fun start(host: String?, port: Int?) {
        stop()
        lastHost = host ?: ServerConfig.QUIC_HOST
        lastPort = port ?: ServerConfig.QUIC_PORT
        val run = Run(lastHost, lastPort, identity().userId)
        current = run
        run.thread = Thread({ supervise(run) }, "chatlite-quic").apply { isDaemon = true }
        run.thread.start()
    }

    @Synchronized
    override fun stop() {
        val run = current ?: return
        current = null
        synchronized(run) {
            run.online = false
            run.stopped.set(true)
        }
        run.thread.interrupt()
    }

    override fun reconnect() {
        start(lastHost, lastPort)
    }

    override fun isConnected() = current?.let { it.online && !it.stopped.get() } == true

    override val isServerConnected
        get() = isConnected()

    override fun sendText(content: String, callback: (Boolean) -> Unit) = callback(false)

    override fun addMessageReceiveListener(listener: MessageReceiveListener) {
        listeners.addIfAbsent(listener)
    }

    override fun removeMessageReceiveListener(listener: MessageReceiveListener) {
        listeners.remove(listener)
    }

    override fun addAuthStateListener(listener: AuthStateListener) {
        authListeners.addIfAbsent(listener)
    }

    override fun removeAuthStateListener(listener: AuthStateListener) {
        authListeners.remove(listener)
    }

    override fun send(
        payload: ByteArray,
        type: MsgType,
        targetClientId: String,
        expectedResponses: Int,
        callback: (Boolean, List<ByteArray>) -> Unit,
    ) {
        if (payload.isEmpty() || payload.size > MessageProtocol.QUIC_SERVER_MAX_PAYLOAD_BYTES) {
            callback(false, emptyList())
            return
        }
        val outgoing = Outgoing(payload.copyOf(), type, targetClientId, callback)
        val run = current
        val accepted =
            run != null &&
                synchronized(run) {
                    val connected = active(run) && run.online
                    if (!connected || payload.size > 4 * 1024 * 1024 - run.queuedBytes) false
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
            val sent =
                synchronized(run) {
                    active(run) &&
                        run.online &&
                        run.commands.offer(
                            Outgoing(buildLogoutPayload(userId.toString()), MsgType.LOGOUT, "") {
                                _,
                                _ ->
                            }
                        )
                }
            if (sent) return
        }
        stop()
    }

    private fun active(run: Run) = current === run && !run.stopped.get()

    private fun now() = System.nanoTime() / 1_000_000

    private fun complete(
        outgoing: Outgoing,
        success: Boolean,
        responses: List<ByteArray> = emptyList(),
    ) {
        if (!outgoing.completed.compareAndSet(false, true)) return
        outgoing.owner?.let { run ->
            synchronized(run) { run.queuedBytes -= outgoing.payload.size }
        }
        core.recoverOperation(
            onFailure = { error ->
                onFailure(error)
            }
        ) {
            outgoing.callback(success, responses)
        }
    }

    private fun failQueued(run: Run) {
        while (true) complete(run.commands.poll() ?: break, false)
    }

    private fun supervise(run: Run) {
        var retry = timing.reconnectMillis
        try {
            while (active(run)) {
                attemptOnce(run)
                if (active(run)) {
                    Thread.sleep(maxOf(retry, run.retryNotBefore - now()))
                    retry = (retry * 2).coerceAtMost(30_000)
                }
            }
        } catch (_: InterruptedException) {
            // stop() interrupts backoff; the attempt releases its driver in finally.
        } finally {
            run.online = false
            failQueued(run)
        }
    }

    private fun attemptOnce(run: Run) {
        try {
            recoverOperation({ failure -> handleAttemptFailure(run, failure) }) {
                val credentials = identity()
                if (credentials.token.isBlank() || credentials.userId != run.account)
                    throw AuthenticationFailure("Authentication changed")
                driverFactory().use { driver -> Attempt(run, driver, credentials).execute() }
            }
        } catch (cancelled: java.util.concurrent.CancellationException) {
            run.stopped.set(true)
            throw cancelled
        } catch (linkage: LinkageError) {
            run.stopped.set(true)
            onFailure(IOException("Cannot load the Android QUIC native library", linkage))
        } finally {
            run.online = false
            failQueued(run)
        }
    }

    private fun handleAttemptFailure(run: Run, failure: Exception) {
        when (failure) {
            is AuthenticationFailure -> {
                run.stopped.set(true)
                if (current === run)
                    authListeners.forEach { listener ->
                        recoverOperation({ onFailure(it) }) {
                            listener.onAuthInvalidated(failure.message ?: "Authentication failed")
                        }
                    }
            }
            is InterruptedException -> if (active(run)) throw IOException("QUIC worker interrupted")
            else -> if (active(run)) onFailure(failure)
        }
    }

    private inner class Attempt(
        val run: Run,
        val driver: QuicDriver,
        val credentials: QuicIdentity,
    ) {
        private val timers = mutableListOf<Timer>()
        private val decoders = mutableMapOf<Long, QuicStreamProtocol.Decoder>()
        private val sessions = mutableMapOf<String, Session>()
        private val byStream = mutableMapOf<Long, Session>()
        private var nextStream = 4L
        private var handshaken = false
        private var lastReceived = now()
        private var heartbeatAt = now()
        private val acks = PendingMessageAcks(schedule = ::schedule, onCallbackFailure = onFailure)
        private val dispatcher =
            ProtobufMessageDispatcher(
                listeners = { if (active(run)) listeners.toList() else emptyList() },
                agentHandler = { if (active(run)) agentStreamHandler else null },
                onAcknowledgement = { ack, bytes -> acks.acknowledge(ack, bytes) },
                onFailure = onFailure,
                accountId = { run.account ?: 0 },
                persistIncoming = { wrapper ->
                    check(active(run) && run.account == identity().userId) {
                        "Stale receiving session"
                    }
                    persistIncoming(run.account ?: error("Missing authenticated account"), wrapper)
                    check(active(run) && run.account == identity().userId) {
                        "Receiving session changed during persistence"
                    }
                },
            )

        private fun schedule(delay: Long, action: () -> Unit): () -> Unit {
            val cancelled = AtomicBoolean(false)
            timers += Timer(now() + delay, cancelled, action)
            return { cancelled.set(true) }
        }

        fun execute() {
            val cancelHandshake =
                schedule(timing.handshakeMillis) { throw IOException("QUIC handshake timed out") }
            try {
                val endpoint = endpointResolver(credentials, run.host, run.port)
                driver.connect(endpoint.first, endpoint.second)
                while (active(run)) {
                    val due = timers.filter { it.deadline <= now() && !it.cancelled.get() }
                    timers.removeAll { it.deadline <= now() || it.cancelled.get() }
                    due.forEach { it.action() }
                    if (run.online) serviceOutgoing()
                    if (!active(run)) break
                    handleEvent(driver.poll(20), cancelHandshake)
                }
            } finally {
                cleanupAttempt()
            }
        }

        private fun serviceOutgoing() {
            var count = 0
            while (count++ < 32) sendCommand(run.commands.poll() ?: break)
            if (!active(run)) return
            if (now() - lastReceived > timing.idleMillis)
                throw IOException("QUIC heartbeat timed out")
            if (now() >= heartbeatAt) {
                driver.send(0, QuicStreamProtocol.encodeFrame(buildHeartbeatPayload()))
                heartbeatAt = now() + timing.heartbeatMillis
            }
        }

        private fun handleEvent(event: QuicEvent?, cancelHandshake: () -> Unit) {
            when (event) {
                QuicEvent.Connected -> {
                    check(!handshaken) { "Duplicate QUIC handshake event" }
                    handshaken = true
                    cancelHandshake()
                    schedule(timing.loginMillis) {
                        if (!run.online) throw IOException("QUIC login timed out")
                    }
                    driver.send(
                        0,
                        QuicStreamProtocol.encodeFrame(
                            buildLoginPayload(credentials.token, "mobile", credentials.deviceId)
                        ),
                    )
                }
                is QuicEvent.Data ->
                    if (active(run)) {
                        val decoder =
                            decoders.getOrPut(event.streamId) { QuicStreamProtocol.Decoder() }
                        decoder.decode(event.bytes).forEach { receive(event.streamId, it) }
                    }
                is QuicEvent.StreamClosed -> closeStream(event.streamId)
                is QuicEvent.Closed -> throw IOException(event.reason)
                null -> Unit
            }
        }

        private fun closeStream(stream: Long) {
            decoders.remove(stream)
            if (stream == 0L) throw IOException("QUIC control stream closed")
            byStream.remove(stream)?.let { session ->
                sessions.remove(session.key)
                session.cancelTimeout?.invoke()
                session.waiting.forEach { complete(it, false) }
                session.waiting.clear()
            }
        }

        private fun cleanupAttempt() {
            run.online = false
            acks.failAll()
            sessions.values.forEach { session ->
                session.waiting.forEach { complete(it, false) }
                session.waiting.clear()
            }
            dispatcher.clear()
            timers.clear()
        }

        private fun receive(stream: Long, bytes: ByteArray) {
            if (!active(run)) return
            val wrapper = MessageProtos.MessageWrapper.parseFrom(bytes)
            lastReceived = now()
            when {
                !run.online -> receiveLogin(stream, wrapper)
                wrapper.type == MsgType.STREAM_INIT_ACK -> receiveInit(stream, wrapper)
                wrapper.type == MsgType.LOGOUT ->
                    throw AuthenticationFailure("QUIC session invalidated")
                else ->
                    dispatcher.dispatch(bytes) { id, conversation -> sendReceipt(id, conversation) }
            }
        }

        private fun receiveLogin(stream: Long, wrapper: MessageProtos.MessageWrapper) {
            val controlResponse = stream == 0L && wrapper.type == MsgType.RESPONSE
            if (!handshaken || !controlResponse) return
            if (!wrapper.hasResponse()) return
            val response = wrapper.response
            handshakeRetryAfterMillis(response.message)?.let { run.retryNotBefore = now() + it }
            if (isTransientGatewayResponse(response.message))
                throw IOException("Gateway temporarily unavailable: ${response.message}")
            val id = loginIdentity(response)
            val accepted =
                synchronized(run) {
                    if (active(run)) {
                        run.account = id
                        onAuthenticated(id)
                        run.online = true
                        true
                    } else false
                }
            if (accepted) heartbeatAt = now() + timing.heartbeatMillis
        }

        private fun loginIdentity(response: MessageProtos.ResponseMessage): Int {
            val id =
                response.clientId.toIntOrNull()
                    ?: throw AuthenticationFailure("QUIC login rejected")
            val expected = credentials.userId == null || id == credentials.userId
            val accepted = response.success && id > 0
            if (!accepted || !expected) throw AuthenticationFailure("QUIC login rejected")
            return id
        }

        private fun receiveInit(stream: Long, wrapper: MessageProtos.MessageWrapper) {
            val session = byStream[stream] ?: return
            if (session.ready) return
            check(wrapper.hasResponse() && wrapper.response.success) {
                "QUIC stream initialization rejected"
            }
            session.ready = true
            session.cancelTimeout?.invoke()
            while (session.waiting.isNotEmpty() && active(run)) {
                val outgoing = session.waiting.removeAt(0)
                var sent = false
                try {
                    write(session.stream, outgoing)
                    sent = true
                } finally {
                    if (!sent) complete(outgoing, false)
                }
            }
        }

        private fun sendReceipt(id: String, conversation: String) {
            val ack =
                MessageProtos.MessageWrapper.newBuilder()
                    .setType(MsgType.ACK)
                    .setAck(
                        MessageProtos.AckMessage.newBuilder()
                            .setMessageId(id)
                            .setSuccess(true)
                            .setConversationId(conversation)
                    )
                    .build()
            driver.send(0, QuicStreamProtocol.encodeFrame(ack.toByteArray()))
        }

        private fun sendCommand(outgoing: Outgoing) =
            recoverOperation({ failure ->
                sessions.values.forEach { it.waiting.remove(outgoing) }
                complete(outgoing, false)
                if (failure is IOException) throw failure
                onFailure(failure)
            }) {
                validateOutgoing(outgoing)
                val kind =
                    when (outgoing.type) {
                        MsgType.CHAT -> MessageProtos.ConversationType.PRIVATE
                        MsgType.GROUP_CHAT -> MessageProtos.ConversationType.GROUP
                        MsgType.AGENT_CHAT -> MessageProtos.ConversationType.AGENT
                        else -> null
                    }
                if (kind == null) {
                    write(0, outgoing)
                    if (outgoing.type == MsgType.LOGOUT) run.stopped.set(true)
                } else routeConversation(outgoing, kind)
            }

        private fun validateOutgoing(outgoing: Outgoing) {
            val wrapper = MessageProtos.MessageWrapper.parseFrom(outgoing.payload)
            require(wrapper.type == outgoing.type) { "Message type and payload disagree" }
            wrapper.deliveryMessageId()?.let {
                require(it.isNotBlank()) { "A messageId is required" }
            }
        }

        private fun routeConversation(outgoing: Outgoing, kind: MessageProtos.ConversationType) {
            val target = outgoing.target.toLong()
            val key = "${kind.name}:$target"
            val existing = sessions[key]
            when {
                existing?.ready == true -> write(existing.stream, outgoing)
                existing != null -> {
                    require(existing.waiting.size < 128) { "QUIC stream send queue is full" }
                    existing.waiting += outgoing
                }
                else -> initializeSession(key, target, kind, outgoing)
            }
        }

        private fun initializeSession(
            key: String,
            target: Long,
            kind: MessageProtos.ConversationType,
            outgoing: Outgoing,
        ) {
            require(sessions.size < 256) { "QUIC conversation stream limit reached" }
            val session = Session(nextStream, key)
            nextStream += 4
            sessions[key] = session
            byStream[session.stream] = session
            session.waiting += outgoing
            session.cancelTimeout =
                schedule(timing.streamInitMillis) {
                    if (!session.ready) throw IOException("QUIC stream initialization timed out")
                }
            val init =
                MessageProtos.MessageWrapper.newBuilder()
                    .setType(MsgType.CHECK)
                    .setCheck(
                        MessageProtos.CheckMessage.newBuilder()
                            .setConversationType(kind)
                            .setTargetClientId(target)
                    )
                    .build()
            driver.send(session.stream, QuicStreamProtocol.encodeFrame(init.toByteArray()))
        }

        private fun write(stream: Long, outgoing: Outgoing) {
            val id = MessageProtos.MessageWrapper.parseFrom(outgoing.payload).deliveryMessageId()
            if (id != null)
                acks.await(id) { success, responses -> complete(outgoing, success, responses) }
            var sent = false
            try {
                driver.send(stream, QuicStreamProtocol.encodeFrame(outgoing.payload))
                if (id == null) complete(outgoing, true)
                sent = true
            } finally {
                if (!sent) {
                    if (id != null) acks.fail(id) else complete(outgoing, false)
                }
            }
        }
    }
}
