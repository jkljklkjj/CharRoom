package component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Desktop 平台的毛玻璃效果实现
 * 使用 Compose Multiplatform 的 graphicsLayer 模糊效果
 */
actual fun Modifier.glassmorphism(
    blurRadius: Float,
    backgroundColor: Color
): Modifier = this
    .graphicsLayer {
        // 使用 Compose 的 BlurEffect（Compose 1.5+ 支持）
        renderEffect = BlurEffect(blurRadius.toInt(), blurRadius.toInt())
        clip = true
    }
    .background(backgroundColor)

/**
 * Desktop 平台的柔和阴影效果实现
 * 使用 drawBehind 绘制多层阴影
 */
actual fun Modifier.softShadow(
    elevation: Float,
    color: Color
): Modifier = this.drawBehind {
    val shadowColor = color.copy(alpha = 0.15f)
    val ambientColor = color.copy(alpha = 0.08f)

    // 绘制多层阴影以获得更柔和的效果
    for (i in 1..3) {
        val offset = Offset(0f, elevation * i * 0.3f)
        val radius = elevation * i * 1.5f

        drawCircle(
            color = if (i == 1) shadowColor else ambientColor,
            radius = radius,
            center = center + offset
        )
    }
}

/**
 * Desktop 平台的动态模糊背景实现
 */
@Composable
actual fun DynamicBlurBackground(
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()
    }
}
