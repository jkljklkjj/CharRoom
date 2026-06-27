package component

/**
 * Android: 支付链接处理
 * TODO: 集成微信支付 SDK 后，在这里调起 WXPayEntryActivity
 */
actual fun openPaymentUrl(url: String) {
    // Android 上暂时用 Mock 模式（后端自动完成支付）
    println("[TokenQuota] Android payment URL: $url")
    println("[TokenQuota] 使用 Mock 支付模式，后端自动确认")
}
