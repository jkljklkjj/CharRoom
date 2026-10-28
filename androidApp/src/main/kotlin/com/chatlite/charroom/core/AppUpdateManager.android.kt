package com.chatlite.charroom.core

import android.content.Context
import android.os.Build
import core.GlobalAppUpdateManager
import core.GlobalApiService
import core.update.*
import java.io.File

/** Installed once for the application; Activity recreation does not recreate downloads. */
fun initAppUpdateManager(context: Context) {
    val app = context.applicationContext
    val architecture = if (Build.SUPPORTED_ABIS.contains("arm64-v8a")) "arm64" else if (Build.SUPPORTED_ABIS.contains("x86_64")) "x64" else "unsupported"
    val target = UpdateTarget("android", architecture)
    UpdateRuntime.target = target
    UpdateRuntime.installedVersion = com.chatlite.charroom.BuildConfig.VERSION_NAME
    val journal = UpdateJournal(File(app.filesDir, "updates"))
    GlobalAppUpdateManager = ManagedAppUpdater(target, journal, AndroidUpdateDownload(app, journal), AndroidUpdateInstaller(app, target),
        checker = { platform, channel -> GlobalApiService.checkAppVersion(platform, channel) },
        installedVersion = com.chatlite.charroom.BuildConfig.VERSION_NAME)
}
