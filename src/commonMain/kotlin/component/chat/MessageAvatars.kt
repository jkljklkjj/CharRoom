package component.chat

import androidx.compose.runtime.Composable

/** Optional avatar slots; click handling belongs to the supplied composable content. */
data class MessageAvatars(
    val senderAvatar: @Composable (() -> Unit)? = null,
    val myAvatar: @Composable (() -> Unit)? = null,
)
