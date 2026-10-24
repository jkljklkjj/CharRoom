package component

import java.awt.Desktop
import java.net.URI
import core.AppLogger

/**
 * Desktop: 打开系统默认浏览器访问支付链接
 */
fun openPaymentUrl(url: String) {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        }
    } catch (_: Exception) {
        AppLogger.w("[TokenQuota] Failed to open URL: $url")
    }
}
