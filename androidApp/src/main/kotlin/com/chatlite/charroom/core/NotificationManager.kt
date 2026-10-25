package com.chatlite.charroom.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.chatlite.charroom.MainActivity
import com.chatlite.charroom.R
import com.chatlite.i18n.currentStrings
import core.state.GlobalAppState

class ChatNotificationManager(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)
    companion object {
        const val CHANNEL_ID_MESSAGES = "chat_messages_channel"
        const val CHANNEL_ID_SERVICE = "chat_service_channel"
        const val NOTIFICATION_ID_SERVICE = 1
        private const val MESSAGE_ID = 0
    }
    init {
        manager.createNotificationChannels(listOf(
            NotificationChannel(CHANNEL_ID_MESSAGES, currentStrings["android.notification.channel"], android.app.NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true) },
            NotificationChannel(CHANNEL_ID_SERVICE, currentStrings["android.service.channel"], android.app.NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
        ))
    }

    fun showPrivateMessageNotification(senderId: Int, senderName: String?, message: String) =
        show("private", senderId, senderName ?: context.getString(R.string.app_name), message)

    fun showGroupMessageNotification(groupId: Int, groupName: String?, senderName: String?, message: String) =
        show("group", groupId, listOfNotNull(groupName, senderName).joinToString(" · ").ifBlank { currentStrings["android.notifications"] }, message)

    private fun show(type: String, target: Int, title: String, content: String) {
        if (AppLifecycleObserver.isAppInForeground || GlobalAppState.currentToken.isNullOrBlank()) return
        val destination = NotificationDestination.parse(type, target, GlobalAppState.currentUserId ?: 0) ?: return
        if (!manager.areNotificationsEnabled()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("chatlite://notification/${destination.accountId}/$type/$target")
            putExtra("NOTIFICATION_TYPE", type)
            putExtra(if (type == "group") "GROUP_ID" else "USER_ID", target)
            putExtra("ACCOUNT_ID", destination.accountId)
        }
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(context.getString(R.string.app_name))
            .setContentText(currentStrings["android.notification.newMessage"]).build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title)
            .setContentText(content).setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(pending).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(publicVersion)
            .setCategory(Notification.CATEGORY_MESSAGE).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup("messages:${destination.accountId}").build()
        try { manager.notify(destination.tag, MESSAGE_ID, notification) } catch (_: SecurityException) { }
    }

    /** Leave the foreground service notification intact, including during logout cleanup. */
    fun cancelAllNotifications() {
        manager.activeNotifications.filter { it.tag?.startsWith("messages:") == true }.forEach { manager.cancel(it.tag, it.id) }
    }
    fun cancelPrivateNotification(userId: Int) = cancel("private", userId)
    fun cancelGroupNotification(groupId: Int) = cancel("group", groupId)
    private fun cancel(type: String, target: Int) {
        val destination = NotificationDestination.parse(type, target, GlobalAppState.currentUserId ?: 0) ?: return
        manager.cancel(destination.tag, MESSAGE_ID)
    }
}
