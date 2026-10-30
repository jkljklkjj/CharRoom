package core

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import core.state.GlobalAppState
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 桌面端图片加载器实现 */
object DesktopImageLoader : ImageLoaderProvider {
    override suspend fun loadImageBitmapFromUrl(
        url: String,
        cacheKey: String?,
        targetSize: Int?,
    ): ImageBitmap? =
        withContext(Dispatchers.IO) {
            recoverOperation(
                onFailure = { error ->
                    AppLogger.w("Image load error: $url, ${error.message}")
                    null
                }
            ) {
                val cached = cacheFile(url, cacheKey)
                readCached(cached, targetSize) ?: download(url, cached, targetSize)
            }
        }

    private fun cacheFile(url: String, key: String?): File? {
        if (key.isNullOrBlank()) return null
        return recoverOperation(onFailure = { null }) {
            val hash =
                java.security.MessageDigest.getInstance("SHA-1")
                    .digest(key.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            val path = recoverOperation(onFailure = { null }) { URI(url).path }
            val extension = path?.substringAfterLast('.', "jpg") ?: "jpg"
            val directory = File(System.getProperty("user.home"), ".qingliao/cache/avatars")
            directory.mkdirs()
            File(directory, "$hash.$extension")
        }
    }

    private fun readCached(file: File?, targetSize: Int?): ImageBitmap? =
        recoverOperation(onFailure = { null }) {
            file
                ?.takeIf { it.exists() }
                ?.let { cached ->
                    ImageIO.createImageInputStream(cached)
                        ?.use { decode(it, targetSize) }
                        ?.toComposeImageBitmap()
                }
        }

    private fun download(url: String, file: File?, targetSize: Int?): ImageBitmap? {
        val connection = URI(url).toURL().openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 10000
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "QingLiao-Chat/1.0")
        if (url.contains(ServerConfig.SERVER_IP)) {
            connection.setRequestProperty(
                "Authorization",
                "Bearer ${GlobalAppState.currentToken.orEmpty()}",
            )
        }
        return try {
            val bytes = connection.inputStream.use { it.readBytes() }
            if (file != null)
                recoverOperation(onFailure = {}) {
                    AtomicHistoryFile.write(file) { it.write(bytes) }
                }
            ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
                ?.use {
                    decode(it, targetSize)
                }
                ?.toComposeImageBitmap()
        } finally {
            connection.disconnect()
        }
    }

    internal fun decode(
        input: javax.imageio.stream.ImageInputStream,
        targetSize: Int?,
    ): BufferedImage? {
        val readers = ImageIO.getImageReaders(input)
        if (!readers.hasNext()) return null
        val reader = readers.next()
        try {
            reader.input = input
            val options = reader.defaultReadParam
            if (targetSize != null) {
                val sample =
                    (maxOf(reader.getWidth(0), reader.getHeight(0)) / targetSize).coerceAtLeast(1)
                options.setSourceSubsampling(sample, sample, 0, 0)
            }
            val image = reader.read(0, options)
            return if (targetSize == null || maxOf(image.width, image.height) <= targetSize) image
            else resize(image, targetSize)
        } finally {
            reader.dispose()
        }
    }

    private fun resize(image: BufferedImage, targetSize: Int): BufferedImage {
        val scale = targetSize.toDouble() / maxOf(image.width, image.height)
        return BufferedImage(
                (image.width * scale).toInt().coerceAtLeast(1),
                (image.height * scale).toInt().coerceAtLeast(1),
                BufferedImage.TYPE_INT_ARGB,
            )
            .also { resized ->
                val graphics = resized.createGraphics()
                try {
                    graphics.setRenderingHint(
                        java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR,
                    )
                    graphics.drawImage(image, 0, 0, resized.width, resized.height, null)
                } finally {
                    graphics.dispose()
                    image.flush()
                }
            }
    }
}
