package core

/** Listener identity and snapshot locks are shared with the transport's dispatcher. */
internal class TransportListenerRegistry : TransportObservers {
    val messageListeners = mutableListOf<MessageReceiveListener>()
    val authStateListeners = mutableListOf<AuthStateListener>()

    override fun addMessageReceiveListener(listener: MessageReceiveListener) {
        synchronized(messageListeners) {
            if (!messageListeners.contains(listener)) {
                messageListeners.add(listener)
            }
        }
    }

    override fun removeMessageReceiveListener(listener: MessageReceiveListener) {
        synchronized(messageListeners) {
            messageListeners.remove(listener)
        }
    }

    override fun addAuthStateListener(listener: AuthStateListener) {
        synchronized(authStateListeners) {
            if (!authStateListeners.contains(listener)) {
                authStateListeners.add(listener)
            }
        }
    }

    override fun removeAuthStateListener(listener: AuthStateListener) {
        synchronized(authStateListeners) {
            authStateListeners.remove(listener)
        }
    }
}
