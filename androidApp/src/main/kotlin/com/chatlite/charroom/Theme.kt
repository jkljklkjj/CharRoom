package com.chatlite.charroom

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val ChatPrimary = Color(0xFFD78345)
private val ChatPrimaryVariant = Color(0xFFBA6D35)
private val ChatSecondary = Color(0xFFE8A474)
private val ChatBackground = Color(0xFFF8F5F2)
private val ChatSurface = Color(0xFFFFFDFC)

private val ChatLightColors = lightColors(
    primary = ChatPrimary,
    primaryVariant = ChatPrimaryVariant,
    secondary = ChatSecondary,
    background = ChatBackground,
    surface = ChatSurface,
    onPrimary = Color.White,
    onSecondary = Color(0xFF2C221A),
    onBackground = Color(0xFF2F2A25),
    onSurface = Color(0xFF312B27),
    error = Color(0xFFD74A3D),
    onError = Color.White
)

private val ChatShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun ChatTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = ChatLightColors.primary.toArgb()
            window.navigationBarColor = ChatLightColors.primary.toArgb()
            WindowCompat.getInsetsController(window, window.decorView)?.isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colors = ChatLightColors,
        shapes = ChatShapes,
        content = content
    )
}
