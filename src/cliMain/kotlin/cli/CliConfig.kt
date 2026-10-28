package cli

import core.AtomicHistoryFile
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption.*
import java.security.MessageDigest
import java.util.Properties

class CliConfig(private val home: File = File(System.getProperty("user.home"), ".qingliao")) {
    private val file = File(home, "cli/config.properties")
    fun load(): CliOptions {
        if (!file.exists()) return CliOptions()
        val props = Properties().apply { file.inputStream().use { load(it) } }
        return CliOptions.parse(buildList {
            for ((key, flag) in listOf("server" to "--server", "quic.host" to "--quic-host", "quic.port" to "--quic-port")) {
                props.getProperty(key)?.let { add(flag); add(it) }
            }
        }.toTypedArray())
    }
    fun save(server: String, host: String, port: Int) {
        val props = Properties().apply {
            setProperty("server", server); setProperty("quic.host", host); setProperty("quic.port", port.toString())
        }
        AtomicHistoryFile.write(file) { props.store(it, "ChatLite CLI endpoints; no credentials") }
    }
    fun directory(server: String): File {
        val normalized = CliOptions.server(server)
        val hash = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(home, "cli/servers/$hash")
    }
    fun lock(directory: File): AutoCloseable {
        directory.mkdirs()
        val channel = FileChannel.open(File(directory, "session.lock").toPath(), CREATE, WRITE)
        try {
            val lock = requireNotNull(channel.tryLock()) { "该服务器已有 CLI 实例运行，请先关闭后再启动" }
            return AutoCloseable { try { lock.release() } finally { channel.close() } }
        } catch (error: Exception) { channel.close(); throw error }
    }
}
