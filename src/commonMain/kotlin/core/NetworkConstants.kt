package core

object NetworkConstants {
    const val WS_PROTOCOL = "wss"
    const val HTTP_PROTOCOL = "https"
    const val WS_PORT = 443
    const val WS_PATH = "/ws"

    fun wsUrl(): String = "$WS_PROTOCOL://${ServerConfig.SERVER_IP}$WS_PATH"
    fun wsOrigin(): String = "$HTTP_PROTOCOL://${ServerConfig.SERVER_IP}"
}
