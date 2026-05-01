package core

object ApiEndpoints {
    private val BASE = "https://${ServerConfig.SERVER_IP}/api"

    const val LOGIN = "/user/login"
    const val REGISTER = "/user/register"
    const val VALIDATE_TOKEN = "/user/validateToken"
    const val FRIEND_GET = "/friend/get"
    const val GROUP_GET = "/group/get"
    const val FRIEND_ADD = "/friend/add"
    const val GROUP_ADD = "/user/addgroup"
    const val FRIEND_REQUESTS = "/friend/requests"
    const val GROUP_REQUESTS = "/group/requests"
    const val FRIEND_ACCEPT = "/friend/accept"
    const val FRIEND_REJECT = "/friend/reject"
    const val GROUP_ACCEPT = "/group/accept"
    const val GROUP_REJECT = "/group/reject"
    const val USER_DETAIL = "/user/get"
    const val GROUP_DETAIL = "/group/get/detail"
    const val OFFLINE = "/message/getOfflineMessage"
    const val AGENT_NL = "/agent/nl"
    const val AGENT_NL_STREAM = "/agent/nl/stream"
    const val FILE_UPLOAD = "/file/upload"
    const val USER_PROFILE = "/user/profile"
    const val USER_PROFILE_UPDATE = "/user/profile/update"
    const val USER_PROFILE_UPDATE_EMAIL = "/user/profile/updateEmail"
    const val SEND_EMAIL_UPDATE_VERIFY_CODE = "/user/sendEmailUpdateVerifyCode"
    const val USER_AVATAR_UPLOAD = "/user/avatar/upload"

    fun url(path: String): String = BASE + path
}
