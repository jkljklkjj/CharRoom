package data.datasource.local

import java.io.File

/** Desktop (JVM) 平台文件操作实现 */
class DesktopFileProvider : FileProvider {
    override fun getAppDataDir(): String =
        File(System.getProperty("user.home"), ".qingliao").absolutePath

    override fun readFile(path: String): ByteArray? {
        val file = File(path)
        if (!file.exists()) return null
        return file.readBytes()
    }

    override fun writeFile(path: String, data: ByteArray) {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeBytes(data)
    }

    override fun fileExists(path: String): Boolean = File(path).exists()

    override fun deleteFile(path: String): Boolean = File(path).delete()

    override fun mkdirs(path: String) {
        File(path).mkdirs()
    }
}
