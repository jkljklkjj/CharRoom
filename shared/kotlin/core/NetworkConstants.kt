package core

object NetworkConstants {
    // 全局统一使用WSS安全协议，CDN/反向代理会自动处理SSL卸载
    const val WS_PROTOCOL = "wss"
    const val WS_PORT = 443
    const val WS_PATH = "/ws"
    fun wsUrl(): String = "$WS_PROTOCOL://${ServerConfig.SERVER_IP}$WS_PATH"
    fun wsOrigin(): String = "https://${ServerConfig.SERVER_IP}"

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
