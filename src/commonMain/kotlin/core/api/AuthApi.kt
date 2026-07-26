package core

import kotlinx.serialization.json.*

suspend fun loginTokens(account: String, password: String): LoginTokenBundle? {
    val body = buildJsonObject {
        put("account", account); put("password", password)
        put("deviceType", ServerConfig.DEVICE_TYPE); put("deviceId", generateDeviceId())
    }
    return sendRequest<LoginTokenBundle>(ApiEndpoints.LOGIN, "POST", body).data?.takeIf { it.accessToken.isNotBlank() }
}

suspend fun login(account: String, password: String): String? = loginTokens(account, password)?.accessToken

suspend fun refreshTokenBundle(refreshToken: String): LoginTokenBundle? {
    if (refreshToken.isBlank()) return null
    return sendRequest<LoginTokenBundle>(ApiEndpoints.REFRESH_TOKEN, "POST", buildJsonObject { put("refreshToken", refreshToken) })
        .data?.takeIf { it.accessToken.isNotBlank() }
}

suspend fun refreshAccessToken(refreshToken: String): String? = refreshTokenBundle(refreshToken)?.accessToken

suspend fun validateToken(token: String): LoginTokenBundle? {
    if (token.isBlank()) return null
    val resp = sendRequest<LoginTokenBundle>(ApiEndpoints.VALIDATE_TOKEN, "GET", token = token)
    return resp.data?.takeIf { resp.isSuccess && it.accessToken.isNotBlank() }
}

suspend fun register(username: String, password: String, email: String = ""): Int? {
    val body = buildJsonObject { put("username", username); put("password", password); put("email", email) }
    return sendRequest<Int>(ApiEndpoints.REGISTER, "POST", body).data
}

suspend fun verifyRegister(username: String, password: String, email: String = "", verifyCode: String = ""): Int? {
    val body = buildJsonObject {
        put("username", username); put("password", password); put("email", email); put("verifyCode", verifyCode)
    }
    return sendRequest<Int>(ApiEndpoints.VERIFY_REGISTER, "POST", body).data
}

suspend fun sendRegisterVerifyCode(email: String): Boolean {
    return sendRequest<Boolean>(ApiEndpoints.SEND_REGISTER_VERIFY_CODE, "POST", buildJsonObject { put("email", email) }).data == true
}
