package component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * 毛玻璃效果 Modifier 扩展
 * 跨平台实现：Android 使用 RenderEffect，Desktop 使用自定义模糊
 */
expect fun Modifier.glassmorphism(
    blurRadius: Float = 20f,
    backgroundColor: Color = Color.White.copy(alpha = 0.7f)
): Modifier

/**
 * 柔和阴影效果 Modifier 扩展
 */
expect fun Modifier.softShadow(
    elevation: Float = 8f,
    color: Color = Color.Black.copy(alpha = 0.2f)
): Modifier

/**
 * 动态模糊背景效果
 */
@Composable
expect fun DynamicBlurBackground(
    content: @Composable () -> Unit
)
