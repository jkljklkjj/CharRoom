package core

import java.util.Properties

object ServerConfig {
    // SERVER_IP resolution order:
    // 1) JVM system property: -Dserver.ip=1.2.3.4
    // 2) environment variable SERVER_IP
    // 3) classpath resource server.properties (key: server.ip)
    // 4) fallback to "localhost"
    val SERVER_IP: String by lazy {
        // 1) system property
        val prop = try { System.getProperty("server.ip") } catch (_: Throwable) { null }
        if (!prop.isNullOrBlank()) return@lazy prop.trim()

        // 2) env var
        val env = try { System.getenv("SERVER_IP") } catch (_: Throwable) { null }
        if (!env.isNullOrBlank()) return@lazy env.trim()

        // 3) classpath properties
        try {
            val props = Properties()
            val stream = ServerConfig::class.java.classLoader.getResourceAsStream("server.properties")
            if (stream != null) {
                stream.use { props.load(it) }
                val cfg = props.getProperty("server.ip")
                if (!cfg.isNullOrBlank()) return@lazy cfg.trim()
            }
        } catch (_: Throwable) {
        }

        // 4) fallback
        "localhost"
    }

    const val NETTY_SERVER_PORT = 8080
    const val SPRING_SERVER_PORT = 8088
    var Token: String = ""
    var id: String = ""
}