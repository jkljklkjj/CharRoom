package core.update

import core.model.AppVersionInfo
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class UpdateTarget(val os: String, val architecture: String) {
    val packageType: String
        get() =
            when (os) {
                "android" -> "apk"
                "windows" -> "msi"
                "macos" -> "dmg"
                "linux" -> "deb"
                else -> ""
            }

    companion object {
        fun architecture(value: String): String =
            when (value.lowercase()) {
                "amd64",
                "x86_64",
                "x64" -> "x64"
                "aarch64",
                "arm64",
                "arm64-v8a" -> "arm64"
                else -> "unsupported"
            }

        fun desktop(): UpdateTarget {
            val os = requireNotNull(System.getProperty("os.name")).lowercase()
            return UpdateTarget(
                when {
                    os.startsWith("windows") -> "windows"
                    os.contains("mac") || os.contains("darwin") -> "macos"
                    os.contains("linux") -> "linux"
                    else -> "unsupported"
                },
                architecture(requireNotNull(System.getProperty("os.arch"))),
            )
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
    require(digest.digest().joinToString("") { "%02x".format(it) } == info.sha256) {
        "Installer SHA-256 mismatch"
    }
}

suspend fun reusableArtifact(file: File, info: AppVersionInfo): Boolean {
    if (!file.exists()) return false
    return try {
        verifyArtifact(file, info)
        true
    } catch (invalid: IllegalArgumentException) {
        if (!file.delete())
            throw java.io.IOException("Cannot remove corrupt cached installer", invalid)
        false
    }
}

fun artifactName(info: AppVersionInfo) = "${info.sha256}.${info.packageType}"

fun versionAtLeast(current: String, expected: String): Boolean {
    val actual = current.split('.').map { it.toLongOrNull() }
    val minimum = expected.split('.').map { it.toLongOrNull() }
    if (actual.any { it == null } || minimum.any { it == null }) return false
    val comparison =
        (0 until maxOf(actual.size, minimum.size))
            .asSequence()
            .map {
                (actual.getOrNull(it) ?: 0).compareTo(minimum.getOrNull(it) ?: 0)
            }
            .firstOrNull { it != 0 } ?: 0
    return comparison >= 0
}
