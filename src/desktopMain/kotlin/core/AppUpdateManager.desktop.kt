package core

import com.chatlite.i18n.currentStrings
import core.model.AppVersionInfo
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 桌面端应用更新管理器实现
 */
class AppUpdateManagerImpl : AppUpdateManager {
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    override val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val httpClient = HttpClient {
        followRedirects = true
    }

    private var downloadJob: kotlinx.coroutines.Job? = null

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
            val result = GlobalApiService.checkAppVersion(platform, channel)

            when {
                result == null -> {
                    _updateState.value = UpdateState.Failed(currentStrings["update.check.failed"])
                }
                result.hasUpdate && result.latestVersion != null -> {
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
            _updateState.value = UpdateState.Failed(currentStrings["update.check.failed.with.error"].format(e.message))
        }
    }

    override suspend fun downloadUpdate(versionInfo: AppVersionInfo) {
        if (_updateState.value is UpdateState.Downloading) {
            return
        }

        _updateState.value = UpdateState.Downloading(0, versionInfo.fileSize)

        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        downloadJob = GlobalScope.launch(Dispatchers.IO) {
            try {
                val downloadDir = System.getProperty("java.io.tmpdir")
                val fileName = "Qingliao_${versionInfo.versionName}_${System.currentTimeMillis()}.exe"
                val outputFile = File(downloadDir, fileName)

                val response = httpClient.prepareGet(versionInfo.downloadUrl).execute { response ->
                    val totalBytes = response.headers["Content-Length"]?.toLongOrNull() ?: versionInfo.fileSize
                    var downloadedBytes = 0L

                    val input = response.bodyAsChannel()
                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int

                        while (input.readAvailable(buffer).also { read = it } > 0) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read

                            val progress = if (totalBytes > 0) {
                                (downloadedBytes * 100 / totalBytes).toInt()
                            } else {
                                0
                            }

                            _updateState.value = UpdateState.Downloading(progress, totalBytes)
                        }
                    }
                }

                // 验证MD5
                if (versionInfo.md5 != null) {
                    val fileMd5 = calculateMD5(outputFile)
                    if (!fileMd5.equals(versionInfo.md5, ignoreCase = true)) {
                        _updateState.value = UpdateState.Failed(currentStrings["update.verification.failed"])
                        return@launch
                    }
                }

                _updateState.value = UpdateState.Downloaded(outputFile.absolutePath, versionInfo)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _updateState.value = UpdateState.Failed(currentStrings["update.download.failed"].format(e.message))
                }
            }
        }
    }

    override suspend fun installUpdate(filePath: String) {
        _updateState.value = UpdateState.Installing

        try {
            withContext(Dispatchers.IO) {
                val file = File(filePath)
                if (file.exists()) {
                    // Windows下启动安装包
                    val processBuilder = ProcessBuilder(file.absolutePath, "/silent", "/norestart")
                    processBuilder.start()

                    // 退出当前应用
                    System.exit(0)
                } else {
                    _updateState.value = UpdateState.Failed(currentStrings["update.package.not.found"])
                }
            }
        } catch (e: Exception) {
            _updateState.value = UpdateState.Failed(currentStrings["update.install.failed"].format(e.message))
        }
    }

    override fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _updateState.value = UpdateState.Idle
    }

    /**
     * 计算文件MD5
     */
    private fun calculateMD5(file: File): String {
        val md = java.security.MessageDigest.getInstance("MD5")
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
 * 初始化桌面端应用更新管理器
 */
fun initAppUpdateManager() {
    GlobalAppUpdateManager = AppUpdateManagerImpl()
}