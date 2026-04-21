package core

object NetworkConstants {
    fun wsUrl(): String = "ws://${ServerConfig.SERVER_IP}/ws"
    fun wsOrigin(): String = "http://${ServerConfig.SERVER_IP}"

    object WsMessageType {
        const val LOGIN = "login"
        const val LOGOUT = "logout"
        const val CHAT = "chat"
        const val AGENT_CHAT = "agentChat"
        const val AGENT_CHAT_STREAM = "agentChatStream"
        const val GROUP_CHAT = "groupChat"
        const val CHECK = "check"
        const val HEARTBEAT = "heartbeat"
    }
}
