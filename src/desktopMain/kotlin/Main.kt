import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import core.initKermit

fun main() = application {
    initKermit()
    Window(onCloseRequest = ::exitApplication,title="轻聊") {
        App()
    }
}