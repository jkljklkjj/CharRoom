package component

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Android平台文件选择器实现
 */
private const val MAX_AVATAR_BYTES = 5 * 1024 * 1024

object AndroidFilePicker : FilePickerProvider {
    private var pickImageCallback: ((ByteArray, String) -> Unit)? = null
    private var pickFileCallback: ((ByteArray, String, Long) -> Unit)? = null

    @OptIn(DelicateCoroutinesApi::class)
    @Composable
    override fun Register() {
        val activity = LocalActivity.current ?: return

        val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
            onResult = { uri: Uri? ->
                uri?.let {
                    GlobalScope.launch(Dispatchers.IO) {
                        val inputStream = activity.contentResolver.openInputStream(it)
                        val fileName = getFileName(activity, it)
                        val bytes = inputStream?.readBytes()
                        inputStream?.close()
                        bytes?.let { data ->
                            val compressed = compressImageIfNeeded(data, fileName)
                            pickImageCallback?.invoke(compressed, compressedFileName(fileName, data, compressed))
                            pickImageCallback = null
                        }
                    }
                }
            }
        )

        val filePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
            onResult = { uri: Uri? ->
                uri?.let {
                    GlobalScope.launch(Dispatchers.IO) {
                        val inputStream = activity.contentResolver.openInputStream(it)
                        val fileName = getFileName(activity, it)
                        val fileSize = getFileSize(activity, it)
                        val bytes = inputStream?.readBytes()
                        inputStream?.close()
                        bytes?.let { data ->
                            pickFileCallback?.invoke(data, fileName, fileSize)
                            pickFileCallback = null
                        }
                    }
                }
            }
        )

        // 保存launcher引用
        currentImageLauncher = { imagePickerLauncher.launch(it) }
        currentFileLauncher = { filePickerLauncher.launch(it) }
    }

    private var currentImageLauncher: ((String) -> Unit)? = null
    private var currentFileLauncher: ((String) -> Unit)? = null

    override fun pickImage(onResult: (ByteArray, String) -> Unit) {
        pickImageCallback = onResult
        currentImageLauncher?.invoke("image/*")
    }

    override fun pickFile(onResult: (ByteArray, String, Long) -> Unit) {
        pickFileCallback = onResult
        currentFileLauncher?.invoke("*/*")
    }

    private fun getFileName(context: Activity, uri: Uri): String {
        var fileName = "file"
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = it.getString(nameIndex)
                }
            }
        }
        return fileName
    }

    private fun getFileSize(context: Activity, uri: Uri): Long {
        var size = 0L
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1) {
                    size = it.getLong(sizeIndex)
                }
            }
        }
        return size
    }

    private fun compressedFileName(originalName: String, originalBytes: ByteArray, compressedBytes: ByteArray): String {
        return if (originalBytes.size > compressedBytes.size && !originalName.endsWith(".jpg", true) && !originalName.endsWith(".jpeg", true)) {
            val baseName = originalName.substringBeforeLast('.')
            "$baseName.jpg"
        } else originalName
    }

    private fun compressImageIfNeeded(bytes: ByteArray, fileName: String): ByteArray {
        if (bytes.size <= MAX_AVATAR_BYTES) return bytes
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val output = ByteArrayOutputStream()
        var quality = 90
        do {
            output.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            quality -= 10
        } while (output.size() > MAX_AVATAR_BYTES && quality >= 10)
        return output.toByteArray()
    }

    private fun InputStream.readBytes(): ByteArray {
        val buffer = ByteArrayOutputStream()
        var nRead: Int
        val data = ByteArray(16384)
        while (read(data, 0, data.size).also { nRead = it } != -1) {
            buffer.write(data, 0, nRead)
        }
        buffer.flush()
        return buffer.toByteArray()
    }
}
