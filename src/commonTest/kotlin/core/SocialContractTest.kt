package core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.serialization.decodeFromString
import model.EncounterRoom
import model.SocialProfile

class SocialContractTest {
    @Test fun currentFriendRequestHasTheNameAvatarAndIntroduction() {
        val request=json.decodeFromString<FriendRequest>("""{"id":42,"username":"Fixture","avatarUrl":"/avatar.png","reason":"Hello"}""")
        assertEquals("Fixture",request.senderName)
        assertEquals("/avatar.png",request.senderAvatar)
        assertEquals("Hello",request.message)
    }
    @Test fun legacyFriendRequestStillDecodes() {
        val request=json.decodeFromString<FriendRequest>("""{"id":42,"senderId":42,"senderName":"Legacy","message":"Hello","status":0,"createTime":1}""")
        assertEquals("Legacy",request.senderName)
        assertEquals("Hello",request.message)
    }
    @Test fun hiddenProfileDoesNotNeedPrivateFields() {
        val profile=json.decodeFromString<SocialProfile>("""{"id":"42","username":"Fixture","relation":"STRANGER","detailsVisible":false,"cover":"sunset"}""")
        assertNull(profile.bio);assertNull(profile.interests);assertNull(profile.settings)
    }
    @Test fun encounterUsesRoomLocalIdentityAndPreservesMessageIds() {
        val room=json.decodeFromString<EncounterRoom>("""{"state":"ACTIVE","roomId":"room-1","mySlot":"A","messages":[{"seq":1,"author":"B","content":"Hello","createdAt":1,"messageId":"stable-id"}]}""")
        assertEquals("stable-id",room.messages.single().messageId)
        assertFalse(room.exchanged);assertNull(room.peer)
    }
}
