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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val CoolOrangeLightColors: Colors = qingliaoPalette(false).let { p ->
    lightColors(
        primary = p.action, primaryVariant = p.onApricot,
        secondary = p.onApricot, secondaryVariant = p.action,
        background = p.paper, surface = p.surface,
        onPrimary = p.onAction, onSecondary = p.onAction,
        onBackground = p.ink, onSurface = p.ink,
        error = Color(0xFFB63B36), onError = Color.White
    )
}

val CoolOrangeDarkColors: Colors = qingliaoPalette(true).let { p ->
    darkColors(
        primary = p.action, primaryVariant = p.onApricot,
        secondary = p.onApricot, secondaryVariant = p.action,
        background = p.paper, surface = p.surface,
        onPrimary = p.onAction, onSecondary = p.onAction,
        onBackground = p.ink, onSurface = p.ink,
        error = Color(0xFFFFA49B), onError = Color(0xFF44201C)
    )
}

val CoolOrangeShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
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
    val p = qingliaoPalette(isDarkMode)
    return Brush.verticalGradient(listOf(p.surface, p.paper))
}

fun sidebarHeaderBrush(isDarkMode: Boolean): Brush {
    val p = qingliaoPalette(isDarkMode)
    return Brush.linearGradient(listOf(p.accent, p.action))
}

fun chatHeaderBrush(isDarkMode: Boolean): Brush {
    val p = qingliaoPalette(isDarkMode)
    return Brush.verticalGradient(listOf(p.paper, p.paper))
}

fun messageBubbleBrush(isMine: Boolean, isDarkMode: Boolean): Brush {
    val p = qingliaoPalette(isDarkMode)
    val color = if (isMine) p.message else p.surface
    return Brush.verticalGradient(listOf(color, color))
}

@Composable
fun rememberElasticScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f
): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
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
            dampingRatio = Spring.DampingRatioNoBouncy,
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
fun refinedMessageBubbleBrush(isMine: Boolean, isDarkMode: Boolean): Brush =
    messageBubbleBrush(isMine, isDarkMode)

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
