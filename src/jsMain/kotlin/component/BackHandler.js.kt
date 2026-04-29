package component

import androidx.compose.runtime.Composable

/**
 * Web平台返回键实现
 */
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Web端可以监听浏览器的popstate事件
    // 暂时留空，或者根据需求实现
}
