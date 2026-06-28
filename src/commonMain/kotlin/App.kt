import androidx.compose.material.*
import androidx.compose.runtime.*
import com.chatlite.i18n.LocalStrings
import com.chatlite.i18n.stringsForLocale
import component.CoolOrangeDarkColors
import component.CoolOrangeLightColors
import component.CoolOrangeShapes
import component.LoginRegisterApp
import core.AppSettings
import core.ThemeMode

@Composable
fun App(
    menuBar: @Composable (() -> Unit)? = null,
    onBackPressed: ((() -> Boolean) -> Unit)? = null
) {
    val themeMode by AppSettings.themeMode.collectAsState()
    val language by AppSettings.language.collectAsState()

    // Resolve dark/light based on the selected theme mode
    val isDarkMode = remember(themeMode) { AppSettings.isDarkMode(themeMode) }

    // Resolve effective strings for the selected language
    val strings = remember(language) {
        stringsForLocale(AppSettings.getEffectiveLanguage())
    }

    CompositionLocalProvider(LocalStrings provides strings) {
        MaterialTheme(
            colors = if (isDarkMode) CoolOrangeDarkColors else CoolOrangeLightColors,
            shapes = CoolOrangeShapes
        ) {
            // 桌面端菜单栏
            menuBar?.invoke()

            LoginRegisterApp(
                isDarkMode = isDarkMode,
                onToggleDarkMode = { /* Theme switching is now done via SettingsScreen */ },
                onBackPressed = onBackPressed
            )
        }
    }
}