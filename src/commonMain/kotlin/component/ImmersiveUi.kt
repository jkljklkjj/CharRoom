package component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.Surface
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val CoolOrangeLightColors: Colors = lightColors(
    primary = Color(0xFFFF7A33),
    primaryVariant = Color(0xFFE8601A),
    secondary = Color(0xFFFF9966),
    secondaryVariant = Color(0xFFFF8533),
    background = Color(0xFFFFF8F5),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFF222222),
    onBackground = Color(0xFF222222),
    onSurface = Color(0xFF222222),
    error = Color(0xFFFF4757),
    onError = Color(0xFFFFFFFF)
)

val CoolOrangeDarkColors: Colors = darkColors(
    primary = Color(0xFFFF9966),
    primaryVariant = Color(0xFFE8601A),
    secondary = Color(0xFFFFB380),
    secondaryVariant = Color(0xFFFF9966),
    background = Color(0xFF1A1A1A),
    surface = Color(0xFF2D2D2D),
    onPrimary = Color(0xFF222222),
    onSecondary = Color(0xFFFFFFFF),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFCCCCCC),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF1A1A1A)
)

val CoolOrangeShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp)
)

/**
 * 消息气泡形状 - 带「尾巴」切角
 * 自己消息: 底部右侧小切角（尾巴在右下）
 * 对方消息: 底部左侧小切角（尾巴在左下）
 */
fun bubbleShape(isMine: Boolean) = if (isMine) {
    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp)
} else {
    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)
}

fun immersiveBackgroundBrush(isDarkMode: Boolean): Brush {
    return if (isDarkMode) {
        Brush.linearGradient(
            listOf(
                Color(0xFF1A1A1A),
                Color(0xFF222222),
                Color(0xFF2A2A2A)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color(0xFFFFF8F5),
                Color(0xFFFFF0E6),
                Color(0xFFFFE8D8)
            )
        )
    }
}

fun sidebarHeaderBrush(isDarkMode: Boolean): Brush {
    return if (isDarkMode) {
        Brush.linearGradient(
            listOf(
                Color(0xFFCC5A1A),
                Color(0xFFE8601A),
                Color(0xFFFF7A33)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color(0xFFFF7A33),
                Color(0xFFFF8C4A),
                Color(0xFFFF9966)
            )
        )
    }
}

fun chatHeaderBrush(isDarkMode: Boolean): Brush {
    return if (isDarkMode) {
        Brush.linearGradient(
            listOf(
                Color(0xFF2D2D2D),
                Color(0xFF333333)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color(0xFFFFF8F5),
                Color(0xFFFFF0E6)
            )
        )
    }
}

fun messageBubbleBrush(isMine: Boolean, isDarkMode: Boolean): Brush {
    return if (isMine) {
        if (isDarkMode) {
            Brush.linearGradient(listOf(Color(0xFFE8601A), Color(0xFFFF7A33)))
        } else {
            Brush.linearGradient(listOf(Color(0xFFFF9A66), Color(0xFFFF7A33)))
        }
    } else {
        if (isDarkMode) {
            Brush.linearGradient(listOf(Color(0xFF3D3D3D), Color(0xFF3D3D3D)))
        } else {
            Brush.linearGradient(listOf(Color(0xFFF5F5F5), Color(0xFFF5F5F5)))
        }
    }
}

@Composable
fun rememberElasticScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.92f
): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    return scale
}

/**
 * 消息气泡弹出动画
 */
@Composable
fun rememberBubbleAnimation(): Float {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )
    return scale
}

/**
 * 脉冲动画（用于在线状态指示器）
 */
@Composable
fun rememberPulseAnimation(): Float {
    val infiniteTransition = rememberInfiniteTransition()
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    return pulse
}

/**
 * 渐变边框效果
 */
fun gradientBorderBrush(isDarkMode: Boolean): Brush {
    return if (isDarkMode) {
        Brush.linearGradient(
            listOf(
                Color(0xFFFF9966).copy(alpha = 0.4f),
                Color(0xFFFF7A33).copy(alpha = 0.6f),
                Color(0xFFFF9966).copy(alpha = 0.4f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color(0xFFFF7A33).copy(alpha = 0.3f),
                Color(0xFFFF9966).copy(alpha = 0.5f),
                Color(0xFFFF7A33).copy(alpha = 0.3f)
            )
        )
    }
}

/**
 * 更精致的消息气泡渐变
 */
fun refinedMessageBubbleBrush(isMine: Boolean, isDarkMode: Boolean): Brush {
    return if (isMine) {
        if (isDarkMode) {
            Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF7A33),
                    Color(0xFFE8601A)
                ),
                center = androidx.compose.ui.geometry.Offset(0.3f, 0.3f),
                radius = 300f
            )
        } else {
            Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFB380),
                    Color(0xFFFF7A33)
                ),
                center = androidx.compose.ui.geometry.Offset(0.3f, 0.3f),
                radius = 300f
            )
        }
    } else {
        if (isDarkMode) {
            Brush.radialGradient(
                colors = listOf(
                    Color(0xFF454545),
                    Color(0xFF3D3D3D)
                ),
                center = androidx.compose.ui.geometry.Offset(0.7f, 0.3f),
                radius = 300f
            )
        } else {
            Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFAFAFA),
                    Color(0xFFF5F5F5)
                ),
                center = androidx.compose.ui.geometry.Offset(0.7f, 0.3f),
                radius = 300f
            )
        }
    }
}

/**
 * 现代对话框容器 - 带遮罩层 + 居中卡片
 */
@Composable
fun ModernDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismissRequest
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = modifier.widthIn(max = 400.dp).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {} // prevent dismiss when clicking inside
            ),
            color = MaterialTheme.colors.surface,
            shape = RoundedCornerShape(16.dp),
            elevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(24.dp), content = content)
        }
    }
}
