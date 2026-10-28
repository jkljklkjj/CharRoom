package core

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import core.state.GlobalAppState
import java.net.URI
import javax.imageio.ImageIO

/**
 * 桌面端图片加载器实现
 */
object DesktopImageLoader : ImageLoaderProvider {
    // JVM / Desktop implementation: download bytes, optionally cache to local disk by cacheKey, and decode via Skia
    override suspend fun loadImageBitmapFromUrl(url: String, cacheKey: String?, targetSize: Int?): ImageBitmap? = withContext(Dispatchers.IO) {
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
                val bufferedImage = ImageIO.createImageInputStream(cacheFile)?.use { decode(it, targetSize) }
                if (bufferedImage != null) {
                    return@withContext bufferedImage.toComposeImageBitmap()
                }
            } catch (_: Exception) {
                // 缓存损坏，重新下载
            }
        }

        // 下载图片
        val conn = URI(url).toURL().openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 10000
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "QingLiao-Chat/1.0")

        // 如果是本站资源，带上Authorization头
        if (url.contains(ServerConfig.SERVER_IP)) {
            conn.setRequestProperty("Authorization", "Bearer ${GlobalAppState.currentToken.orEmpty()}")
        }

        try { conn.inputStream.use { ins ->
            val bytes = ins.readBytes()
            // 写入缓存
            if (cacheFile != null) {
                try { AtomicHistoryFile.write(cacheFile) { it.write(bytes) } } catch (_: Exception) {}
            }
            // 转换为ImageBitmap
            val bufferedImage = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))?.use { decode(it, targetSize) }
            return@withContext bufferedImage?.toComposeImageBitmap()
        } } finally { conn.disconnect() }
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        AppLogger.w("Image load error: $url, ${e.message}")
        null
    }
}

    internal fun decode(input: javax.imageio.stream.ImageInputStream, targetSize: Int?): BufferedImage? {
        val readers = ImageIO.getImageReaders(input)
        if (!readers.hasNext()) return null
        val reader = readers.next()
        try {
            reader.input = input
            val options = reader.defaultReadParam
            if (targetSize != null) {
                val sample = (maxOf(reader.getWidth(0), reader.getHeight(0)) / targetSize).coerceAtLeast(1)
                options.setSourceSubsampling(sample, sample, 0, 0)
            }
            val image = reader.read(0, options)
            if (targetSize == null || maxOf(image.width, image.height) <= targetSize) return image
            val scale = targetSize.toDouble() / maxOf(image.width, image.height)
            return BufferedImage((image.width * scale).toInt().coerceAtLeast(1), (image.height * scale).toInt().coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB).also { resized ->
                val graphics = resized.createGraphics()
                try {
                    graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                    graphics.drawImage(image, 0, 0, resized.width, resized.height, null)
                } finally { graphics.dispose(); image.flush() }
            }
        } finally { reader.dispose() }
    }
}
