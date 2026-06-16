package component

import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Android 平台的毛玻璃效果实现
 * 使用 RenderEffect 实现真正的模糊效果
 */
fun Modifier.glassmorphism(
    blurRadius: Float,
    backgroundColor: Color
): Modifier = this
    .graphicsLayer {
        // 使用 Android 的 RenderEffect 实现模糊
        renderEffect = RenderEffect
            .createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
            .asComposeRenderEffect()
        // 确保模糊效果在硬件加速下工作
        clip = true
    }
    .background(backgroundColor)

/**
 * Android 平台的柔和阴影效果实现
 * 使用 drawBehind 绘制多层阴影
 */
fun Modifier.softShadow(
    elevation: Float,
    color: Color
): Modifier = this.drawBehind {
    val shadowColor = color.copy(alpha = 0.1f)
    val ambientColor = color.copy(alpha = 0.05f)

    // 绘制多层阴影以获得更柔和的效果
    for (i in 1..3) {
        val offset = Offset(0f, elevation * i * 0.5f)
        val radius = elevation * i * 2f

        drawCircle(
            color = if (i == 1) shadowColor else ambientColor,
            radius = radius,
            center = center + offset
        )
    }
}

/**
 * Android 平台的动态模糊背景实现
 */
@Composable
fun DynamicBlurBackground(
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()
    }
}
