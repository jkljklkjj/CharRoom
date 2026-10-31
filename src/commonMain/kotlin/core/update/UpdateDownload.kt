package core.update

import core.model.AppVersionInfo
import java.io.File

interface UpdateDownload {
    suspend fun download(info: AppVersionInfo, progress: (Int, Long) -> Unit): File

    fun cancel()

    fun close()
}
