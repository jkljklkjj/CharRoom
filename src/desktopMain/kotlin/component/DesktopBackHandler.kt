package component

import androidx.compose.runtime.Composable

/**
 * 桌面端返回键实现（桌面端暂不需要特殊处理）
 */
object DesktopBackHandler : BackHandlerProvider {
    @Composable
    override fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
        // 桌面端返回键由系统处理，暂无特殊实现
    }
}
