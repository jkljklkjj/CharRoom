package model

/** The platform validates the owning account before delivering a navigation request. */
data class ChatNavigationRequest(val requestId: Long, val conversation: ConversationKey)
