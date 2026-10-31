package com.chatlite.charroom.core

import android.content.Context
import android.os.StatFs
import android.os.storage.StorageManager
import java.io.File
import java.io.IOException

/** Includes reclaimable cache space; unsupported volumes retain a conservative free-space check. */
internal class AndroidUpdateStorage(context: Context) {
    private val storage =
        requireNotNull(context.applicationContext.getSystemService(StorageManager::class.java))

    fun prepare(directory: File, fileSize: Long) {
        require(fileSize > 0)
        val required = Math.addExact(fileSize, 32L * 1024 * 1024)
        ensureUpdateStorage(
            required,
            allocatable = { storage.getAllocatableBytes(storage.getUuidForPath(directory)) },
            allocate = { storage.allocateBytes(storage.getUuidForPath(directory), required) },
            available = { StatFs(directory.absolutePath).availableBytes },
        )
    }
}

internal fun ensureUpdateStorage(
    required: Long,
    allocatable: () -> Long,
    allocate: () -> Unit,
    available: () -> Long,
) {
    require(required > 0)
    try {
        require(allocatable() >= required) { "Not enough update space" }
        allocate()
    } catch (_: IOException) {
        require(available() >= required) { "Not enough update space" }
    }
}
