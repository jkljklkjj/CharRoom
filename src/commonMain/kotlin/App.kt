import androidx.compose.runtime.Composable
import androidx.compose.material.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import component.CoolOrangeDarkColors
import component.CoolOrangeLightColors
import component.CoolOrangeShapes
import component.LoginRegisterApp

@Composable
fun App(menuBar: @Composable (() -> Unit)? = null) {
    var isDarkMode by remember { mutableStateOf(false) }

    MaterialTheme(
        colors = if (isDarkMode) CoolOrangeDarkColors else CoolOrangeLightColors,
        shapes = CoolOrangeShapes
    ) {
        // 桌面端菜单栏
        menuBar?.invoke()

        LoginRegisterApp(
            isDarkMode = isDarkMode,
            onToggleDarkMode = { isDarkMode = !isDarkMode }
        )
    }
}