package core

// 本文件由 ApiClient.kt 拆分而来：个人资料 API（资料/邮箱/密码/版本/头像）。
// 共享基础设施（httpClient / sendRequest / json / ApiResponse 等）见 ApiClient.kt。

import core.model.VersionCheckResult
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*

suspend fun sendEmailUpdateVerifyCode(token: String, email: String): Boolean {
    val requestBody = buildJsonObject {
        put("email", email)
    }

    val response =
        sendRequest<Unit>(
            path = ApiEndpoints.SEND_EMAIL_UPDATE_VERIFY_CODE,
            method = "POST",
            body = requestBody,
            token = token,
        )

    return response.isSuccess
}

suspend fun updateUserProfile(
    token: String,
    username: String,
    phone: String,
    signature: String,
    password: String? = null,
): Boolean {
    val requestBody = buildJsonObject {
        put("username", username)
        put("phone", phone)
        put("signature", signature)
        password?.let { put("password", it) }
    }

    val response =
        sendRequest<Unit>(
            path = ApiEndpoints.USER_PROFILE_UPDATE,
            method = "PUT",
            body = requestBody,
            token = token,
        )

    return response.isSuccess
}

suspend fun updateEmail(token: String, newEmail: String, verifyCode: String): Boolean {
    val requestBody = buildJsonObject {
        put("email", newEmail)
        put("verifyCode", verifyCode)
    }

    val response =
        sendRequest<Unit>(
            path = ApiEndpoints.USER_PROFILE_UPDATE_EMAIL,
            method = "PUT",
            body = requestBody,
            token = token,
        )

    return response.isSuccess
}

suspend fun updateUserInfo(
    token: String,
    nickname: String? = null,
    signature: String? = null,
): Boolean {
    val requestBody = buildJsonObject {
        nickname?.let { put("username", it) }
        signature?.let { put("signature", it) }
    }

    val response =
        sendRequest<Unit>(
            path = ApiEndpoints.USER_PROFILE_UPDATE,
            method = "PUT",
            body = requestBody,
            token = token,
        )

    return response.isSuccess
}

suspend fun changePassword(token: String, newPassword: String): Boolean {
    val requestBody = buildJsonObject {
        put("password", newPassword)
    }

    val response =
        sendRequest<Unit>(
            path = ApiEndpoints.USER_PROFILE_UPDATE,
            method = "PUT",
            body = requestBody,
            token = token,
        )

    return response.isSuccess
}

suspend fun checkAppVersion(appVersion: String, platform: String): VersionCheckResult? {
    val query =
        io.ktor.http.Parameters.build {
            append("clientVersion", appVersion)
            append("platform", platform)
            append("os", core.update.UpdateRuntime.target.os)
            append("architecture", core.update.UpdateRuntime.target.architecture)
        }
    val response =
        sendRequest<core.model.PublishedAppVersion>(
            path = ApiEndpoints.APP_VERSION_CHECK + "?" + query.formUrlEncode(),
            method = "GET",
            token = null,
        )
    return response.data?.toCheckResult()
}

suspend fun uploadAvatar(token: String, fileBytes: ByteArray, fileName: String): String? =
    uploadFile(ApiEndpoints.USER_AVATAR_UPLOAD, fileBytes, fileName, token)
