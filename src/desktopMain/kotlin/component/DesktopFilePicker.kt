package component

import androidx.compose.runtime.Composable
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * 桌面端文件选择器实现
 */
private const val MAX_AVATAR_BYTES = 5 * 1024 * 1024

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
            val compressed = compressImageIfNeeded(bytes)
            val fileName = if (compressed !== bytes && !file.name.endsWith(".jpg", true) && !file.name.endsWith(".jpeg", true)) {
                "${file.nameWithoutExtension}.jpg"
            } else file.name
            onResult(compressed, fileName)
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

    private fun compressImageIfNeeded(bytes: ByteArray): ByteArray {
        if (bytes.size <= MAX_AVATAR_BYTES) return bytes
        val original = ImageIO.read(ByteArrayInputStream(bytes)) ?: return bytes
        val output = ByteArrayOutputStream()
        var quality = 0.9f
        var compressedBytes = bytes
        while (quality > 0.1f) {
            output.reset()
            val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
            val ios = ImageIO.createImageOutputStream(output)
            writer.output = ios
            val params = writer.defaultWriteParam
            if (params.canWriteCompressed()) {
                params.compressionMode = ImageWriteParam.MODE_EXPLICIT
                params.compressionQuality = quality
            }
            writer.write(null, IIOImage(original, null, null), params)
            ios.close()
            writer.dispose()
            val nextBytes = output.toByteArray()
            if (nextBytes.size <= MAX_AVATAR_BYTES) return nextBytes
            compressedBytes = nextBytes
            quality -= 0.1f
        }
        return compressedBytes
    }

    @Composable
    override fun Register() {
        // 桌面端不需要注册，空实现
    }
}
