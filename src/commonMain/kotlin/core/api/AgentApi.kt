package core

import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.*
import model.QuotaInfo
import model.PayResult
import model.TokenPrices

suspend fun getTokenQuota(token: String): QuotaInfo? = sendRequest<QuotaInfo>(ApiEndpoints.AGENT_QUOTA, token = token).let { if (it.isSuccess) it.data else null }

suspend fun getTokenPrices(token: String): TokenPrices? = sendRequest<TokenPrices>(ApiEndpoints.AGENT_QUOTA_PRICES, token = token).let { if (it.isSuccess) it.data else null }

suspend fun purchaseTokens(token: String, amountFen: Int): PayResult? = sendRequest<PayResult>(ApiEndpoints.AGENT_QUOTA_PURCHASE, "POST", buildJsonObject { put("amount", amountFen) }, token).let { if (it.isSuccess) it.data else null }

suspend fun confirmPurchase(token: String, purchaseId: Long): Boolean = sendRequest<Unit>("${ApiEndpoints.AGENT_QUOTA_PURCHASE_CONFIRM}?purchaseId=$purchaseId", "POST", token = token).isSuccess

suspend fun agentChat(token: String, message: String, stream: Boolean = false): String = try {
    httpClient.post(ApiEndpoints.url(ApiEndpoints.AGENT_NL)) {
        header(HttpHeaders.Authorization, "Bearer $token")
        setBody(buildJsonObject { put("message", message); put("stream", stream) })
        timeout { requestTimeoutMillis = 30000 }
    }.bodyAsText()
} catch (_: Exception) { "" }

suspend fun getDevices(token: String): List<DeviceInfo> = sendRequest<List<DeviceInfo>>(ApiEndpoints.SYNC_DEVICES, "GET", token = token).data ?: emptyList()
