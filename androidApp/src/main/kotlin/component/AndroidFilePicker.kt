package component

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Android平台文件选择器实现
 */
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
                            pickImageCallback?.invoke(data, fileName)
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
