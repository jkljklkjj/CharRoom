package core.update

import core.model.AppVersionInfo

data class ApkIdentity(val packageName: String, val version: String, val code: Long, val signers: Set<String>)

fun validateAndroidUpgrade(installed: ApkIdentity, archive: ApkIdentity, expected: AppVersionInfo) {
    require(archive.packageName == installed.packageName) { "APK belongs to another application" }
    require(archive.code == expected.versionCode.toLong() && archive.code > installed.code) { "APK versionCode is not the published upgrade" }
    require(versionAtLeast(archive.version, expected.versionName) && versionAtLeast(expected.versionName, archive.version)) { "APK version name differs from release" }
    require(installed.signers.isNotEmpty() && installed.signers == archive.signers) { "APK signing identity differs from installed app" }
}
