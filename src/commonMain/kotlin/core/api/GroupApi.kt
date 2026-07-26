package core

import kotlinx.serialization.json.*
import model.Group

suspend fun addGroup(token: String, groupId: String): ApiResponse<Unit> =
    sendRequest<Unit>("${ApiEndpoints.GROUP_ADD}/$groupId/join", "POST", token = token)

suspend fun getGroupRequests(token: String): List<FriendRequest> =
    sendRequest<List<FriendRequest>>(ApiEndpoints.GROUP_REQUESTS, "GET", token = token).data ?: emptyList()

suspend fun acceptGroupApplication(token: String, groupId: String, userId: String): Boolean =
    sendRequest<Unit>(ApiEndpoints.GROUP_ACCEPT, "POST", buildJsonObject { put("groupId", groupId); put("userId", userId) }, token).isSuccess

suspend fun rejectGroupApplication(token: String, groupId: String, userId: String): Boolean =
    sendRequest<Unit>(ApiEndpoints.GROUP_REJECT, "POST", buildJsonObject { put("groupId", groupId); put("userId", userId) }, token).isSuccess

suspend fun createGroup(token: String, name: String, memberIds: List<Int>): Group? {
    val body = buildJsonObject { put("name", name); putJsonArray("memberIds") { memberIds.forEach { add(it) } } }
    return sendRequest<Group>(ApiEndpoints.GROUP_ADD, "POST", body, token).data
}

suspend fun inviteToGroup(token: String, groupId: Int, userId: Int): Boolean =
    sendRequest<Unit>("${ApiEndpoints.GROUP_ADD}/$groupId/members", "POST", buildJsonObject { put("userId", userId) }, token).isSuccess

suspend fun leaveGroup(token: String, groupId: Int): Boolean =
    sendRequest<Unit>("${ApiEndpoints.GROUP_GET}/$groupId/leave", "POST", token = token).isSuccess
