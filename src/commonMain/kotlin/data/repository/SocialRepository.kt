package data.repository

import core.ApiEndpoints
import core.sendRequest
import io.ktor.http.encodeURLQueryComponent
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import model.*

/** Bound to the login that created this screen; never switches credentials mid-request. */
class SocialRepository(private val token: String) {
    private suspend inline fun <reified T> request(
        path: String,
        method: String = "GET",
        body: Any? = null,
    ): T {
        val response = sendRequest<T>(ApiEndpoints.SOCIAL + path, method, body, token)
        currentCoroutineContext().ensureActive()
        check(response.isSuccess && response.data != null) {
            response.message ?: "HTTP request failed"
        }
        return response.data
    }

    suspend fun directory(
        query: String = "",
        tag: String = "",
        channel: String = "all",
        cursor: Int = 0,
    ) =
        request<SocialDirectory>(
            "/discovery?q=${query.encodeURLQueryComponent()}&" +
                "tag=${tag.encodeURLQueryComponent()}&channel=$channel&cursor=$cursor"
        )

    suspend fun profile(id: String) = request<SocialProfile>("/profiles/${id.toInt()}")

    suspend fun save(edit: SocialEdit) = request<SocialProfile>("/profile", "PUT", edit)

    suspend fun daily(cursor: String = "") =
        request<DailyQuestion>("/daily?cursor=${cursor.encodeURLQueryComponent()}")

    suspend fun answer(day: String, content: String) =
        request<Boolean>(
            "/daily",
            "PUT",
            buildJsonObject {
                put("day", day)
                put("content", content)
            },
        )

    suspend fun room(after: Int) = request<EncounterRoom>("/encounter?after=$after")

    suspend fun act(action: EncounterAction) = request<EncounterRoom>("/encounter", "POST", action)

    suspend fun friend(id: String, reason: String) =
        request<Boolean>(
            "/friend-requests",
            "POST",
            buildJsonObject {
                put("targetId", id)
                put("reason", reason)
            },
        )

    suspend fun block(id: String) = request<Boolean>("/blocks/${id.toInt()}", "POST")

    suspend fun blocked() = request<List<SocialBlockedUser>>("/blocks")

    suspend fun unblock(id: Int) = request<Boolean>("/blocks/$id", "DELETE")

    suspend fun report(id: String?, roomId: String?, reason: String) =
        request<Boolean>(
            "/reports",
            "POST",
            buildJsonObject {
                id?.let { put("targetId", it.toInt()) }
                roomId?.let { put("roomId", it) }
                put("reason", reason)
            },
        )
}
