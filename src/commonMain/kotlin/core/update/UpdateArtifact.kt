package core.update

import core.model.AppVersionInfo
import java.io.File
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class UpdateTarget(val os: String, val architecture: String) {
    val packageType: String get() = when (os) { "android" -> "apk"; "windows" -> "msi"; "macos" -> "dmg"; "linux" -> "deb"; else -> "" }
    fun validate(info: AppVersionInfo) {
        require(info.versionName.matches(Regex("[0-9]+(\\.[0-9]+){0,3}"))) { "Invalid update version" }
        require(info.packageType == packageType && packageType.isNotEmpty()) { "Installer does not match this operating system" }
        require(info.architecture == architecture || (os == "android" && info.architecture == "universal")) { "Installer does not match this architecture" }
        require(info.versionCode >= 0 && (os != "android" || info.versionCode > 0)) { "Invalid installer versionCode" }
        require(info.fileSize in 1..(2L * 1024 * 1024 * 1024)) { "Invalid installer size" }
        require(info.sha256.matches(Regex("[0-9a-f]{64}"))) { "Missing installer SHA-256" }
        val uri = URI(info.downloadUrl)
        require(uri.scheme == "https" && uri.host != null && uri.userInfo == null) { "HTTPS installer URL required" }
    }
    companion object {
        fun architecture(value: String): String = when (value.lowercase()) {
            "amd64", "x86_64", "x64" -> "x64"
            "aarch64", "arm64", "arm64-v8a" -> "arm64"
            else -> "unsupported"
        }
        fun desktop(): UpdateTarget {
            val os = requireNotNull(System.getProperty("os.name")).lowercase()
            return UpdateTarget(when { os.startsWith("windows") -> "windows"; os.contains("mac") || os.contains("darwin") -> "macos"; os.contains("linux") -> "linux"; else -> "unsupported" }, architecture(requireNotNull(System.getProperty("os.arch"))))
        }
    }
}

suspend fun verifyArtifact(file: File, info: AppVersionInfo) {
    require(file.isFile && file.length() == info.fileSize) { "Installer is incomplete" }
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    require(digest.digest().joinToString("") { "%02x".format(it) } == info.sha256) { "Installer SHA-256 mismatch" }
}

suspend fun reusableArtifact(file: File, info: AppVersionInfo): Boolean {
    if (!file.exists()) return false
    try { verifyArtifact(file, info); return true }
    catch (invalid: IllegalArgumentException) { check(file.delete()) { "Cannot remove corrupt cached installer" }; return false }
}

fun artifactName(info: AppVersionInfo) = "${info.sha256}.${info.packageType}"
fun versionAtLeast(current: String, expected: String): Boolean {
    val a = current.split('.').map { it.toLongOrNull() ?: return false }
    val b = expected.split('.').map { it.toLongOrNull() ?: return false }
    for (i in 0 until maxOf(a.size, b.size)) {
        val comparison = (a.getOrElse(i) { 0 }).compareTo(b.getOrElse(i) { 0 })
        if (comparison != 0) return comparison > 0
    }
    return true
}
