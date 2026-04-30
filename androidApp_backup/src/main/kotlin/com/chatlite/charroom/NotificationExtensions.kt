package com.chatlite.charroom

import android.app.NotificationManager

/**
 * 取消所有通知
 */
fun NotificationManager.cancelAllNotifications() {
    cancelAll()
}

/**
 * 取消私信通知
 */
fun NotificationManager.cancelPrivateNotification(userId: Int) {
    cancel("private_$userId", 0)
}

/**
 * 取消群聊通知
 */
fun NotificationManager.cancelGroupNotification(groupId: Int) {
    cancel("group_$groupId", 0)
}
