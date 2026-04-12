import androidx.compose.runtime.Composable
import androidx.compose.material.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import component.LoginRegisterApp

@Composable
fun App() {
    var isDarkMode by remember { mutableStateOf(false) }

    MaterialTheme(colors = if (isDarkMode) darkColors() else lightColors()) {
        LoginRegisterApp(
            isDarkMode = isDarkMode,
            onToggleDarkMode = { isDarkMode = !isDarkMode }
        )
    }
}