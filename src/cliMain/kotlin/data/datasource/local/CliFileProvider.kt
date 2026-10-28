package data.datasource.local

import java.io.File

/**
 * CLI 平台文件操作实现
 */
class CliFileProvider(private val directory: File) : FileProvider {
    override fun getAppDataDir(): String {
        return directory.absolutePath
    }

    override fun readFile(path: String): ByteArray? {
        val file = File(path)
        if (!file.exists()) return null
        return file.readBytes()
    }

    override fun writeFile(path: String, data: ByteArray) {
        val file = File(path)
        file.parentFile?.mkdirs()
        core.AtomicHistoryFile.write(file) { it.write(data) }
    }

    override fun fileExists(path: String): Boolean {
        return File(path).exists()
    }

    override fun deleteFile(path: String): Boolean {
        return File(path).delete()
    }

    override fun mkdirs(path: String) {
        File(path).mkdirs()
    }
}
