package com.chatlite.charroom.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import core.model.AppVersionInfo
import core.update.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidUpdateInstaller(context: Context, private val target: UpdateTarget) : UpdateInstaller {
    private val app = context.applicationContext

    @Suppress("DEPRECATION")
    override suspend fun validate(file: File, info: AppVersionInfo) {
        target.validate(info)
        val flags =
            if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES
        val installed = app.packageManager.getPackageInfo(app.packageName, flags)
        val archive =
            requireNotNull(app.packageManager.getPackageArchiveInfo(file.absolutePath, flags)) {
                "Invalid APK"
            }
        val code =
            if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode
            else archive.versionCode.toLong()
        val oldCode =
            if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode
            else installed.versionCode.toLong()
        val oldSigners =
            if (Build.VERSION.SDK_INT >= 28) installed.signingInfo?.apkContentsSigners
            else installed.signatures
        val newSigners =
            if (Build.VERSION.SDK_INT >= 28) archive.signingInfo?.apkContentsSigners
            else archive.signatures
        validateAndroidUpgrade(
            ApkIdentity(
                app.packageName,
                installed.versionName.orEmpty(),
                oldCode,
                oldSigners.orEmpty().map { it.toCharsString() }.toSet(),
            ),
            ApkIdentity(
                archive.packageName,
                archive.versionName.orEmpty(),
                code,
                newSigners.orEmpty().map { it.toCharsString() }.toSet(),
            ),
            info,
        )
    }

    override suspend fun handoff(file: File, info: AppVersionInfo): Boolean =
        withContext(Dispatchers.Main) {
            if (!app.packageManager.canRequestPackageInstalls()) {
                app.startActivity(
                    Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            "package:${app.packageName}".toUri(),
                        )
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                error("Allow installation from this app, then retry installation")
            }
            val uri = FileProvider.getUriForFile(app, app.packageName + ".fileprovider", file)
            app.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
            )
            false // Opening the system confirmation screen is not installation success.
        }
}
