package com.chatlite.charroom.component

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chatlite.charroom.core.FileTooLargeException
import com.chatlite.charroom.core.imageSampleSize
import com.chatlite.charroom.core.readBounded
import com.chatlite.i18n.LocalStrings
import component.io.FilePickerProvider
import core.state.GlobalAppState
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer

private const val MAX_IMAGE_INPUT = 20 * 1024 * 1024
private const val MAX_FILE_BYTES = 5 * 1024 * 1024
private const val MAX_IMAGE_OUTPUT = 500 * 1024

object AndroidFilePicker : FilePickerProvider {
    private data class Selection(val account: Int?, val image: ((ByteArray, String) -> Unit)? = null, val file: ((ByteArray, String, Long) -> Unit)? = null)
    private var selection: Selection? = null
    private var launchImage: (() -> Unit)? = null
    private var launchFile: (() -> Unit)? = null
    private var registeredOwner: Any? = null
    private var reading = false
    private var launchFailure: (() -> Unit)? = null

    @Composable
    override fun Register() {
        val activity = LocalActivity.current ?: return
        val scope = rememberCoroutineScope()
        val owner = remember { Any() }
        val strings = LocalStrings.current
        var processing by remember { mutableStateOf(false) }
        var job by remember { mutableStateOf<Job?>(null) }
        fun result(uri: Uri?) {
            val request = selection
            selection = null
            if (uri == null || request == null) { reading = false; return }
            reading = true
            processing = true
            job = scope.launch {
                try {
                    val loaded = runInterruptible(Dispatchers.IO) { load(activity, uri, request.image != null) }
                    ensureActive()
                    if (request.account == GlobalAppState.currentUserId) {
                        request.image?.invoke(loaded.first, loaded.second)
                        request.file?.invoke(loaded.first, loaded.second, loaded.first.size.toLong())
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    Toast.makeText(activity, strings[if (failure is FileTooLargeException) "android.file.tooLarge" else "android.file.failed"], Toast.LENGTH_LONG).show()
                } finally { processing = false; if (registeredOwner === owner) reading = false }
            }
        }
        val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), ::result)
        val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent(), ::result)
        DisposableEffect(activity, owner) {
            registeredOwner = owner
            launchImage = { imageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            launchFile = { fileLauncher.launch("*/*") }
            launchFailure = { Toast.makeText(activity, strings["android.file.failed"], Toast.LENGTH_LONG).show() }
            onDispose {
                if (registeredOwner === owner) {
                    registeredOwner = null; selection = null; launchImage = null; launchFile = null; launchFailure = null; reading = false
                }
            }
        }
        if (processing) AlertDialog(
            onDismissRequest = { job?.cancel() },
            text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { CircularProgressIndicator(Modifier.size(28.dp)); Text(strings["android.file.processing"]) } },
            buttons = { TextButton(onClick = { job?.cancel() }, modifier = Modifier.padding(8.dp)) { Text(strings["social.cancel"]) } }
        )
    }

    override fun pickImage(onResult: (ByteArray, String) -> Unit) {
        val launcher = launchImage ?: return
        if (selection != null || reading) return
        selection = Selection(GlobalAppState.currentUserId, image = onResult)
        try { launcher() } catch (_: RuntimeException) { selection = null; launchFailure?.invoke() }
    }
    override fun pickFile(onResult: (ByteArray, String, Long) -> Unit) {
        val launcher = launchFile ?: return
        if (selection != null || reading) return
        selection = Selection(GlobalAppState.currentUserId, file = onResult)
        try { launcher() } catch (_: RuntimeException) { selection = null; launchFailure?.invoke() }
    }

    private fun load(activity: Activity, uri: Uri, image: Boolean): Pair<ByteArray, String> {
        val resolver = activity.contentResolver
        val limit = if (image) MAX_IMAGE_INPUT else MAX_FILE_BYTES
        var name = if (image) "image" else "file"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex).substringAfterLast('/').substringAfterLast('\\').take(160).ifBlank { name }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex) && cursor.getLong(sizeIndex) > limit) throw FileTooLargeException()
            }
        }
        val bytes = resolver.openInputStream(uri)?.use { it.readBounded(limit) } ?: throw IOException("Cannot open selected content")
        if (bytes.isEmpty()) throw IOException("Empty selected content")
        return if (image) normalizeImage(bytes, name) else bytes to name
    }

    private fun normalizeImage(bytes: ByteArray, name: String): Pair<ByteArray, String> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Unsupported image")
        val orientation = try { ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } catch (_: IOException) { ExifInterface.ORIENTATION_NORMAL }
        val sample = imageSampleSize(bounds.outWidth, bounds.outHeight)
        if (sample == 1 && bytes.size <= MAX_IMAGE_OUTPUT && orientation in listOf(ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED)) return bytes to name
        var bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSampleSize(sample)
            }
        } else {
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: throw IOException("Cannot decode selected image")
            orient(decoded, orientation)
        }
        val format = if (bitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val extension = if (bitmap.hasAlpha()) "png" else "jpg"
        try {
            var quality = 90
            repeat(14) {
                val output = ByteArrayOutputStream()
                if (!bitmap.compress(format, quality, output)) throw IOException("Cannot encode selected image")
                if (output.size() <= MAX_IMAGE_OUTPUT) return output.toByteArray() to "${name.substringBeforeLast('.')}.${extension}"
                if (format == Bitmap.CompressFormat.JPEG && quality > 50) quality -= 10
                else {
                    val smaller = Bitmap.createScaledBitmap(bitmap, (bitmap.width * .75f).toInt().coerceAtLeast(1), (bitmap.height * .75f).toInt().coerceAtLeast(1), true)
                    if (smaller !== bitmap) bitmap.recycle()
                    bitmap = smaller
                }
            }
            throw FileTooLargeException()
        } finally { bitmap.recycle() }
    }

    private fun orient(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { if (it !== bitmap) bitmap.recycle() }
    }
}
