package com.chatlite.charroom.component

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Android 平台的毛玻璃效果实现
 * 使用 RenderEffect 实现真正的模糊效果
 */
fun Modifier.glassmorphism(
    blurRadius: Float,
    backgroundColor: Color
): Modifier = this
    .graphicsLayer {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            renderEffect = RenderEffect
                .createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }
        clip = true
    }
    .background(backgroundColor)

/**
 * Android 平台的柔和阴影效果实现
 */
fun Modifier.softShadow(
    elevation: Float,
    color: Color
): Modifier = this.drawBehind {
    val shadowColor = color.copy(alpha = 0.1f)
    val ambientColor = color.copy(alpha = 0.05f)
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
