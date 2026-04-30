package component

import androidx.compose.runtime.Composable

/**
 * 返回键处理接口
 */
interface BackHandlerProvider {
    /**
     * 返回键处理
     * @param enabled 是否启用
     * @param onBack 按下返回键时的回调
     */
    @Composable
    fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)
}

// 全局返回键处理实现，由各平台初始化
lateinit var BackHandlerImpl: BackHandlerProvider

/**
 * 跨平台返回键处理
 * @param enabled 是否启用
 * @param onBack 按下返回键时的回调
 */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    BackHandlerImpl.BackHandler(enabled, onBack)
}