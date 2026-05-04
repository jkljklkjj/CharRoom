package core

import java.util.Properties

object ServerConfig {
    const val AGENT_ASSISTANT_ID = 900000001
    const val AGENT_ASSISTANT_NAME = "AI助手"

    val SERVER_IP: String = "chatlite.xin"
//    val SERVER_IP: String by lazy {
//        // 本地开发时可以取消注释以下代码来切换服务器地址
//        // 优先使用系统属性和环境变量（用于本地开发和CI覆盖）
//        val prop = try { System.getProperty("server.ip") } catch (_: Throwable) { null }
//        if (!prop.isNullOrBlank()) return@lazy prop.trim()
//
//        val env = try { System.getenv("SERVER_IP") } catch (_: Throwable) { null }
//        if (!env.isNullOrBlank()) return@lazy env.trim()
//
//        // 尝试加载配置文件（本地开发用）
//        try {
//            val props = Properties()
//            // 尝试两种路径格式，兼容不同平台的类加载器
//            var stream = ServerConfig::class.java.classLoader?.getResourceAsStream("server.properties")
//            if (stream == null) {
//                stream = ServerConfig::class.java.getResourceAsStream("/server.properties")
//            }
//            if (stream != null) {
//                stream.use { props.load(it) }
//                val cfg = props.getProperty("server.ip")
//                if (!cfg.isNullOrBlank()) return@lazy cfg.trim()
//            }
//        } catch (_: Throwable) {
//        }
//
//        // 默认使用生产环境地址
//        "chatlite.xin"
//    }

    const val NETTY_SERVER_PORT = 80
    const val SPRING_SERVER_PORT = 80

    var Token: String = ""
    var id: String = ""

    fun isAgentAssistant(userId: Int): Boolean = userId == AGENT_ASSISTANT_ID
}
