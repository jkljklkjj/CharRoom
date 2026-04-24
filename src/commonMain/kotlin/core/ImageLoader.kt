package core

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

/**
 * 内存缓存，最多缓存50张图片
 */
private val imageCache = Collections.synchronizedMap(LinkedHashMap<String, ImageBitmap>(50, 0.75f, true))
private val cacheMutex = Mutex()
private const val MAX_CACHE_SIZE = 50

/**
 * Platform-implemented image loader that returns an ImageBitmap for a given URL, or null on failure.
 * Desktop JVM implementation lives in `desktopMain`.
 */
expect suspend fun loadImageBitmapFromUrl(url: String, cacheKey: String? = null): ImageBitmap?

/**
 * 带缓存的图片加载
 */
suspend fun loadImageBitmapWithCache(url: String, cacheKey: String? = null): ImageBitmap? {
    val key = cacheKey ?: url

    // 先从缓存读取
    cacheMutex.withLock {
        val cached = imageCache[key]
        if (cached != null) {
            return cached
        }
    }

    // 缓存未命中，加载图片
    val bitmap = loadImageBitmapFromUrl(url, cacheKey)
    if (bitmap != null) {
        cacheMutex.withLock {
            // 添加到缓存
            imageCache[key] = bitmap
            // 缓存超过大小，移除最旧的
            if (imageCache.size > MAX_CACHE_SIZE) {
                val firstKey = imageCache.entries.first().key
                imageCache.remove(firstKey)
            }
        }
    }

    return bitmap
}

/**
 * 清空图片缓存
 */
fun clearImageCache() {
    cacheMutex.tryLock()
    try {
        imageCache.clear()
    } finally {
        cacheMutex.unlock()
    }
}
