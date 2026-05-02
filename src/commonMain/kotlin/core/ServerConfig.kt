package core

import java.util.Properties

object ServerConfig {
    const val AGENT_ASSISTANT_ID = 900000001
    const val AGENT_ASSISTANT_NAME = "AI助手"

    val SERVER_IP: String by lazy {
        val prop = try { System.getProperty("server.ip") } catch (_: Throwable) { null }
        if (!prop.isNullOrBlank()) return@lazy prop.trim()

        val env = try { System.getenv("SERVER_IP") } catch (_: Throwable) { null }
        if (!env.isNullOrBlank()) return@lazy env.trim()

        try {
            val props = Properties()
            val stream = ServerConfig::class.java.classLoader?.getResourceAsStream("server.properties")
            if (stream != null) {
                stream.use { props.load(it) }
                val cfg = props.getProperty("server.ip")
                if (!cfg.isNullOrBlank()) return@lazy cfg.trim()
            }
        } catch (_: Throwable) {
        }

        "chatlite.xin"
    }

    const val NETTY_SERVER_PORT = 80
    const val SPRING_SERVER_PORT = 80

    var Token: String = ""
    var id: String = ""

    fun isAgentAssistant(userId: Int): Boolean = userId == AGENT_ASSISTANT_ID
}
