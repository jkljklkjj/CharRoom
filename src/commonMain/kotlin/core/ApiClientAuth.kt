package core

// 本文件由 ApiClient.kt 拆分而来：认证 API（登录/注册/刷新/验证码）。
// 共享基础设施（httpClient / sendRequest / json / ApiResponse 等）见 ApiClient.kt。

import core.model.AppVersionInfo
import core.model.VersionCheckResult
import core.state.GlobalAppState
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import model.Group
import model.Message
import model.MessageType
import model.User

suspend fun loginTokens(account: String, password: String): LoginTokenBundle? {
    val requestBody = buildJsonObject {
        put("account", account)
        put("password", password)
        put("deviceType", ServerConfig.DEVICE_TYPE)
        put("deviceId", generateDeviceId())
    }

    val response = sendRequest<LoginTokenBundle>(
        path = ApiEndpoints.LOGIN,
        method = "POST",
        body = requestBody
    )

    if (response.isSuccess && response.data != null && response.data.accessToken.isNotBlank()) {
        return response.data
    }
//    throw Exception("登录失败")

    return null
}



suspend fun login(account: String, password: String): String? {
    return loginTokens(account, password)?.accessToken
}



suspend fun refreshTokenBundle(refreshToken: String): LoginTokenBundle? {
    if (refreshToken.isBlank()) return null

    val requestBody = buildJsonObject {
        put("refreshToken", refreshToken)
    }

    val response = sendRequest<LoginTokenBundle>(
        path = ApiEndpoints.REFRESH_TOKEN,
        method = "POST",
        body = requestBody
    )

    return response.data?.takeIf { it.accessToken.isNotBlank() }
}



suspend fun refreshAccessToken(refreshToken: String): String? {
    return refreshTokenBundle(refreshToken)?.accessToken
}



suspend fun validateToken(token: String): LoginTokenBundle? {
    if (token.isBlank()) return null

    val response = sendRequest<LoginTokenBundle>(
        path = ApiEndpoints.VALIDATE_TOKEN,
        method = "GET",
        token = token
    )

    // 只有code为0才认为成功，其他所有情况都返回null
    return response.data?.takeIf { response.isSuccess && it.accessToken.isNotBlank() }
}



suspend fun register(username: String, password: String, email: String = ""): Int? {
    val requestBody = buildJsonObject {
        put("username", username)
        put("password", password)
        put("email", email)
    }

    val response = sendRequest<Int>(
        path = ApiEndpoints.REGISTER,
        method = "POST",
        body = requestBody
    )

    return response.data
}



suspend fun verifyRegister(username: String, password: String, email: String = "", verifyCode: String = ""): Int? {
    val requestBody = buildJsonObject {
        put("username", username)
        put("password", password)
        put("email", email)
        put("verifyCode", verifyCode)
    }

    val response = sendRequest<Int>(
        path = ApiEndpoints.VERIFY_REGISTER,
        method = "POST",
        body = requestBody
    )

    return response.data
}



suspend fun sendRegisterVerifyCode(email: String): Boolean {
    val requestBody = buildJsonObject {
        put("email", email)
    }

    val response = sendRequest<Boolean>(
        path = ApiEndpoints.SEND_REGISTER_VERIFY_CODE,
        method = "POST",
        body = requestBody
    )

    return response.data == true
}


