package component

/**
 * 分享文本 — 平台相关。
 * Desktop: 用 java.awt.Desktop 打开默认邮件客户端发送
 * Android: 无默认实现（各平台在对应 sourceSet 中覆盖）
 */
fun shareText(text: String) {
    // No-op in commonMain; platform-specific implementation in desktopMain/androidMain
}
