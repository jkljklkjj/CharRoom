package model

import kotlinx.serialization.Serializable

@Serializable
data class QuotaInfo(
    val weeklyInputUsed: Long = 0,
    val weeklyOutputUsed: Long = 0,
    val weeklyCostFen: Int = 0,
    val weeklyFreeLimitFen: Int = 400,
    val freeRemainingFen: Int = 400,
    val balanceFen: Long = 0
) {
    val balanceYuan: Double get() = balanceFen / 100.0
    val freeRemainingYuan: Double get() = freeRemainingFen / 100.0
    val weeklyFreePct: Double get() =
        if (weeklyFreeLimitFen > 0) freeRemainingFen.toDouble() / weeklyFreeLimitFen * 100 else 0.0
}

@Serializable
data class TokenPrices(
    val inputPrice: Int = 120,
    val outputPrice: Int = 250
)

@Serializable
data class PayResult(
    val purchaseId: Long = 0,
    val amountFen: Int = 0,
    val codeUrl: String = "",
    val outTradeNo: String = ""
)
