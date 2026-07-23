package data.datasource.local

/**
 * 跨平台文件操作接口
 * 各平台提供具体实现
 */
interface FileProvider {
    fun getAppDataDir(): String
    fun readFile(path: String): ByteArray?
    fun writeFile(path: String, data: ByteArray)
    fun fileExists(path: String): Boolean
    fun deleteFile(path: String): Boolean
    fun mkdirs(path: String)
}

/**
 * 全局文件提供者实例
 * 在应用启动时设置为平台具体的实现
 */
var fileProvider: FileProvider = object : FileProvider {
    override fun getAppDataDir(): String = error("FileProvider not initialized")
    override fun readFile(path: String): ByteArray? = error("FileProvider not initialized")
    override fun writeFile(path: String, data: ByteArray) = error("FileProvider not initialized")
    override fun fileExists(path: String): Boolean = error("FileProvider not initialized")
    override fun deleteFile(path: String): Boolean = error("FileProvider not initialized")
    override fun mkdirs(path: String) = error("FileProvider not initialized")
}
