package component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import java.awt.KeyboardFocusManager
import java.awt.KeyEventDispatcher
import java.awt.event.KeyEvent

/**
 * Desktop平台返回键实现（监听ESC键）
 */
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    val dispatcher = remember {
        KeyEventDispatcher { event ->
            if (enabled && event.id == KeyEvent.KEY_PRESSED && event.keyCode == KeyEvent.VK_ESCAPE) {
                onBack()
                true
            } else {
                false
            }
        }
    }

    DisposableEffect(enabled, onBack) {
        val keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        keyboardFocusManager.addKeyEventDispatcher(dispatcher)

        onDispose {
            keyboardFocusManager.removeKeyEventDispatcher(dispatcher)
        }
    }
}
