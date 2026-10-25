package component.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalPlatformSettings = staticCompositionLocalOf<@Composable () -> Unit> { {} }
