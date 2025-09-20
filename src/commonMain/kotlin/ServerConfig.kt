import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ServerConfig {
    const val SERVER_IP = "localhost"
    const val NETTY_SERVER_PORT = 8080
    const val SPRING_SERVER_PORT = 8088
    var Token: String by mutableStateOf("")
    // 当前用户账号
    var id: String by mutableStateOf("")
}