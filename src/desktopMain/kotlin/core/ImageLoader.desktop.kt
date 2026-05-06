package core

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.net.URL
import javax.imageio.ImageIO

/**
 * 桌面端图片加载器实现
 */
object DesktopImageLoader : ImageLoaderProvider {
    // JVM / Desktop implementation: download bytes, optionally cache to local disk by cacheKey, and decode via Skia
    override suspend fun loadImageBitmapFromUrl(url: String, cacheKey: String?): ImageBitmap? = withContext(Dispatchers.IO) {
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

        // 先读缓存
        if (cacheFile != null && cacheFile.exists()) {
            try {
                val bufferedImage = ImageIO.read(cacheFile)
                if (bufferedImage != null) {
                    return@withContext bufferedImage.toComposeImageBitmap()
                }
            } catch (_: Exception) {
                // 缓存损坏，重新下载
            }
        }

        // 下载图片
        val conn = URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 10000
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "QingLiao-Chat/1.0")

        // 如果是本站资源，带上Authorization头
        if (url.contains(ServerConfig.SERVER_IP)) {
            conn.setRequestProperty("Authorization", "Bearer ${ServerConfig.Token}")
        }

        conn.inputStream.use { ins ->
            val bytes = ins.readBytes()
            // 写入缓存
            if (cacheFile != null) {
                try { cacheFile.writeBytes(bytes) } catch (_: Exception) {}
            }
            // 转换为ImageBitmap
            val bufferedImage = ImageIO.read(ByteArrayInputStream(bytes))
            return@withContext bufferedImage?.toComposeImageBitmap()
        }
    } catch (e: Exception) {
        println("Image load error: $url, ${e.message}")
        null
    }
}
}
