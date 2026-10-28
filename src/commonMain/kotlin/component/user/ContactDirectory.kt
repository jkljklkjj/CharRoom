package component.user

import model.Group
import model.User
import java.text.Collator
import java.util.Locale

enum class SidebarTab { MESSAGES, CONTACTS }

data class ContactDirectory(val friends: List<User>, val groups: List<User>) {
    val size: Int get() = friends.size + groups.size
}

/** The contact kind selects routing; the ID is always the server ID. */
fun User.asContactGroup(): Group? = if (isGroup) Group(id, username) else null

fun buildContactDirectory(users: List<User>, selfId: Int?, query: String, locale: Locale = Locale.getDefault()): ContactDirectory {
    val keyword = query.trim()
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    val sorted = users.asSequence()
        .filter { it.isGroup || (it.id != 0 && it.id != selfId) }
        .distinctBy { it.conversationKey }
        .filter { keyword.isEmpty() || it.username.contains(keyword, ignoreCase = true) || it.id.toString().contains(keyword) }
        .sortedWith { a, b -> collator.compare(a.username, b.username).takeIf { it != 0 } ?: a.id.compareTo(b.id) }
        .toList()
    return ContactDirectory(sorted.filterNot { it.isGroup }, sorted.filter { it.isGroup })
}
