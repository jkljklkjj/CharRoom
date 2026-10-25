package component.user

import model.Group
import model.User
import java.text.Collator
import java.util.Locale

enum class SidebarTab { MESSAGES, CONTACTS }

data class ContactDirectory(val friends: List<User>, val groups: List<User>) {
    val size: Int get() = friends.size + groups.size
}

/** Group IDs are negative only in the sidebar; the chat screen uses the real positive ID. */
fun User.asContactGroup(): Group? = if (id < 0) Group(-id, username) else null

fun buildContactDirectory(users: List<User>, selfId: Int?, query: String, locale: Locale = Locale.getDefault()): ContactDirectory {
    val keyword = query.trim()
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    val sorted = users.asSequence()
        .filter { it.id != 0 && it.id != selfId }
        .distinctBy { it.id }
        .filter { keyword.isEmpty() || it.username.contains(keyword, ignoreCase = true) || kotlin.math.abs(it.id).toString().contains(keyword) }
        .sortedWith { a, b -> collator.compare(a.username, b.username).takeIf { it != 0 } ?: a.id.compareTo(b.id) }
        .toList()
    return ContactDirectory(sorted.filter { it.id > 0 }, sorted.filter { it.id < 0 })
}
