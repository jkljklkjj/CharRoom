package core

object ServerConfig {
    const val AGENT_ASSISTANT_ID = 900000001
    const val AGENT_ASSISTANT_NAME = "AI助手"

    var SERVER_IP: String = "chatlite.xin"

    // QUIC/Netty 服务端地址（可与 REST API 地址不同，如 quic.chatlite.xin）
    var QUIC_HOST: String = "quic.chatlite.xin"

    const val NETTY_SERVER_PORT = 80
    const val SPRING_SERVER_PORT = 80

    // QUIC 配置
    var QUIC_PORT = 9443
    var QUIC_ALPN = "custom"

    fun isAgentAssistant(userId: Int): Boolean = userId == AGENT_ASSISTANT_ID

    var DEVICE_TYPE: String = "desktop"
}
