package core.update

import core.model.AppVersionInfo
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI

interface UpdateDownload {
    suspend fun download(info: AppVersionInfo, progress: (Int, Long) -> Unit): File
    fun cancel()
    fun close()
}

/** Resume immutable artifacts by hash; an ignored Range starts a fresh download. */
class HttpUpdateDownload(
    private val journal: UpdateJournal,
    private val connect: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
) : UpdateDownload {
    @Volatile private var connection: HttpURLConnection? = null
    override fun cancel() { connection?.disconnect() }
    override fun close() = cancel()

    override suspend fun download(info: AppVersionInfo, progress: (Int, Long) -> Unit): File {
        journal.directory.mkdirs()
        val destination = journal.artifact(info)
        if (reusableArtifact(destination, info)) return destination
        val partial = File(destination.path + ".part")
        val etagFile = File(partial.path + ".etag")
        val etag = etagFile.takeIf { it.exists() && it.length() <= 512 }?.readText()?.takeIf { it.matches(Regex("\"[^\"\\r\\n]{1,256}\"")) }
        if (partial.length() > info.fileSize) check(partial.delete())
        if (partial.length() == info.fileSize) return finish(partial, destination, info)
        val remaining = info.fileSize - partial.length()
        require(journal.directory.usableSpace >= remaining + 32L * 1024 * 1024) { "Not enough disk space for update" }
        val offset = partial.length()
        var url = URI(info.downloadUrl).toURL()
        var response: HttpURLConnection? = null
        try {
            for (redirect in 0..5) {
                currentCoroutineContext().ensureActive()
                require(url.protocol == "https" && url.userInfo == null) { "Unsafe installer redirect" }
                val request = connect(url)
                connection = request; response = request
                request.instanceFollowRedirects = false
                request.connectTimeout = 10000; request.readTimeout = 15000
                request.setRequestProperty("Accept-Encoding", "identity")
                if (offset > 0) request.setRequestProperty("Range", "bytes=$offset-")
                if (offset > 0 && etag != null) request.setRequestProperty("If-Range", etag)
                val status = request.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    require(redirect < 5) { "Too many installer redirects" }
                    val location = requireNotNull(request.getHeaderField("Location")) { "Missing redirect URL" }
                    url = url.toURI().resolve(location).toURL(); request.disconnect(); continue
                }
                require(status == 200 || status == 206) { "Installer download HTTP $status" }
                val append = status == 206
                val responseEtag = request.getHeaderField("ETag")?.takeIf { it.matches(Regex("\"[^\"\\r\\n]{1,256}\"")) }
                if (append && etag != null && responseEtag != null && responseEtag != etag) {
                    partial.delete(); etagFile.delete(); error("Installer changed during resume; retry a fresh download")
                }
                if (responseEtag != null) core.AtomicHistoryFile.write(etagFile, responseEtag)
                val start = if (append) offset else 0L
                if (append) {
                    val range = request.getHeaderField("Content-Range")
                    val expected = "bytes $offset-${info.fileSize - 1}/${info.fileSize}"
                    require(range == expected) { "Invalid installer byte range" }
                }
                val length = request.getHeaderField("Content-Length")?.toLongOrNull()
                require(length == null || length == info.fileSize - start) { "Installer length differs from release" }
                var downloaded = start
                var lastProgress = -1
                request.inputStream.use { input ->
                    FileOutputStream(partial, append).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(downloaded + count <= info.fileSize) { "Installer exceeds declared size" }
                            output.write(buffer, 0, count); downloaded += count
                            val percentage = (downloaded * 100 / info.fileSize).toInt()
                            if (percentage != lastProgress) { progress(percentage, info.fileSize); lastProgress = percentage }
                        }
                        output.fd.sync()
                    }
                }
                require(downloaded == info.fileSize) { "Installer download interrupted" }
                return finish(partial, destination, info)
            }
            error("Installer download did not complete")
        } finally { response?.disconnect(); connection = null }
    }
    private suspend fun finish(partial: File, destination: File, info: AppVersionInfo): File {
        try { verifyArtifact(partial, info) }
        catch (error: IllegalArgumentException) { partial.delete(); throw error }
        check(partial.renameTo(destination)) { "Cannot finalize installer" }
        File(partial.path + ".etag").delete()
        return destination
    }
}
