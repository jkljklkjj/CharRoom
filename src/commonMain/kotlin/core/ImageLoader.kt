package core

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Platform-implemented image loader that returns an ImageBitmap for a given URL, or null on failure.
 * Desktop JVM implementation lives in `desktopMain`.
 */
expect suspend fun loadImageBitmapFromUrl(url: String, cacheKey: String? = null): ImageBitmap?
