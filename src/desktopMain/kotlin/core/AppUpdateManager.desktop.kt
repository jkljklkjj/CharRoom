package core

import core.update.*
import java.io.File
import kotlin.system.exitProcess

fun initAppUpdateManager() {
    val target = UpdateTarget.desktop()
    UpdateRuntime.target = target
    val journal = UpdateJournal(File(System.getProperty("user.home"), ".charroom/updates"))
    GlobalAppUpdateManager =
        ManagedAppUpdater(
            target,
            journal,
            HttpUpdateDownload(journal),
            DesktopUpdateInstaller(target, journal),
            checker = { platform, channel -> GlobalApiService.checkAppVersion(platform, channel) },
            lifecycle =
                core.update.UpdateLifecycle(
                    prepare = {
                        UpdatePreparation.save()
                        if (target.os == "windows") {
                            Chat.stop()
                            UpdatePreparation.save()
                        }
                    },
                    exit = { exitProcess(0) },
                ),
        )
}
