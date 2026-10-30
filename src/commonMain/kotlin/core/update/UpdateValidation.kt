package core.update

import core.model.AppVersionInfo
import java.net.URI

fun UpdateTarget.validate(info: AppVersionInfo) {
    require(info.versionName.matches(Regex("[0-9]+(\\.[0-9]+){0,3}"))) {
        "Invalid update version"
    }
    require(info.packageType == packageType && packageType.isNotEmpty()) {
        "Installer does not match this operating system"
    }
    require(
        info.architecture == architecture || (os == "android" && info.architecture == "universal")
    ) {
        "Installer does not match this architecture"
    }
    require(info.versionCode >= 0 && (os != "android" || info.versionCode > 0)) {
        "Invalid installer versionCode"
    }
    require(info.fileSize in 1..(2L * 1024 * 1024 * 1024)) { "Invalid installer size" }
    require(info.sha256.matches(Regex("[0-9a-f]{64}"))) { "Missing installer SHA-256" }
    val uri = URI(info.downloadUrl)
    require(uri.scheme == "https" && uri.host != null && uri.userInfo == null) {
        "HTTPS installer URL required"
    }
}
