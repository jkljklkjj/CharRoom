package com.chatlite.charroom.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import component.QingliaoTheme

@Composable
fun ChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    QingliaoTheme(darkTheme = darkTheme, content = content)
}
