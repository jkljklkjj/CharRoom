package data.datasource.local

/**
 * Android 平台文件操作实现
 * 注意：Android 平台需要 Context 来获取应用数据目录
 */
class AndroidFileProvider : FileProvider {
    override fun getAppDataDir(): String {
        // Android 平台需要通过 Context 获取 filesDir
        // 这里使用简化实现，实际应用中应该注入 Context
        return "/data/data/com.chatlite/files/.qingliao"
    }

    override fun readFile(path: String): ByteArray? {
        return try {
            val file = java.io.File(path)
            if (!file.exists()) return null
            file.readBytes()
        } catch (e: Exception) {
            null
        }
    }

    override fun writeFile(path: String, data: ByteArray) {
        try {
            val file = java.io.File(path)
            file.parentFile?.mkdirs()
            file.writeBytes(data)
        } catch (e: Exception) {
            // 忽略写入错误
        }
    }

    override fun fileExists(path: String): Boolean {
        return java.io.File(path).exists()
    }

    override fun deleteFile(path: String): Boolean {
        return java.io.File(path).delete()
    }

    override fun mkdirs(path: String) {
        java.io.File(path).mkdirs()
    }
}