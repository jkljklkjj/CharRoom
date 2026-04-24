package com.chatlite.charroom

import android.app.Notification
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import core.MessageReceiveListener
import model.users
import kotlin.random.Random

/**
 * 安卓端通知管理器
 * 负责处理新消息通知、渠道创建、通知点击等功能
 */
class ChatNotificationManager(private val context: Context) : MessageReceiveListener {
    private val notificationManager = NotificationManagerCompat.from(context)

    // 通知渠道ID
    companion object {
        const val CHANNEL_ID_MESSAGES = "chat_messages_channel"
        const val CHANNEL_ID_SERVICE = "chat_service_channel"
        const val NOTIFICATION_ID_SERVICE = 1001
        private const val NOTIFICATION_ID_BASE = 2000
    }

    init {
        createNotificationChannels()
    }

    /**
     * 创建通知渠道（Android O及以上需要）
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 消息通知渠道
            val messageChannel = NotificationChannel(
                CHANNEL_ID_MESSAGES,
                "新消息通知",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "收到新消息时的通知"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                vibrationPattern = longArrayOf(100, 200, 100, 200)
            }

            // 前台服务通知渠道（已经在ChatForegroundService中创建，这里保持一致）
            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                "聊天服务",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持后台聊天连接"
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
            }

            notificationManager.createNotificationChannels(listOf(messageChannel, serviceChannel))
        }
    }

    /**
     * 收到私聊消息回调
     */
    override fun onPrivateMessageReceived(senderId: Int, message: String, timestamp: Long) {
        // 如果应用在前台，不显示通知
        if (AppLifecycleObserver.isAppInForeground) {
            return
        }

        // 获取发送者信息
        val sender = users.find { it.id == senderId }
        val senderName = sender?.username ?: "陌生人"

        // 创建通知点击意图
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NOTIFICATION_TYPE", "private")
            putExtra("USER_ID", senderId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            senderId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 构建通知
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(senderName)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE)
            .build()

        // 显示通知，使用senderId作为通知ID，这样同一个用户的消息会覆盖
        try {
            if (notificationManager.areNotificationsEnabled()) {
                notificationManager.notify(NOTIFICATION_ID_BASE + senderId, notification)
            }
        } catch (_: SecurityException) {
            // 没有通知权限，忽略
        }
    }

    /**
     * 收到群聊消息回调
     */
    override fun onGroupMessageReceived(groupId: Int, senderId: Int, senderName: String, message: String, timestamp: Long) {
        // 如果应用在前台，不显示通知
        if (AppLifecycleObserver.isAppInForeground) {
            return
        }

        // 获取群组信息
        val group = users.find { it.id == -groupId }
        val groupName = group?.username ?: "群组"

        // 创建通知点击意图
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NOTIFICATION_TYPE", "group")
            putExtra("GROUP_ID", groupId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            -groupId, // 使用负的groupId作为requestCode，避免和私聊冲突
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 构建通知
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$groupName · $senderName")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE)
            .build()

        // 显示通知，使用groupId作为通知ID，同一个群的消息会覆盖
        try {
            if (notificationManager.areNotificationsEnabled()) {
                notificationManager.notify(NOTIFICATION_ID_BASE + (-groupId), notification)
            }
        } catch (_: SecurityException) {
            // 没有通知权限，忽略
        }
    }

    /**
     * 取消所有消息通知
     */
    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }

    /**
     * 取消特定用户的私聊通知
     */
    fun cancelPrivateNotification(userId: Int) {
        notificationManager.cancel(NOTIFICATION_ID_BASE + userId)
    }

    /**
     * 取消特定群组的通知
     */
    fun cancelGroupNotification(groupId: Int) {
        notificationManager.cancel(NOTIFICATION_ID_BASE + (-groupId))
    }
}
