package model

import kotlinx.serialization.Serializable

@Serializable
data class SocialSettings(val userId: Int = 0, val bio: String = "", val interests: List<String> = emptyList(), val city: String = "", val intent: String = "", val cover: String = "sunset", val visibility: String = "SELF", val discoverable: Boolean = false, val mutualDiscovery: Boolean = false)
@Serializable
data class SocialEdit(val username: String, val signature: String = "", val bio: String = "", val interests: List<String> = emptyList(), val city: String = "", val intent: String = "", val cover: String = "sunset", val visibility: String = "SELF", val discoverable: Boolean = false, val mutualDiscovery: Boolean = false)
@Serializable
data class SocialProfile(val id: String, val username: String, val avatarUrl: String? = null, val avatarKey: String? = null, val signature: String? = null, val bio: String? = null, val interests: List<String>? = null, val city: String? = null, val intent: String? = null, val cover: String = "sunset", val relation: String = "STRANGER", val detailsVisible: Boolean = false, val settings: SocialSettings? = null, val reason: String? = null) {
    fun asUser() = User(id.toInt(), username, signature = signature, avatarUrl = avatarUrl, avatarKey = avatarKey)
}
@Serializable data class SocialDirectory(val users: List<SocialProfile> = emptyList(), val nextCursor: Int? = null)
@Serializable data class DailyAnswer(val id: String, val content: String, val createdAt: String, val author: SocialProfile)
@Serializable data class DailyQuestion(val day: String, val question: String, val myAnswer: String = "", val answers: List<DailyAnswer> = emptyList(), val nextCursor: String? = null)
@Serializable data class EncounterMessage(val seq: Int, val author: String, val content: String, val createdAt: Long, val messageId: String)
@Serializable data class EncounterRoom(val state: String = "IDLE", val roomId: String? = null, val serverTime: Long = 0L, val endsAt: Long = 0L, val question: String = "", val mySlot: String = "", val reason: String = "", val myReady: Boolean = false, val extendRequested: Boolean = false, val exchangeRequested: Boolean = false, val extensions: Int = 0, val exchanged: Boolean = false, val messages: List<EncounterMessage> = emptyList(), val nextSeq: Int = 0, val peer: SocialProfile? = null)
@Serializable data class EncounterAction(val action: String, val roomId: String? = null, val messageId: String? = null, val content: String? = null)
@Serializable data class SocialBlockedUser(val id: Int, val username: String)
