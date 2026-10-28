package com.chatlite.charroom.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import core.ImageLoaderProvider

/**
 * Android端图片加载器实现，支持内存缓存和磁盘缓存
 */
object AndroidImageLoader : ImageLoaderProvider {
    private lateinit var context: Context

    // 磁盘缓存目录
    private val diskCacheDir by lazy {
        File(context.cacheDir, "image_cache").apply {
            if (!exists()) mkdirs()
        }
    }

    // 磁盘缓存最大大小：100MB
    private const val MAX_DISK_CACHE_SIZE = 100 * 1024 * 1024L

    // 读写锁，保证线程安全
    private val diskCacheLock = ReentrantReadWriteLock()

    /**
     * 初始化，需要在Application中调用
     */
    fun init(context: Context) {
        this.context = context.applicationContext
        // 初始化时清理过期的磁盘缓存
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO).launch { diskCacheLock.write { cleanupDiskCache() } }
    }

    /**
     * Android implementation: load image from URL and convert to ImageBitmap
     * 支持两级缓存：内存缓存 -> 磁盘缓存 -> 网络下载
     */
    override suspend fun loadImageBitmapFromUrl(url: String, cacheKey: String?, targetSize: Int?): ImageBitmap? = withContext(Dispatchers.IO) {
        check(::context.isInitialized)
        // Shared cache owns decoded bitmaps and concurrent requests; disk keys include decode size.
        val key = "${cacheKey ?: url}#${targetSize ?: "original"}".md5()
        loadFromDiskCache(key, targetSize)?.let { return@withContext it }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5000
            connection.readTimeout = 10000
            val bytes = connection.inputStream.use { it.readBytes() }
            val options = BitmapFactory.Options()
            if (targetSize != null) {
                options.inJustDecodeBounds = true
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                var sample = 1
                while (maxOf(options.outWidth, options.outHeight) / (sample * 2) >= targetSize) sample *= 2
                options.inJustDecodeBounds = false
                options.inSampleSize = sample
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return@withContext null
            val bitmap = resize(decoded, targetSize)
            saveToDiskCache(key, bitmap)
            bitmap.asImageBitmap()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            timber.log.Timber.e(failure, "图片加载失败")
            null
        } finally { connection.disconnect() }
    }

    private fun resize(bitmap: Bitmap, targetSize: Int?): Bitmap {
        if (targetSize == null || maxOf(bitmap.width, bitmap.height) <= targetSize) return bitmap
        val scale = targetSize.toDouble() / maxOf(bitmap.width, bitmap.height)
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
            .also { if (it !== bitmap) bitmap.recycle() }
    }

    /**
     * 从磁盘缓存加载图片
     */
    private fun loadFromDiskCache(key: String, targetSize: Int?): ImageBitmap? {
        return diskCacheLock.read {
            try {
                val file = File(diskCacheDir, key)
                if (file.exists() && file.length() > 0) {
                    BitmapFactory.decodeFile(file.absolutePath)?.let { resize(it, targetSize).asImageBitmap() }
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 保存图片到磁盘缓存
     */
    private fun saveToDiskCache(key: String, bitmap: Bitmap) {
        diskCacheLock.write {
            try {
                val file = File(diskCacheDir, key)
                // 确保父目录存在
                file.parentFile?.mkdirs()
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }
                // 检查磁盘缓存大小，超过限制则清理旧文件
                trimDiskCache()
            } catch (e: Exception) {
                timber.log.Timber.e(e, "图片加载失败")
            }
        }
    }

    /**
     * 裁剪磁盘缓存，保持在最大大小以内
     */
    private fun trimDiskCache() {
        var totalSize = 0L
        val files = diskCacheDir.listFiles() ?: return
        files.forEach { totalSize += it.length() }

        if (totalSize > MAX_DISK_CACHE_SIZE) {
            // 按修改时间排序，删除最旧的文件
            files.sortedBy { it.lastModified() }.forEach { file ->
                if (totalSize > MAX_DISK_CACHE_SIZE * 0.8) { // 清理到80%以下
                    totalSize -= file.length()
                    file.delete()
                } else {
                    return@forEach
                }
            }
        }
    }

    /**
     * 清理过期的磁盘缓存（超过7天的文件）
     */
    private fun cleanupDiskCache() {
        val now = System.currentTimeMillis()
        val sevenDays = 7 * 24 * 60 * 60 * 1000L
        diskCacheDir.listFiles()?.forEach { file ->
            if (now - file.lastModified() > sevenDays) {
                file.delete()
            }
        }
    }

    /**
     * 字符串MD5哈希，用于生成缓存文件名
     */
    private fun String.md5(): String {
        val bytes = MessageDigest.getInstance("MD5").digest(toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}