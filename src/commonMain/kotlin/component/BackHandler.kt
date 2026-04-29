package component

import androidx.compose.runtime.Composable

/**
 * 跨平台返回键处理
 * @param enabled 是否启用
 * @param onBack 按下返回键时的回调
 */
@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)
