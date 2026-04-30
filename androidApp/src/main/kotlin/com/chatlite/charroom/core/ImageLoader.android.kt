package com.chatlite.charroom.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import core.ImageLoaderProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Android端图片加载器实现
 */
object AndroidImageLoader : ImageLoaderProvider {
    /**
     * Android implementation: load image from URL and convert to ImageBitmap
     */
    override suspend fun loadImageBitmapFromUrl(url: String, cacheKey: String?): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.doInput = true
        connection.connect()
        val inputStream: InputStream = connection.inputStream
        val bitmap: Bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        connection.disconnect()
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
}