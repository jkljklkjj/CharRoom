package core

object ApiEndpoints {
    private val BASE = "http://${ServerConfig.SERVER_IP}/api"

    const val LOGIN = "/user/login"
    const val REGISTER = "/user/register"
    const val VALIDATE_TOKEN = "/user/validateToken"
    const val FRIEND_GET = "/friend/get"
    const val GROUP_GET = "/group/get"
    const val FRIEND_ADD = "/friend/add"
    const val FRIEND_REQUESTS = "/friend/requests"
    const val FRIEND_ACCEPT = "/friend/accept"
    const val GROUP_ADD = "/user/addgroup"
    const val USER_DETAIL = "/user/get"
    const val GROUP_DETAIL = "/group/getDetail"
    const val OFFLINE = "/message/getOfflineMessage"
    const val AGENT_NL = "/agent/nl"
    const val AGENT_NL_STREAM = "/agent/nl/stream"

    fun url(path: String): String = BASE + path
}
