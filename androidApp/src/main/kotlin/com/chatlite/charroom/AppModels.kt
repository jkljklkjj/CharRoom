package com.chatlite.charroom

sealed class Screen {
    object Login : Screen()
    object Register : Screen()
    object Users : Screen()
    data class Chat(val user: LocalUser) : Screen()
}

data class LocalUser(val id: Int, val username: String, val online: Boolean = true)
