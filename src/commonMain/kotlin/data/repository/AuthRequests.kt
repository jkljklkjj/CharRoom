package data.repository

import core.LoginTokenBundle
import core.getUserInfo
import core.loginTokens
import core.refreshTokenBundle
import core.validateToken
import model.User

/** Network operations used by authentication; tests can provide isolated responses. */
data class AuthRequests(
    val login: suspend (String, String) -> LoginTokenBundle? = ::loginTokens,
    val profile: suspend (String) -> User? = ::getUserInfo,
    val validate: suspend (String) -> LoginTokenBundle? = ::validateToken,
    val refresh: suspend (String) -> LoginTokenBundle? = ::refreshTokenBundle,
)
