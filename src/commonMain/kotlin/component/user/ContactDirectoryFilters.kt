package component.user

import model.User

fun ContactDirectory.filtered(query: String): ContactDirectory {
    val keyword = query.trim()
    if (keyword.isEmpty()) return this
    fun matches(user: User) =
        user.username.contains(keyword, ignoreCase = true) || user.id.toString().contains(keyword)
    return ContactDirectory(friends.filter(::matches), groups.filter(::matches))
}
