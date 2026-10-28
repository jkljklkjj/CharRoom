package core.model

import kotlinx.serialization.Serializable

/** GET /app/version/check; version ordering is decided by the server. */
@Serializable
data class PublishedAppVersion(
    val latestVersion: String,
    val updateAvailable: Boolean,
    val forceUpdate: Boolean,
    val downloadUrl: String,
    val releaseNotes: String,
    val packageType: String,
    val architecture: String,
    val fileSize: Long,
    val sha256: String,
    val versionCode: Int,
    val message: String? = null
) {
    fun toCheckResult(): VersionCheckResult = VersionCheckResult(
        hasUpdate = updateAvailable,
        latestVersion = if (updateAvailable) AppVersionInfo(
            versionName = latestVersion, updateContent = releaseNotes,
            downloadUrl = downloadUrl, forceUpdate = forceUpdate, packageType = packageType,
            architecture = architecture, fileSize = fileSize, sha256 = sha256, versionCode = versionCode
        ) else null,
        message = message
    )
}
