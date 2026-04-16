package core

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.URL

// JVM / Desktop implementation: download bytes, optionally cache to local disk by cacheKey, and decode via Skia
actual suspend fun loadImageBitmapFromUrl(url: String, cacheKey: String?): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        var cacheFile: File? = null
        if (!cacheKey.isNullOrBlank()) {
            try {
                val digest = java.security.MessageDigest.getInstance("SHA-1")
                val hash = digest.digest(cacheKey.toByteArray()).joinToString("") { b -> "%02x".format(b) }
                val path = try { URI(url).path } catch (_: Exception) { null }
                val ext = path?.substringAfterLast('.', "jpg") ?: "jpg"
                val cacheDir = File(System.getProperty("user.home"), ".qingliao/cache/avatars")
                cacheDir.mkdirs()
                cacheFile = File(cacheDir, "$hash.$ext")
            } catch (_: Exception) {
                cacheFile = null
            }
        }

        if (cacheFile != null && cacheFile.exists()) {
            try {
                val bytes = cacheFile.readBytes()
                val skImage = org.jetbrains.skia.Image.makeFromEncoded(bytes)
                return@withContext skImage.toComposeImageBitmap()
            } catch (_: Exception) {
                // fallthrough to re-download
            }
        }

        val conn = URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 10000
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = true
        conn.inputStream.use { ins ->
            val bytes = ins.readBytes()
            if (cacheFile != null) {
                try { cacheFile.writeBytes(bytes) } catch (_: Exception) {}
            }
            val skImage = org.jetbrains.skia.Image.makeFromEncoded(bytes)
            return@withContext skImage.toComposeImageBitmap()
        }
    } catch (e: Exception) {
        null
    }
}
