package com.chatlite.charroom

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import core.MessageReceiveListener
import core.addMessageReceiveListener
import core.initKermit
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.w3c.dom.Notification
import org.w3c.dom.NotificationOptions
import org.w3c.dom.ServiceWorkerRegistration
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Web端应用入口
 * 实现浏览器通知、标题闪烁提醒、PWA离线支持等功能
 */
fun main() = application {
    initKermit()

    // 初始化Web端功能
    LaunchedEffect(Unit) {
        // 注册PWA Service Worker
        registerServiceWorker()

        // 请求通知权限
        requestNotificationPermission()

        // 初始化标题闪烁管理器
        val titleBlinkManager = TitleBlinkManager()

        // 注册消息接收监听器
        addMessageReceiveListener(object : MessageReceiveListener {
            override fun onPrivateMessageReceived(senderId: Int, message: String, timestamp: Long) {
                // 页面不可见时显示通知和标题闪烁
                if (document.hidden == true) {
                    showNotification("新消息", message)
                    titleBlinkManager.startBlink("【新消息】轻聊")
                }
            }

            override fun onGroupMessageReceived(groupId: Int, senderId: Int, senderName: String, message: String, timestamp: Long) {
                // 页面不可见时显示通知和标题闪烁
                if (document.hidden == true) {
                    showNotification("群消息 - $senderName", message)
                    titleBlinkManager.startBlink("【新消息】轻聊")
                }
            }
        })

        // 监听页面可见性变化，停止标题闪烁
        document.addEventListener("visibilitychange", {
            if (document.hidden == false) {
                titleBlinkManager.stopBlink()
            }
        })
    }

    Window(title = "轻聊") {
        App()
    }
}

/**
 * 请求浏览器通知权限
 */
suspend fun requestNotificationPermission(): Boolean {
    return suspendCoroutine { continuation ->
        if (Notification.permission == "granted") {
            continuation.resume(true)
        } else if (Notification.permission != "denied") {
            Notification.requestPermission { result ->
                continuation.resume(result == "granted")
            }
        } else {
            continuation.resume(false)
        }
    }
}

/**
 * 显示浏览器通知
 */
fun showNotification(title: String, body: String) {
    if (Notification.permission == "granted") {
        val options = NotificationOptions(
            body = body,
            icon = "/icons/ic_notification.png",
            tag = "chat_message"
        )
        val notification = Notification(title, options)

        // 点击通知打开页面
        notification.onclick = {
            window.focus()
            notification.close()
        }

        // 3秒后自动关闭通知
        window.setTimeout({ notification.close() }, 3000)
    }
}

/**
 * 标题闪烁管理器
 */
class TitleBlinkManager {
    private var originalTitle: String? = null
    private var blinkJob: kotlinx.coroutines.Job? = null
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)

    /**
     * 开始标题闪烁
     */
    fun startBlink(blinkTitle: String) {
        if (blinkJob?.isActive == true) return

        originalTitle = document.title

        blinkJob = scope.launch {
            while (isActive) {
                document.title = blinkTitle
                delay(500)
                document.title = originalTitle ?: "轻聊"
                delay(500)
            }
        }
    }

    /**
     * 停止标题闪烁
     */
    fun stopBlink() {
        blinkJob?.cancel()
        blinkJob = null
        originalTitle?.let { document.title = it }
    }
}

/**
 * 注册PWA Service Worker，实现离线支持
 */
suspend fun registerServiceWorker(): ServiceWorkerRegistration? {
    return try {
        if ("serviceWorker" in window.navigator) {
            suspendCoroutine { continuation ->
                window.navigator.serviceWorker.register("/sw.js")
                    .then { registration ->
                        println("ServiceWorker 注册成功: ${registration.scope}")
                        continuation.resume(registration)
                    }
                    .catch { error ->
                        println("ServiceWorker 注册失败: ${error.message}")
                        continuation.resume(null)
                    }
            }
        } else {
            null
        }
    } catch (e: Exception) {
        println("ServiceWorker 注册异常: ${e.message}")
        null
    }
}
