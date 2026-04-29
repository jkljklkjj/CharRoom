package component

import androidx.compose.runtime.Composable
import java.io.File
import java.nio.file.Files
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Desktop平台文件选择器实现
 */
actual object FilePicker {
    actual fun pickImage(onResult: (ByteArray, String) -> Unit) {
        val fileChooser = JFileChooser()
        fileChooser.dialogTitle = "选择图片"
        fileChooser.fileFilter = FileNameExtensionFilter(
            "图片文件 (*.jpg, *.jpeg, *.png, *.gif, *.webp)",
            "jpg", "jpeg", "png", "gif", "webp"
        )

        val result = fileChooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            val selectedFile = fileChooser.selectedFile
            val bytes = Files.readAllBytes(selectedFile.toPath())
            onResult(bytes, selectedFile.name)
        }
    }

    actual fun pickFile(onResult: (ByteArray, String, Long) -> Unit) {
        val fileChooser = JFileChooser()
        fileChooser.dialogTitle = "选择文件"

        val result = fileChooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            val selectedFile = fileChooser.selectedFile
            val bytes = Files.readAllBytes(selectedFile.toPath())
            onResult(bytes, selectedFile.name, selectedFile.length())
        }
    }
}
