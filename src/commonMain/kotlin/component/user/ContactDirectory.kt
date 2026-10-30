package component.user

import java.text.Collator
import java.util.Locale
import model.Group
import model.User

enum class SidebarTab {
    MESSAGES,
    CONTACTS,
}

data class ContactDirectory(val friends: List<User>, val groups: List<User>) {
    val size: Int
        get() = friends.size + groups.size
}

/** The contact kind selects routing; the ID is always the server ID. */
fun User.asContactGroup(): Group? = if (isGroup) Group(id, username) else null

fun buildContactDirectory(
    users: List<User>,
    selfId: Int?,
    locale: Locale = Locale.getDefault(),
): ContactDirectory {
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    val sorted =
        users
            .asSequence()
            .filter { it.isGroup || (it.id != 0 && it.id != selfId) }
            .distinctBy { it.conversationKey }
            .sortedWith { a, b ->
                collator.compare(a.username, b.username).takeIf { it != 0 } ?: a.id.compareTo(b.id)
            }
            .toList()
    return ContactDirectory(sorted.filterNot { it.isGroup }, sorted.filter { it.isGroup })
}

private data class RankedContact(val user: User, val time: Long, val unread: Int)

/** Snapshot keys/activity once; stable sorting retains source order for ties. */
fun sortConversationUsers(
    users: List<User>,
    activity: (model.ConversationKey) -> Pair<Long, Int>,
): List<User> =
    users
        .map { user ->
            val (time, unread) = activity(user.conversationKey)
            RankedContact(user, time, unread)
        }
        .sortedWith(compareByDescending<RankedContact> { it.time }.thenByDescending { it.unread })
        .map { it.user }
