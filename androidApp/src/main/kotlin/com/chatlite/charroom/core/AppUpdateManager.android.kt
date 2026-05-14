package com.chatlite.charroom.core

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import core.AppConfig
import core.UpdateState
import core.model.AppVersionInfo
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Android端应用更新管理器实现
 */
class AndroidAppUpdateManager(private val context: Context) : core.AppUpdateManager {
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    override val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val httpClient = HttpClient {
        followRedirects = true
    }

    private var downloadJob: kotlinx.coroutines.Job? = null
    private var downloadId: Long = -1

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    val downloadManager = context?.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val query = DownloadManager.Query().setFilterById(id)
                    val cursor = downloadManager.query(query)
                    if (cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            val uri = downloadManager.getUriForDownloadedFile(id)
                            uri?.let {
                                val filePath = getRealPathFromUri(context, it)
                                filePath?.let { path ->
                                    _updateState.value = UpdateState.Downloaded(path, latestVersionInfo!!)
                                }
                            }
                        } else {
                            _updateState.value = UpdateState.Failed("下载失败")
                        }
                    }
                    cursor.close()
                }
            }
        }
    }

    private var latestVersionInfo: AppVersionInfo? = null

    init {
        context.registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    override suspend fun checkForUpdates(
        platform: String,
        channel: String,
        autoDownload: Boolean
    ) {
        if (_updateState.value is UpdateState.Checking || _updateState.value is UpdateState.Downloading) {
            return
        }

        _updateState.value = UpdateState.Checking

        try {
            val result = core.GlobalApiService.checkAppVersion(platform, channel)

            when {
                result == null -> {
                    _updateState.value = UpdateState.Failed("检查更新失败")
                }
                result.hasUpdate && result.latestVersion != null -> {
                    latestVersionInfo = result.latestVersion
                    _updateState.value = UpdateState.Available(result.latestVersion)

                    if (autoDownload) {
                        downloadUpdate(result.latestVersion)
                    }
                }
                else -> {
                    _updateState.value = UpdateState.NoUpdate
                }
            }
        } catch (e: Exception) {
            _updateState.value = UpdateState.Failed("检查更新失败: ${e.message}")
        }
    }

    override suspend fun downloadUpdate(versionInfo: AppVersionInfo) {
        if (_updateState.value is UpdateState.Downloading) {
            return
        }

        latestVersionInfo = versionInfo

        // 使用系统DownloadManager下载
        val request = DownloadManager.Request(Uri.parse(versionInfo.downloadUrl))
        request.setTitle("${AppConfig.APP_NAME}更新")
        request.setDescription("正在下载新版本 ${versionInfo.versionName}")
        request.setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            "Qingliao_${versionInfo.versionName}.apk"
        )
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setMimeType("application/vnd.android.package-archive")

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = downloadManager.enqueue(request)

        _updateState.value = UpdateState.Downloading(0, versionInfo.fileSize)

        // 启动协程监听下载进度
        downloadJob = kotlinx.coroutines.coroutineScope {
            launch(Dispatchers.IO) {
                var finished = false
                while (!finished) {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    if (cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                            finished = true
                        } else if (status == DownloadManager.STATUS_RUNNING) {
                            val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            val progress = if (bytesTotal > 0) {
                                (bytesDownloaded * 100 / bytesTotal).toInt()
                            } else {
                                0
                            }
                            _updateState.value = UpdateState.Downloading(progress, bytesTotal)
                        }
                    }
                    cursor.close()
                    kotlinx.coroutines.delay(1000)
                }
            }
        }
    }

    override suspend fun installUpdate(filePath: String) {
        _updateState.value = UpdateState.Installing

        try {
            val apkFile = File(filePath)
            if (!apkFile.exists()) {
                _updateState.value = UpdateState.Failed("安装包不存在")
                return
            }

            val intent = Intent(Intent.ACTION_VIEW)
            val uri: Uri

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                uri = FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    apkFile
                )
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                uri = Uri.fromFile(apkFile)
            }

            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            _updateState.value = UpdateState.Idle
        } catch (e: Exception) {
            _updateState.value = UpdateState.Failed("安装失败: ${e.message}")
        }
    }

    override fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null

        if (downloadId != -1L) {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.remove(downloadId)
            downloadId = -1
        }

        _updateState.value = UpdateState.Idle
    }

    /**
     * 从Uri获取真实文件路径
     */
    private fun getRealPathFromUri(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val columnIndex = it.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA)
                    return it.getString(columnIndex)
                }
            }
        }
        return uri.path
    }

    /**
     * 计算文件MD5
     */
    private fun calculateMD5(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                md.update(buffer, 0, read)
            }
        }
        val digest = md.digest()
        return digest.joinToString("") { "%02x".format(it) }
    }
}

/**
 * 初始化Android全局应用更新管理器
 */
fun initAndroidAppUpdateManager(context: Context) {
    core.GlobalAppUpdateManager = AndroidAppUpdateManager(context)
}