package core

interface MessageNotificationSink {
    fun privateMessage(senderId: Int, content: String)

    fun groupMessage(groupId: Int, senderId: Int, senderName: String, content: String)
}

/** Optional platform display side effect; it never adds a receiver that could satisfy an ACK. */
object PlatformMessageNotifications {
    @Volatile var sink: MessageNotificationSink? = null

    fun privateMessage(senderId: Int, content: String) = display {
        it.privateMessage(senderId, content)
    }

    fun groupMessage(groupId: Int, senderId: Int, senderName: String, content: String) = display {
        it.groupMessage(groupId, senderId, senderName, content)
    }

    private fun display(action: (MessageNotificationSink) -> Unit) {
        val target = sink ?: return
        core.recoverOperation(onFailure = { failure -> }) {
            action(target)
        }
    }
}
