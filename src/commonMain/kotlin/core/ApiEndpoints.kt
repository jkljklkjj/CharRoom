package core

object ApiEndpoints {
    private var _customBase: String? = null
    private val BASE get() = _customBase ?: "https://${ServerConfig.SERVER_IP}/api"

    fun setBaseUrl(url: String) {
        _customBase = url.removeSuffix("/")
    }

    // Auth
    const val LOGIN = "/auth/login"
    const val REFRESH_TOKEN = "/auth/refresh"
    const val REGISTER = "/auth/register/verify"
    const val VERIFY_REGISTER = "/auth/register/verify"
    const val SEND_REGISTER_VERIFY_CODE = "/auth/verify-code"
    const val VALIDATE_TOKEN = "/auth/validate"

    // Users
    const val USER_DETAIL = "/users"
    const val USER_PROFILE = "/users/me/profile"
    const val USER_PROFILE_UPDATE = "/users/me/profile"
    const val USER_PROFILE_UPDATE_EMAIL = "/users/me/email"
    const val SEND_EMAIL_UPDATE_VERIFY_CODE = "/users/me/email/verify-code"
    const val USER_AVATAR_UPLOAD = "/users/me/avatar"

    // Friends
    const val FRIEND_GET = "/friends"
    const val FRIEND_ADD = "/friends"
    const val FRIEND_REQUESTS = "/friends/requests"
    const val FRIEND_ACCEPT = "/friends/accept"
    const val FRIEND_REJECT = "/friends/reject"
    const val FRIEND_DEL = "/friends"

    // Groups
    const val GROUP_GET = "/groups"
    const val GROUP_ADD = "/groups"
    const val GROUP_REQUESTS = "/groups/requests"
    const val GROUP_ACCEPT = "/groups/requests/accept"
    const val GROUP_REJECT = "/groups/requests/reject"
    const val GROUP_DETAIL = "/groups"

    // Messages
    const val OFFLINE = "/messages/offline"
    const val SYNC_MESSAGES = "/sync/messages"

    // Agent
    const val AGENT_NL = "/agent/nl"
    const val AGENT_NL_STREAM = "/agent/nl/stream"
    const val AGENT_QUOTA = "/agent/quota"
    const val AGENT_QUOTA_PRICES = "/agent/quota/prices"
    const val AGENT_QUOTA_PURCHASE = "/agent/quota/purchase"
    const val AGENT_QUOTA_PURCHASE_CONFIRM = "/agent/quota/purchase/confirm"

    // Files
    const val FILE_UPLOAD = "/files"

    // App
    const val APP_VERSION_CHECK = "/app/version/check"

    fun url(path: String): String = BASE + path
}
