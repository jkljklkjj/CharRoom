import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import core.MessageReceiveListener
import core.addMessageReceiveListener
import core.initKermit
import model.users
import java.awt.Desktop
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.io.File
import java.util.prefs.Preferences

/**
 * 桌面端应用入口
 * 实现系统托盘、全局快捷键、消息通知、窗口状态记忆等功能
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() = application {
    initKermit()

    // 窗口状态记忆
    val prefs = Preferences.userNodeForPackage(javaClass)
    val windowState = rememberWindowState(
        width = prefs.get("window_width", 800)?.toInt()?.dp ?: 800.dp,
        height = prefs.get("window_height", 600)?.toInt()?.dp ?: 600.dp,
        placement = WindowPlacement.values()[prefs.getInt("window_placement", WindowPlacement.Normal.ordinal)]
    )

    // 窗口可见状态
    var isWindowVisible by remember { mutableStateOf(true) }

    // 系统托盘初始化
    val trayState = rememberTrayState()
    val notificationManager = remember { DesktopNotificationManager() }

    LaunchedEffect(Unit) {
        // 注册消息接收监听器
        addMessageReceiveListener(notificationManager)
    }

    // 系统托盘
    Tray(
        state = trayState,
        icon = painterResource("icons/ic_notification.png"),
        menu = {
            Item(
                text = "打开主窗口",
                onClick = { isWindowVisible = true },
                shortcut = KeyShortcut(Key.O, ctrl = true)
            )
            Item(
                text = "新消息通知",
                onClick = { /* 打开通知设置 */ }
            )
            Separator()
            Item(
                text = "退出",
                onClick = ::exitApplication,
                shortcut = KeyShortcut(Key.Q, ctrl = true)
            )
        },
        onAction = {
            // 点击托盘图标显示/隐藏窗口
            isWindowVisible = !isWindowVisible
        }
    )

    if (isWindowVisible) {
        Window(
            onCloseRequest = {
                // 保存窗口状态
                prefs.putInt("window_width", windowState.size.width.value.toInt())
                prefs.putInt("window_height", windowState.size.height.value.toInt())
                prefs.putInt("window_placement", windowState.placement.ordinal)
                prefs.flush()

                // 最小化到托盘而不是退出
                isWindowVisible = false
            },
            title = "轻聊",
            state = windowState,
            icon = painterResource("icons/ic_launcher.png")
        ) {
            // 注册全局快捷键
            val menuBar = MenuBar {
                Menu("文件", mnemonic = 'F') {
                    Item("设置", onClick = { /* 打开设置 */ }, shortcut = KeyShortcut(Key.S, ctrl = true))
                    Separator()
                    Item("退出", onClick = ::exitApplication, shortcut = KeyShortcut(Key.Q, ctrl = true))
                }
                Menu("编辑", mnemonic = 'E') {
                    Item("复制", onClick = { /* 复制 */ }, shortcut = KeyShortcut(Key.C, ctrl = true))
                    Item("粘贴", onClick = { /* 粘贴 */ }, shortcut = KeyShortcut(Key.V, ctrl = true))
                }
                Menu("帮助", mnemonic = 'H') {
                    Item("关于", onClick = { /* 打开关于 */ })
                }
            }

            App(menuBar = menuBar)
        }
    }
}

/**
 * 桌面端通知管理器
 */
class DesktopNotificationManager : MessageReceiveListener {
    private val systemTray = if (SystemTray.isSupported()) SystemTray.getSystemTray() else null
    private val trayIcon: TrayIcon?

    init {
        // 初始化系统托盘图标
        trayIcon = try {
            val image = Toolkit.getDefaultToolkit().getImage(javaClass.getResource("/icons/ic_notification.png"))
            TrayIcon(image, "轻聊").apply {
                isImageAutoSize = true
            }
        } catch (_: Exception) {
            null
        }

        try {
            trayIcon?.let { systemTray?.add(it) }
        } catch (_: Exception) {
            // 系统托盘不支持
        }
    }

    override fun onPrivateMessageReceived(senderId: Int, message: String, timestamp: Long) {
        val sender = users.find { it.id == senderId }
        val senderName = sender?.username ?: "陌生人"
        showNotification("新消息", "$senderName: $message")
    }

    override fun onGroupMessageReceived(groupId: Int, senderId: Int, senderName: String, message: String, timestamp: Long) {
        val group = users.find { it.id == -groupId }
        val groupName = group?.username ?: "群组"
        showNotification("群消息 - $groupName", "$senderName: $message")
    }

    /**
     * 显示桌面通知
     */
    private fun showNotification(title: String, content: String) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.NOTIFY)) {
                // 使用Java AWT的通知API
                trayIcon?.displayMessage(title, content, TrayIcon.MessageType.INFO)
            }
        } catch (_: Exception) {
            // 通知不支持，忽略
        }
    }
}
