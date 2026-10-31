package core.update

import core.model.AppVersionInfo
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Resume immutable artifacts by hash; an ignored Range starts a fresh download. */
class HttpUpdateDownload(
    private val journal: UpdateJournal,
    private val connect: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) : UpdateDownload {
    @Volatile private var connection: HttpURLConnection? = null

    override fun cancel() {
        connection?.disconnect()
    }

    override fun close() = cancel()

    override suspend fun download(info: AppVersionInfo, progress: (Int, Long) -> Unit): File {
        journal.directory.mkdirs()
        val destination = journal.artifact(info)
        if (reusableArtifact(destination, info)) return destination
        return resume(destination, info, progress)
    }

    private suspend fun resume(
        destination: File,
        info: AppVersionInfo,
        progress: (Int, Long) -> Unit,
    ): File {
        val partial = File(destination.path + ".part")
        val etagFile = File(partial.path + ".etag")
        val etag = readEtag(etagFile)
        if (partial.length() > info.fileSize) check(partial.delete())
        return if (partial.length() == info.fileSize) finish(partial, destination, info)
        else {
            val remaining = info.fileSize - partial.length()
            require(journal.directory.usableSpace >= remaining + 32L * 1024 * 1024) {
                "Not enough disk space for update"
            }
            val offset = partial.length()
            var response: HttpURLConnection? = null
            try {
                val request =
                    openRequest(info, offset, etag) {
                        response = it
                        connection = it
                    }
                receiveArtifact(request, partial, info, offset, etag, progress)
                finish(partial, destination, info)
            } finally {
                response?.disconnect()
                connection = null
            }
        }
    }

    private fun readEtag(file: File): String? =
        file.takeIf { it.exists() && it.length() <= 512 }?.readText()?.takeIf { validEtag(it) }

    private fun validEtag(value: String): Boolean = value.matches(Regex("\"[^\"\\r\\n]{1,256}\""))

    private suspend fun openRequest(
        info: AppVersionInfo,
        offset: Long,
        etag: String?,
        remember: (HttpURLConnection) -> Unit,
    ): HttpURLConnection {
        var url = URI(info.downloadUrl).toURL()
        for (redirect in 0..5) {
            currentCoroutineContext().ensureActive()
            require(url.protocol == "https" && url.userInfo == null) { "Unsafe installer redirect" }
            val request = createRequest(url, offset, etag)
            remember(request)
            if (request.responseCode in listOf(301, 302, 303, 307, 308)) {
                require(redirect < 5) { "Too many installer redirects" }
                val location =
                    requireNotNull(request.getHeaderField("Location")) { "Missing redirect URL" }
                url = url.toURI().resolve(location).toURL()
                request.disconnect()
            } else return request
        }
        error("Installer download did not complete")
    }

    private fun createRequest(url: URL, offset: Long, etag: String?): HttpURLConnection {
        val request = connect(url)
        request.instanceFollowRedirects = false
        request.connectTimeout = 10000
        request.readTimeout = 15000
        request.setRequestProperty("Accept-Encoding", "identity")
        if (offset > 0) request.setRequestProperty("Range", "bytes=$offset-")
        if (offset > 0 && etag != null) request.setRequestProperty("If-Range", etag)
        return request
    }

    private suspend fun receiveArtifact(
        request: HttpURLConnection,
        partial: File,
        info: AppVersionInfo,
        offset: Long,
        etag: String?,
        progress: (Int, Long) -> Unit,
    ) {
        val status = request.responseCode
        require(status == 200 || status == 206) { "Installer download HTTP $status" }
        val append = status == 206
        validateResumeEtag(request, partial, etag, append)
        val start = if (append) offset else 0L
        validateResponseSize(request, info, append, start)
        val downloaded =
            request.inputStream.use { input ->
                FileOutputStream(partial, append).use { output ->
                    val count = copyResponse(input, output, start, info, progress)
                    output.fd.sync()
                    count
                }
            }
        require(downloaded == info.fileSize) { "Installer download interrupted" }
    }

    private fun validateResumeEtag(
        request: HttpURLConnection,
        partial: File,
        etag: String?,
        append: Boolean,
    ) {
        val responseEtag = request.getHeaderField("ETag")?.takeIf { validEtag(it) }
        val changed = etag != null && responseEtag != null && responseEtag != etag
        val etagFile = File(partial.path + ".etag")
        if (append && changed) {
            partial.delete()
            etagFile.delete()
            error("Installer changed during resume; retry a fresh download")
        }
        if (responseEtag != null) core.AtomicHistoryFile.write(etagFile, responseEtag)
    }

    private fun validateResponseSize(
        request: HttpURLConnection,
        info: AppVersionInfo,
        append: Boolean,
        start: Long,
    ) {
        if (append) {
            val expected = "bytes $start-${info.fileSize - 1}/${info.fileSize}"
            require(request.getHeaderField("Content-Range") == expected) {
                "Invalid installer byte range"
            }
        }
        val length = request.getHeaderField("Content-Length")?.toLongOrNull()
        require(length == null || length == info.fileSize - start) {
            "Installer length differs from release"
        }
    }

    private suspend fun copyResponse(
        input: InputStream,
        output: FileOutputStream,
        start: Long,
        info: AppVersionInfo,
        progress: (Int, Long) -> Unit,
    ): Long {
        var downloaded = start
        var lastProgress = -1
        val buffer = ByteArray(64 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            require(downloaded + count <= info.fileSize) { "Installer exceeds declared size" }
            output.write(buffer, 0, count)
            downloaded += count
            val percentage = (downloaded * 100 / info.fileSize).toInt()
            if (percentage != lastProgress) {
                progress(percentage, info.fileSize)
                lastProgress = percentage
            }
        }
        return downloaded
    }

    private suspend fun finish(partial: File, destination: File, info: AppVersionInfo): File {
        try {
            verifyArtifact(partial, info)
        } catch (error: IllegalArgumentException) {
            partial.delete()
            throw error
        }
        check(partial.renameTo(destination)) { "Cannot finalize installer" }
        File(partial.path + ".etag").delete()
        return destination
    }
}
