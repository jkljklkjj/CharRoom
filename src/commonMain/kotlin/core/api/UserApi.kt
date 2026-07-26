package core

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.client.call.*
import kotlinx.serialization.json.*
import model.Group
import model.User

suspend fun getUserInfo(token: String): User? = sendRequest<User>(ApiEndpoints.USER_PROFILE, "GET", token = token).data

suspend fun getFriendList(token: String): List<User> = sendRequest<List<User>>(ApiEndpoints.FRIEND_GET, "GET", token = token).data ?: emptyList()

suspend fun getGroupList(token: String): List<Group> = sendRequest<List<Group>>(ApiEndpoints.GROUP_GET, "GET", token = token).data ?: emptyList()

suspend fun getUserDetail(token: String, userId: String): User? = sendRequest<User>("${ApiEndpoints.USER_DETAIL}?userId=$userId", "GET", token = token).data

suspend fun getGroupDetail(token: String, groupId: String): Group? = sendRequest<Group>("${ApiEndpoints.GROUP_DETAIL}/$groupId", "GET", token = token).data

suspend fun searchUser(token: String, keyword: String): List<User> = sendRequest<List<User>>("${ApiEndpoints.USER_DETAIL}?keyword=$keyword", "GET", token = token).data ?: emptyList()

suspend fun sendFriendRequest(token: String, targetUserId: Int, message: String = ""): Boolean {
    return sendRequest<Unit>(ApiEndpoints.FRIEND_ADD, "POST", buildJsonObject { put("targetUserId", targetUserId); put("message", message) }, token).isSuccess
}

suspend fun addFriend(token: String, account: String): Boolean {
    return sendRequest<Unit>(ApiEndpoints.FRIEND_ADD, "POST", buildJsonObject { put("account", account) }, token).isSuccess
}

suspend fun deleteFriend(token: String, friendId: Int): Boolean = sendRequest<Unit>("${ApiEndpoints.FRIEND_DEL}/$friendId", "DELETE", token = token).isSuccess

suspend fun getFriendRequests(token: String): List<FriendRequest> = sendRequest<List<FriendRequest>>(ApiEndpoints.FRIEND_REQUESTS, "GET", token = token).data ?: emptyList()

suspend fun handleFriendRequest(token: String, requestId: Int, accept: Boolean): Boolean {
    val endpoint = if (accept) ApiEndpoints.FRIEND_ACCEPT else ApiEndpoints.FRIEND_REJECT
    return sendRequest<Unit>(endpoint, "POST", buildJsonObject { put("requestId", requestId) }, token).isSuccess
}

suspend fun acceptFriend(token: String, requestId: String): Boolean = handleFriendRequest(token, requestId.toInt(), true)

suspend fun rejectFriend(token: String, requestId: String): Boolean = handleFriendRequest(token, requestId.toInt(), false)

suspend fun updateUserProfile(token: String, username: String, phone: String, signature: String, password: String? = null): Boolean {
    val body = buildJsonObject {
        put("username", username); put("phone", phone); put("signature", signature)
        password?.let { put("password", it) }
    }
    return sendRequest<Unit>(ApiEndpoints.USER_PROFILE_UPDATE, "PUT", body, token).isSuccess
}

suspend fun updateEmail(token: String, newEmail: String, verifyCode: String): Boolean {
    return sendRequest<Unit>(ApiEndpoints.USER_PROFILE_UPDATE_EMAIL, "PUT", buildJsonObject { put("email", newEmail); put("verifyCode", verifyCode) }, token).isSuccess
}

suspend fun sendEmailUpdateVerifyCode(token: String, email: String): Boolean {
    return sendRequest<Unit>(ApiEndpoints.SEND_EMAIL_UPDATE_VERIFY_CODE, "POST", buildJsonObject { put("email", email) }, token).isSuccess
}

suspend fun batchOnlineStatus(token: String, userIds: List<Int>): Map<String, Boolean> {
    if (userIds.isEmpty()) return emptyMap()
    return try {
        val resp = httpClient.post(ApiEndpoints.url("/users/online-status")) {
            header("Authorization", "Bearer $token"); contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("userIds", userIds.joinToString(",")) })
        }
        resp.body<ApiResponse<Map<String, Boolean>>>().data ?: emptyMap()
    } catch (_: Exception) { emptyMap() }
}
