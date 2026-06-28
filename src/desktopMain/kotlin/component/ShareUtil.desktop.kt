package component

import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder

/**
 * Desktop: 用 java.awt.Desktop 打开默认邮件客户端，将消息内容填入邮件正文
 */
actual fun shareText(text: String) {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
            val body = URLEncoder.encode(text, "UTF-8")
            Desktop.getDesktop().mail(URI.create("mailto:?body=$body"))
        }
    } catch (_: Exception) {
        println("[ShareUtil] Failed to share via mail: $text")
    }
}
