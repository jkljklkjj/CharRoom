package core

import core.state.GlobalAppState

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

    /**
     * 全局认证令牌，已废弃，请使用GlobalAppState.currentToken或者通过参数传递token
     */
    @Deprecated(
        "Use GlobalAppState.currentToken or pass token as parameter",
        ReplaceWith("GlobalAppState.currentToken")
    )
    var Token: String
        get() = GlobalAppState.currentToken.orEmpty()
        set(value) {
            // 兼容旧代码，设置Token时更新AppState
            val currentState = GlobalAppState.authState.value
            if (currentState is core.state.AuthState.Authenticated) {
                GlobalAppState.updateAuthState(
                    currentState.copy(accessToken = value)
                )
            }
        }

    fun isAgentAssistant(userId: Int): Boolean = userId == AGENT_ASSISTANT_ID

    var DEVICE_TYPE: String = "desktop"
}
