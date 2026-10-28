package core

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** CLI non-streaming endpoint; protocol fields match AgentRequest.input and ApiResponse<String>. */
suspend fun agentChat(token: String, message: String): String {
    val result = sendRequest<String>(
        path = ApiEndpoints.AGENT_NL,
        method = "POST",
        body = buildJsonObject { put("input", message) },
        token = token
    )
    check(result.isSuccess) { "助手请求失败（${result.code}），请检查连接或额度后再试" }
    return checkNotNull(result.data) { "助手响应缺少正文" }
}
