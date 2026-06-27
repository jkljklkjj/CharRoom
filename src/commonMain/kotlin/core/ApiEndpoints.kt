package core

object ApiEndpoints {
    private var _customBase: String? = null
    private val BASE get() = _customBase ?: "https://${ServerConfig.SERVER_IP}/api"

    fun setBaseUrl(url: String) {
        _customBase = url.removeSuffix("/")
    }

    const val LOGIN = "/user/login"
    const val REFRESH_TOKEN = "/user/refreshToken"
    const val REGISTER = "/user/register"
    const val VERIFY_REGISTER = "/user/verifyregister"
    const val SEND_REGISTER_VERIFY_CODE = "/user/sendRegisterVerifyCode"
    const val VALIDATE_TOKEN = "/user/validateToken"
    const val FRIEND_GET = "/friend/get"
    const val GROUP_GET = "/group/get"
    const val FRIEND_ADD = "/friend/add"
    const val GROUP_ADD = "/user/addgroup"
    const val FRIEND_REQUESTS = "/friend/requests"
    const val GROUP_REQUESTS = "/group/requests"
    const val FRIEND_ACCEPT = "/friend/accept"
    const val FRIEND_REJECT = "/friend/reject"
    const val FRIEND_DEL = "/friend/del"
    const val GROUP_ACCEPT = "/group/accept"
    const val GROUP_REJECT = "/group/reject"
    const val USER_DETAIL = "/user/get"
    const val GROUP_DETAIL = "/group/get/detail"
    const val OFFLINE = "/message/getOfflineMessage"
    const val SYNC_MESSAGES = "/sync/messages"
    const val AGENT_NL = "/agent/nl"
    const val AGENT_NL_STREAM = "/agent/nl/stream"
    const val FILE_UPLOAD = "/file/upload"
    const val USER_PROFILE = "/user/profile"
    const val USER_PROFILE_UPDATE = "/user/profile/update"
    const val USER_PROFILE_UPDATE_EMAIL = "/user/profile/updateEmail"
    const val SEND_EMAIL_UPDATE_VERIFY_CODE = "/user/sendEmailUpdateVerifyCode"
    const val USER_AVATAR_UPLOAD = "/user/avatar/upload"
    const val APP_VERSION_CHECK = "/app/version/check"

    fun url(path: String): String = BASE + path
}
