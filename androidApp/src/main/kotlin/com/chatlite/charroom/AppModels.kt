package com.chatlite.charroom

sealed class Screen {
    object Login : Screen()
    object Register : Screen()
    object Users : Screen()
    object Profile : Screen()
    data class UserDetail(val user: LocalUser) : Screen()
    data class Chat(val user: LocalUser) : Screen()
}

data class LocalUser(
    val id: Int,
    val username: String,
    val online: Boolean = true,
    val avatarUrl: String? = null,
    val signature: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val isGroup: Boolean = false
)
