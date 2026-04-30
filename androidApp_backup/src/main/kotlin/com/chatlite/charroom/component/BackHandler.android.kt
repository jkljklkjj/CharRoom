package com.chatlite.charroom.component

import androidx.activity.compose.BackHandler as AndroidBackHandler
import androidx.compose.runtime.Composable

/**
 * Android平台返回键实现
 */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    AndroidBackHandler(enabled = enabled, onBack = onBack)
}