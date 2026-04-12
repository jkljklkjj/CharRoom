package component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Colors
import androidx.compose.material.Shapes
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val CoolOrangeLightColors: Colors = lightColors(
    primary = Color(0xFFD78345),
    primaryVariant = Color(0xFFBA6D35),
    secondary = Color(0xFFE8A474),
    secondaryVariant = Color(0xFFCC8754),
    background = Color(0xFFF8F5F2),
    surface = Color(0xFFFFFDFC),
    onPrimary = Color(0xFFFFF7EF),
    onSecondary = Color(0xFF2C221A),
    onBackground = Color(0xFF2F2A25),
    onSurface = Color(0xFF312B27),
    error = Color(0xFFD74A3D),
    onError = Color(0xFFFFFFFF)
)

val CoolOrangeDarkColors: Colors = darkColors(
    primary = Color(0xFFE3A16D),
    primaryVariant = Color(0xFFC98655),
    secondary = Color(0xFFF0B285),
    secondaryVariant = Color(0xFFD69666),
    background = Color(0xFF191512),
    surface = Color(0xFF241D19),
    onPrimary = Color(0xFF2B190F),
    onSecondary = Color(0xFF321F13),
    onBackground = Color(0xFFF2E4D7),
    onSurface = Color(0xFFECDAC8),
    error = Color(0xFFFF8A7A),
    onError = Color(0xFF250D0A)
)

val CoolOrangeShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

fun immersiveBackgroundBrush(isDarkMode: Boolean): Brush {
    return if (isDarkMode) {
        Brush.linearGradient(
            listOf(
                Color(0xFF18130F),
                Color(0xFF261C15),
                Color(0xFF38271B)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color(0xFFFBF7F2),
                Color(0xFFF8EEDF),
                Color(0xFFF5E4D0)
            )
        )
    }
}

fun sidebarHeaderBrush(isDarkMode: Boolean): Brush {
    return if (isDarkMode) {
        Brush.linearGradient(
            listOf(
                Color(0xFF875126),
                Color(0xFFA26433),
                Color(0xFFC07D4A)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color(0xFFCA763A),
                Color(0xFFD9894C),
                Color(0xFFE9A770)
            )
        )
    }
}

fun chatHeaderBrush(isDarkMode: Boolean): Brush {
    return if (isDarkMode) {
        Brush.linearGradient(
            listOf(
                Color(0xFF613A1E),
                Color(0xFF7A4B26),
                Color(0xFF986239)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color(0xFFFFF4E9),
                Color(0xFFFFEEDC),
                Color(0xFFFCE0C5)
            )
        )
    }
}

fun messageBubbleBrush(isMine: Boolean, isDarkMode: Boolean): Brush {
    return if (isMine) {
        if (isDarkMode) {
            Brush.linearGradient(listOf(Color(0xFF93572A), Color(0xFFB26B37), Color(0xFFC98655)))
        } else {
            Brush.linearGradient(listOf(Color(0xFFCA763A), Color(0xFFD9894D), Color(0xFFE7A873)))
        }
    } else {
        if (isDarkMode) {
            Brush.linearGradient(listOf(Color(0xFF2B221D), Color(0xFF352922)))
        } else {
            Brush.linearGradient(listOf(Color(0xFFFFFDFC), Color(0xFFFFF4EA)))
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
