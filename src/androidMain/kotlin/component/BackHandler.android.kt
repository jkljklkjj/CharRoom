package component

import androidx.activity.compose.BackHandler as AndroidBackHandler
import androidx.compose.runtime.Composable

/**
 * Android平台返回键实现
 */
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    AndroidBackHandler(enabled = enabled, onBack = onBack)
}
