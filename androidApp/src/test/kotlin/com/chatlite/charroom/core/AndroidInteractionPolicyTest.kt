package com.chatlite.charroom.core

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class AndroidInteractionPolicyTest {
    @Test fun notificationRoutesPrivateAndGroupWithoutColliding() {
        val direct = NotificationDestination.parse("private", 42, 9)!!
        val group = NotificationDestination.parse("group", 42, 9)!!
        assertEquals(model.ConversationKey.privateChat(42), direct.conversation)
        assertEquals(model.ConversationKey.group(42), group.conversation)
        assertNotEquals(direct.tag, group.tag)
        assertNotEquals(direct.tag, NotificationDestination.parse("private", 42, 10)!!.tag)
    }
    @Test fun notificationCannotNavigateAnotherAccountOrMalformedTarget() {
        val destination = NotificationDestination.parse("private", 42, 9)!!
        assertTrue(destination.belongsTo(9))
        assertFalse(destination.belongsTo(10))
        assertFalse(destination.belongsTo(null))
        assertNull(NotificationDestination.parse("unknown", 42, 9))
        assertNull(NotificationDestination.parse("group", Int.MIN_VALUE, 9))
        assertNull(NotificationDestination.parse("private", 0, 9))
        assertNull(NotificationDestination.parse("private", 42, 0))
    }
    @Test fun fileReadAcceptsExactLimitAndRejectsUnknownOversizedContent() {
        assertArrayEquals(byteArrayOf(1,2,3), ByteArrayInputStream(byteArrayOf(1,2,3)).readBounded(3))
        try {
            ByteArrayInputStream(ByteArray(4)).readBounded(3)
            fail("oversized stream accepted")
        } catch (_: FileTooLargeException) {}
    }
    @Test fun imageSamplingBoundsLargeCameraPhotosAndPanoramas() {
        assertEquals(1, imageSampleSize(800, 600))
        val sample = imageSampleSize(12000, 9000)
        assertTrue(12000 / sample <= 2048)
        assertTrue((12000L / sample) * (9000L / sample) <= 4_194_304L)
        assertTrue(50000 / imageSampleSize(50000, 10) <= 2048)
    }
    @Test fun permissionPolicyDoesNotLoopAfterDenial() {
        assertTrue(shouldRequestNotificationPermission(33, false, false, false))
        assertTrue(shouldRequestNotificationPermission(36, false, true, true))
        assertFalse(shouldRequestNotificationPermission(36, false, true, false))
        assertFalse(shouldRequestNotificationPermission(32, false, false, false))
        assertFalse(shouldRequestNotificationPermission(36, true, false, false))
    }
}
