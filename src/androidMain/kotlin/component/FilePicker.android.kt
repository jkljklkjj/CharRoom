package component

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Android平台文件选择器实现
 */
actual object FilePicker {
    private var pickImageCallback: ((ByteArray, String) -> Unit)? = null
    private var pickFileCallback: ((ByteArray, String, Long) -> Unit)? = null

    @Composable
    actual fun pickImage(onResult: (ByteArray, String) -> Unit) {
        val context = LocalContext.current as Activity
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
            onResult = { uri: Uri? ->
                uri?.let {
                    GlobalScope.launch(Dispatchers.IO) {
                        val inputStream = context.contentResolver.openInputStream(it)
                        val fileName = getFileName(context, it)
                        val bytes = inputStream?.readBytes()
                        inputStream?.close()
                        bytes?.let { data ->
                            onResult(data, fileName)
                        }
                    }
                }
            }
        )

        remember {
            { launcher.launch("image/*") }
        }.invoke()
    }

    @Composable
    actual fun pickFile(onResult: (ByteArray, String, Long) -> Unit) {
        val context = LocalContext.current as Activity
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
            onResult = { uri: Uri? ->
                uri?.let {
                    GlobalScope.launch(Dispatchers.IO) {
                        val inputStream = context.contentResolver.openInputStream(it)
                        val fileName = getFileName(context, it)
                        val fileSize = getFileSize(context, it)
                        val bytes = inputStream?.readBytes()
                        inputStream?.close()
                        bytes?.let { data ->
                            onResult(data, fileName, fileSize)
                        }
                    }
                }
            }
        )

        remember {
            { launcher.launch("*/*") }
        }.invoke()
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
