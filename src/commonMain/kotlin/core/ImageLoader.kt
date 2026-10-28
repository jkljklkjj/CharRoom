package core

import androidx.compose.ui.graphics.ImageBitmap

interface ImageLoaderProvider {
    suspend fun loadImageBitmapFromUrl(url: String, cacheKey: String? = null, targetSize: Int? = null): ImageBitmap?
}

lateinit var ImageLoaderImpl: ImageLoaderProvider
private val imageCache = WeightedLoadCache<ImageBitmap>(24L * 1024 * 1024) { it.width.toLong() * it.height * 4 }

suspend fun loadImageBitmapFromUrl(url: String, cacheKey: String? = null, targetSize: Int? = null): ImageBitmap? =
    loadImageBitmapWithCache(url, cacheKey, targetSize)

suspend fun loadImageBitmapWithCache(url: String, cacheKey: String? = null, targetSize: Int? = null): ImageBitmap? {
    val size = imageSizeBucket(targetSize)
    return imageCache.load(imageMemoryKey(url, cacheKey, size)) { ImageLoaderImpl.loadImageBitmapFromUrl(url, cacheKey, size) }
}

fun getCachedImage(url: String, cacheKey: String? = null, targetSize: Int? = null): ImageBitmap? =
    imageCache.get(imageMemoryKey(url, cacheKey, targetSize))

fun clearImageCache() = imageCache.clear()
