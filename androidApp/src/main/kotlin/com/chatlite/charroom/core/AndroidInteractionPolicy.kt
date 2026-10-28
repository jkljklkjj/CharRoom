package com.chatlite.charroom.core

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

data class NotificationDestination private constructor(val type: String, val targetId: Int, val accountId: Int) {
    val conversation: model.ConversationKey get() = if (type == "group") model.ConversationKey.group(targetId) else model.ConversationKey.privateChat(targetId)
    val tag: String get() = "messages:$accountId:$type:$targetId"
    fun belongsTo(account: Int?) = account == accountId
    companion object {
        fun parse(type: String?, targetId: Int, accountId: Int): NotificationDestination? =
            if (type in listOf("private", "group") && targetId > 0 && accountId > 0) NotificationDestination(type!!, targetId, accountId) else null
    }
}

class FileTooLargeException : IOException("Selected content exceeds the supported size")

fun InputStream.readBounded(limit: Int): ByteArray {
    require(limit > 0)
    val output = ByteArrayOutputStream(minOf(limit, 16 * 1024))
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val count = read(buffer, 0, minOf(buffer.size, limit - total + 1))
        if (count == -1) break
        total += count
        if (total > limit) throw FileTooLargeException()
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

fun imageSampleSize(width: Int, height: Int): Int {
    require(width > 0 && height > 0)
    var sample = 1
    while (width / sample > 2048 || height / sample > 2048 || (width.toLong() / sample) * (height.toLong() / sample) > 4_194_304L) sample *= 2
    return sample
}

fun shouldRequestNotificationPermission(sdk: Int, granted: Boolean, requested: Boolean, rationale: Boolean): Boolean =
    sdk >= 33 && !granted && (!requested || rationale)
