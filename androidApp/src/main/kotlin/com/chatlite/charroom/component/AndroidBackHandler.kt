package com.chatlite.charroom.component

import androidx.activity.compose.BackHandler as AndroidBackHandler
import androidx.compose.runtime.Composable
import component.BackHandlerProvider

/**
 * Android平台返回键实现
 */
object AndroidBackHandler : BackHandlerProvider {
    @Composable
    override fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
        AndroidBackHandler(enabled = enabled, onBack = onBack)
    }
}
