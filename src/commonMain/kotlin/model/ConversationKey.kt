package model

import kotlinx.serialization.Serializable

@Serializable enum class ConversationKind { PRIVATE, GROUP }

/** Local conversation identity. IDs always keep their server value; type prevents collisions. */
data class ConversationKey(val kind: ConversationKind, val id: Int) {
    init { require(id >= 0 && (kind != ConversationKind.GROUP || id > 0)) }
    val value: String get() = "${if (kind == ConversationKind.GROUP) "group" else "user"}:$id"
    companion object {
        fun privateChat(userId: Int) = ConversationKey(ConversationKind.PRIVATE, userId)
        fun group(groupId: Int) = ConversationKey(ConversationKind.GROUP, groupId)
    }
}
