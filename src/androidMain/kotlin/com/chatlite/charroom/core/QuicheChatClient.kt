package com.chatlite.charroom.core

import android.content.Context
import com.chatlite.charroom.core.quic.AndroidQuicTrustRoots
import com.chatlite.charroom.core.quic.QuicheDriver
import core.ChatTransport
import core.NativeQuicChatTransport
import core.QuicIdentity
import core.state.GlobalAppState
import org.slf4j.LoggerFactory

/** Android raw QUIC adapter. Native work runs on the transport-owned worker, never the UI thread. */
private val incomingPersistence = core.ReceivedMessagePersistence()

class QuicheChatClient(context: Context) : ChatTransport by NativeQuicChatTransport(
    persistIncoming = { account, wrapper -> incomingPersistence.persist(account, wrapper) },
    endpointResolver = { identity, host, port -> kotlinx.coroutines.runBlocking { core.discoverGatewayOwner(identity.token, host, port) } },
    driverFactory = { QuicheDriver(AndroidQuicTrustRoots.pemFile(context.applicationContext)) },
    identity = {
        val preferences = context.applicationContext.getSharedPreferences("quic-device", Context.MODE_PRIVATE)
        val id = preferences.getString("deviceId", null) ?: java.util.UUID.randomUUID().toString().also {
            preferences.edit().putString("deviceId", it).apply()
        }
        QuicIdentity(GlobalAppState.currentToken.orEmpty(), GlobalAppState.currentUserId, id)
    },
    onAuthenticated = { GlobalAppState.setCurrentUserId(it) },
    onFailure = { LoggerFactory.getLogger(QuicheChatClient::class.java).warn("Android QUIC connection failed", it) }
)
