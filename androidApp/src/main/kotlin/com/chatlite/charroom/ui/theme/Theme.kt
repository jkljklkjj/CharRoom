package com.chatlite.charroom.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import component.CoolOrangeDarkColors
import component.CoolOrangeLightColors
import component.CoolOrangeShapes

@Composable
fun ChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) CoolOrangeDarkColors else CoolOrangeLightColors

    MaterialTheme(
        colors = colors,
        typography = Typography,
        shapes = CoolOrangeShapes,
        content = content
    )
}