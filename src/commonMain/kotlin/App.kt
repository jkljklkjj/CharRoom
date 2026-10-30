import androidx.compose.material.*
import androidx.compose.runtime.*
import com.chatlite.i18n.LocalStrings
import com.chatlite.i18n.stringsForLocale
import component.LoginRegisterApp
import component.QingliaoTheme
import core.AppSettings

@Composable
fun App(
    menuBar: @Composable (() -> Unit)? = null,
    onBackPressed: ((() -> Boolean) -> Unit)? = null,
) {
    val themeMode by AppSettings.themeMode.collectAsState()
    val language by AppSettings.language.collectAsState()

    // Resolve dark/light based on the selected theme mode
    val isDarkMode = remember(themeMode) { AppSettings.isDarkMode(themeMode) }

    // Resolve effective strings for the selected language
    val strings =
        remember(language) {
            stringsForLocale(AppSettings.getEffectiveLanguage())
        }

    CompositionLocalProvider(LocalStrings provides strings) {
        QingliaoTheme(darkTheme = isDarkMode) {
            // 桌面端菜单栏
            menuBar?.invoke()

            LoginRegisterApp(
                isDarkMode = isDarkMode,
                onBackPressed = onBackPressed,
            )
        }
    }
}
