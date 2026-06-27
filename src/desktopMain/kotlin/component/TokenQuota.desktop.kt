package component

import java.awt.Desktop
import java.net.URI

/**
 * Desktop: 打开系统默认浏览器访问支付链接
 */
actual fun openPaymentUrl(url: String) {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        }
    } catch (_: Exception) {
        println("[TokenQuota] Failed to open URL: $url")
    }
}
