package component

import androidx.compose.runtime.Composable
import java.io.File
import java.io.FileInputStream
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * 桌面端文件选择器实现
 */
object DesktopFilePicker : FilePickerProvider {
    override fun pickImage(onResult: (ByteArray, String) -> Unit) {
        val fileChooser = JFileChooser()
        fileChooser.fileFilter = FileNameExtensionFilter(
            "图片文件", "jpg", "jpeg", "png", "gif", "bmp"
        )
        val result = fileChooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            val bytes = FileInputStream(file).readBytes()
            onResult(bytes, file.name)
        }
    }

    override fun pickFile(onResult: (ByteArray, String, Long) -> Unit) {
        val fileChooser = JFileChooser()
        val result = fileChooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            val bytes = FileInputStream(file).readBytes()
            onResult(bytes, file.name, file.length())
        }
    }

    @Composable
    override fun Register() {
        // 桌面端不需要注册，空实现
    }
}
