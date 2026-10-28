package com.chatlite.charroom.core

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import core.model.AppVersionInfo
import core.update.*
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** DownloadManager owns networking; the application only observes and verifies its own task. */
class AndroidUpdateDownload(context: Context, private val journal: UpdateJournal) : UpdateDownload {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    @Volatile private var task = -1L
    override fun close() {} // Stopping an observer must not discard a system-managed download.
    override fun cancel() {
        val id = task.takeIf { it >= 0 } ?: journal.read()?.downloadId ?: -1
        if (id >= 0) manager.remove(id)
        task = -1
    }
    override suspend fun download(info: AppVersionInfo, progress: (Int, Long) -> Unit): File {
        val final = journal.artifact(info)
        if (reusableArtifact(final, info)) return final
        var id = journal.read()?.downloadId ?: -1
        val existing = if (id >= 0) manager.query(DownloadManager.Query().setFilterById(id))?.use {
            it.moveToFirst() && it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)) == info.downloadUrl
        } == true else false
        if (!existing) {
            val directory = File(requireNotNull(app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)), "updates")
            directory.mkdirs()
            require(directory.usableSpace >= info.fileSize + 32L * 1024 * 1024) { "Not enough download space" }
            val destination = File(directory, artifactName(info))
            check(!destination.exists() || destination.delete())
            val request = DownloadManager.Request(Uri.parse(info.downloadUrl))
                .setDestinationUri(Uri.fromFile(destination))
                .setTitle("${core.AppConfig.APP_NAME} ${info.versionName}")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            id = manager.enqueue(request)
            try { journal.write(UpdatePlan(info, "DOWNLOADING", id)) }
            catch (failure: Exception) { manager.remove(id); throw failure }
        }
        task = id
        while (true) {
            currentCoroutineContext().ensureActive()
            val status = manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                require(cursor.moveToFirst()) { "Download task is missing; retry the update" }
                val state = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val count = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                if (state == DownloadManager.STATUS_FAILED) {
                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    manager.remove(id); task = -1
                    error("System download failed ($reason); retry to create a new task")
                }
                if ((total > 0 && total != info.fileSize) || count > info.fileSize) {
                    manager.remove(id); task = -1
                    error("Download size differs from published installer")
                }
                progress((count * 100 / info.fileSize).toInt().coerceIn(0, 100), info.fileSize)
                state
            } ?: error("Cannot query update download")
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                val uri = requireNotNull(manager.getUriForDownloadedFile(id)) { "Downloaded installer is unavailable" }
                journal.directory.mkdirs()
                require(journal.directory.usableSpace >= info.fileSize + 32L * 1024 * 1024) { "Not enough verification space" }
                val partial = File(final.path + ".part")
                try {
                    requireNotNull(app.contentResolver.openInputStream(uri)).use { input ->
                        FileOutputStream(partial).use { output ->
                            val buffer = ByteArray(64 * 1024); var count = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val n = input.read(buffer); if (n < 0) break
                                count += n; require(count <= info.fileSize) { "Installer exceeds published size" }
                                output.write(buffer, 0, n)
                            }
                            output.fd.sync()
                        }
                    }
                    verifyArtifact(partial, info)
                    check(partial.renameTo(final)) { "Cannot finalize Android installer" }
                    manager.remove(id); task = -1
                    return final
                } catch (error: IllegalArgumentException) { partial.delete(); manager.remove(id); task = -1; throw error }
            }
            delay(1000)
        }
    }
}
