package component

/**
 * 分享文本 — 平台相关
 * Desktop: 用 java.awt.Desktop 打开默认邮件客户端发送
 * Android: 用 Intent.ACTION_SEND 调起系统分享
 */
expect fun shareText(text: String)
