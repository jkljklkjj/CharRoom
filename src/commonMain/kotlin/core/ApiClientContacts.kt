package core

// 本文件由 ApiClient.kt 拆分而来：联系人 API（好友/群组/申请/资料）。
// 共享基础设施（httpClient / sendRequest / json / ApiResponse 等）见 ApiClient.kt。

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

suspend fun getUserInfo(token: String): User? {
    val response = sendRequest<User>(
        path = ApiEndpoints.USER_PROFILE,
        method = "GET",
        token = token
    )

    return response.data
}



suspend fun getFriendList(token: String): List<User> {
    val response = sendRequest<List<User>>(
        path = ApiEndpoints.FRIEND_GET,
        method = "GET",
        token = token
    )

    return response.data ?: emptyList()
}



suspend fun getGroupList(token: String): List<Group> {
    val response = sendRequest<List<Group>>(
        path = ApiEndpoints.GROUP_GET,
        method = "GET",
        token = token
    )

    return response.data ?: emptyList()
}



suspend fun sendFriendRequest(token: String, targetUserId: Int, message: String = ""): Boolean {
    val requestBody = buildJsonObject {
        put("targetUserId", targetUserId)
        put("message", message)
    }

    val response = sendRequest<Unit>(
        path = ApiEndpoints.FRIEND_ADD,
        method = "POST",
        body = requestBody,
        token = token
    )

    return response.isSuccess
}



suspend fun deleteFriend(token: String, friendId: Int): Boolean {
    val response = sendRequest<Unit>(
        path = "${ApiEndpoints.FRIEND_DEL}/$friendId",
        method = "DELETE",
        token = token
    )
    return response.isSuccess
}



suspend fun handleFriendRequest(token: String, requestId: Int, accept: Boolean): Boolean {
    val endpoint = if (accept) ApiEndpoints.FRIEND_ACCEPT else ApiEndpoints.FRIEND_REJECT
    val requestBody = buildJsonObject {
        put("requestId", requestId)
    }

    val response = sendRequest<Unit>(
        path = endpoint,
        method = "POST",
        body = requestBody,
        token = token
    )

    return response.isSuccess
}



suspend fun getFriendRequests(token: String): List<FriendRequest> {
    val response = sendRequest<List<FriendRequest>>(
        path = ApiEndpoints.FRIEND_REQUESTS,
        method = "GET",
        token = token
    )

    return response.data ?: emptyList()
}



suspend fun getGroupRequests(token: String): List<FriendRequest> {
    val response = sendRequest<List<FriendRequest>>(
        path = ApiEndpoints.GROUP_REQUESTS,
        method = "GET",
        token = token
    )

    return response.data ?: emptyList()
}



suspend fun addFriend(token: String, account: String): Boolean {
    val requestBody = buildJsonObject {
        put("account", account)
    }

    val response = sendRequest<Unit>(
        path = ApiEndpoints.FRIEND_ADD,
        method = "POST",
        body = requestBody,
        token = token
    )

    return response.isSuccess
}



suspend fun addGroup(token: String, groupId: String): ApiResponse<Unit> {
    return sendRequest<Unit>(
        path = "${ApiEndpoints.GROUP_ADD}/${groupId}/join",
        method = "POST",
        token = token
    )
}



suspend fun getUserDetail(token: String, userId: String): User? {
    val response = sendRequest<User>(
        path = "${ApiEndpoints.USER_DETAIL}?userId=$userId",
        method = "GET",
        token = token
    )

    return response.data
}



suspend fun getGroupDetail(token: String, groupId: String): Group? {
    val path = "${ApiEndpoints.GROUP_DETAIL}/$groupId"
    val response = sendRequest<Group>(
        path = path,
        method = "GET",
        token = token
    )

    return response.data
}



suspend fun acceptFriend(token: String, requestId: String): Boolean {
    val requestBody = buildJsonObject {
        put("requestId", requestId)
    }

    val response = sendRequest<Unit>(
        path = ApiEndpoints.FRIEND_ACCEPT,
        method = "POST",
        body = requestBody,
        token = token
    )

    return response.isSuccess
}



suspend fun rejectFriend(token: String, requestId: String): Boolean {
    val requestBody = buildJsonObject {
        put("requestId", requestId)
    }

    val response = sendRequest<Unit>(
        path = ApiEndpoints.FRIEND_REJECT,
        method = "POST",
        body = requestBody,
        token = token
    )

    return response.isSuccess
}



suspend fun acceptGroupApplication(token: String, groupId: String, userId: String): Boolean {
    val requestBody = buildJsonObject {
        put("groupId", groupId)
        put("userId", userId)
    }

    val response = sendRequest<Unit>(
        path = ApiEndpoints.GROUP_ACCEPT,
        method = "POST",
        body = requestBody,
        token = token
    )

    return response.isSuccess
}



suspend fun rejectGroupApplication(token: String, groupId: String, userId: String): Boolean {
    val requestBody = buildJsonObject {
        put("groupId", groupId)
        put("userId", userId)
    }

    val response = sendRequest<Unit>(
        path = ApiEndpoints.GROUP_REJECT,
        method = "POST",
        body = requestBody,
        token = token
    )

    return response.isSuccess
}



suspend fun createGroup(token: String, name: String, memberIds: List<Int>): Group? {
    val requestBody = buildJsonObject {
        put("name", name)
        putJsonArray("memberIds") {
            memberIds.forEach { add(it) }
        }
    }

    val response = sendRequest<Group>(
        path = ApiEndpoints.GROUP_ADD,
        method = "POST",
        body = requestBody,
        token = token
    )

    return response.data
}



suspend fun inviteToGroup(token: String, groupId: Int, userId: Int): Boolean {
    val requestBody = buildJsonObject {
        put("userId", userId)
    }

    val response = sendRequest<Unit>(
        path = "${ApiEndpoints.GROUP_ADD}/$groupId/members",
        method = "POST",
        body = requestBody,
        token = token
    )

    return response.isSuccess
}



suspend fun leaveGroup(token: String, groupId: Int): Boolean {
    val response = sendRequest<Unit>(
        path = "${ApiEndpoints.GROUP_GET}/$groupId/leave",
        method = "POST",
        token = token
    )

    return response.isSuccess
}



suspend fun searchUser(token: String, keyword: String): List<User> {
    val path = "${ApiEndpoints.USER_DETAIL}?keyword=$keyword"
    val response = sendRequest<List<User>>(
        path = path,
        method = "GET",
        token = token
    )

    return response.data ?: emptyList()
}


