package core

// 本文件由 ApiClient.kt 拆分而来：Token 配额 API（配额/价格/购买）。
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

suspend fun getTokenQuota(token: String): model.QuotaInfo? {
    val response =
        sendRequest<model.QuotaInfo>(
            path = ApiEndpoints.AGENT_QUOTA,
            token = token,
        )
    return if (response.isSuccess) response.data else null
}

suspend fun getTokenPrices(token: String): model.TokenPrices? {
    val response =
        sendRequest<model.TokenPrices>(
            path = ApiEndpoints.AGENT_QUOTA_PRICES,
            token = token,
        )
    return if (response.isSuccess) response.data else null
}

suspend fun purchaseTokens(
    token: String,
    amountFen: Int,
): model.PayResult? {
    val requestBody =
        buildJsonObject {
            put("amount", amountFen)
        }
    val response =
        sendRequest<model.PayResult>(
            path = ApiEndpoints.AGENT_QUOTA_PURCHASE,
            method = "POST",
            body = requestBody,
            token = token,
        )
    return if (response.isSuccess) response.data else null
}

/**
 * 查询订单状态（只读）。
 * 服务端会在 pending 时主动向支付方查单并入账，客户端不直接修改余额。
 */
suspend fun getPurchaseStatus(
    token: String,
    purchaseId: Long,
): model.OrderStatus? {
    val response =
        sendRequest<model.OrderStatus>(
            path = "${ApiEndpoints.AGENT_QUOTA_PURCHASE_STATUS}/$purchaseId",
            token = token,
        )
    return if (response.isSuccess) response.data else null
}
