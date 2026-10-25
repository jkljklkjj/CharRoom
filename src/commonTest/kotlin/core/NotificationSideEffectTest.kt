package core

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationSideEffectTest {
    @Test fun notificationFailureCannotInterruptTheMessageReceiver() {
        PlatformMessageNotifications.sink = object : MessageNotificationSink {
            override fun privateMessage(senderId: Int, content: String) { error("fixture display unavailable") }
            override fun groupMessage(groupId: Int, senderId: Int, senderName: String, content: String) { error("fixture display unavailable") }
        }
        try {
            var applied = 0
            applied++
            PlatformMessageNotifications.privateMessage(42, "fixture")
            PlatformMessageNotifications.groupMessage(7, 42, "fixture", "fixture")
            assertEquals(1, applied)
        } finally { PlatformMessageNotifications.sink = null }
    }
}
