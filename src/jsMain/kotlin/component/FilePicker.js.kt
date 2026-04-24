package component

import androidx.compose.runtime.Composable
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader
import kotlin.js.ExperimentalJsExport

/**
 * JS/Web平台文件选择器实现
 */
@OptIn(ExperimentalJsExport::class)
actual object FilePicker {
    @Composable
    actual fun pickImage(onResult: (ByteArray, String) -> Unit) {
        pickFileByType("image/*") { bytes, fileName, _ ->
            onResult(bytes, fileName)
        }
    }

    @Composable
    actual fun pickFile(onResult: (ByteArray, String, Long) -> Unit) {
        pickFileByType("*/*", onResult)
    }

    private fun pickFileByType(accept: String, onResult: (ByteArray, String, Long) -> Unit) {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.accept = accept
        input.style.display = "none"

        input.onchange = { event ->
            val file = input.files?.item(0)
            file?.let { selectedFile ->
                val reader = FileReader()
                reader.onload = { loadEvent ->
                    val arrayBuffer = loadEvent.target.asDynamic().result as ArrayBuffer
                    val bytes = ByteArray(arrayBuffer.byteLength)
                    val view = Uint8Array(arrayBuffer)
                    for (i in 0 until arrayBuffer.byteLength) {
                        bytes[i] = view[i]
                    }
                    onResult(bytes, selectedFile.name, selectedFile.size)
                }
                reader.readAsArrayBuffer(selectedFile)
            }
            document.body?.removeChild(input)
        }

        document.body?.appendChild(input)
        input.click()
    }

    private external class Uint8Array(buffer: ArrayBuffer) {
        operator fun get(index: Int): Byte
    }
}
