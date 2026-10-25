package component.chat

import androidx.compose.runtime.Composable
import component.user.asContactGroup
import model.User
import presentation.viewmodel.ChatViewModel

/** Route sidebar entries to the matching private/group history and send actions. */
@Composable
fun ContactChatScreen(
    chatViewModel: ChatViewModel,
    user: User,
    token: String,
    onAvatarClick: ((User) -> Unit)? = null,
    onMyAvatarClick: (() -> Unit)? = null,
    onBackClick: (() -> Unit)? = null
) {
    val group = user.asContactGroup()
    if (group != null) GroupChatScreen(
        chatViewModel, group, token,
        onBack = onBackClick ?: { chatViewModel.selectedUser = null },
        onAvatarClick = onAvatarClick, onMyAvatarClick = onMyAvatarClick
    ) else ChatScreen(chatViewModel, user, onAvatarClick, onMyAvatarClick, onBackClick)
}
