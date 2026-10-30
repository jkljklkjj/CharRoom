package core.update

import core.AppUpdateManager
import core.UpdateState
import core.model.AppVersionInfo
import core.model.VersionCheckResult
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface UpdateInstaller {
    suspend fun validate(file: File, info: AppVersionInfo)

    /**
     * Return true only when an independent installer helper owns the remaining process lifetime.
     */
    suspend fun handoff(file: File, info: AppVersionInfo): Boolean
}

class ManagedAppUpdater(
    private val target: UpdateTarget,
    private val journal: UpdateJournal,
    private val download: UpdateDownload,
    private val installer: UpdateInstaller,
    private val checker: suspend (String, String) -> VersionCheckResult?,
    private val lifecycle: UpdateLifecycle = UpdateLifecycle(),
) : AppUpdateManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    private val visible = MutableStateFlow(false)
    override val updateState = state.asStateFlow()
    override val promptVisible = visible.asStateFlow()
    private var worker: Job? = null
    private var generation = 0L
    @Volatile private var version: AppVersionInfo? = null

    private fun busy() =
        state.value is UpdateState.Downloading ||
            state.value is UpdateState.Verifying ||
            state.value is UpdateState.Preparing ||
            state.value is UpdateState.Installing ||
            state.value is UpdateState.Checking

    @Synchronized
    private fun start(reportErrors: Boolean = true, action: suspend () -> Unit) {
        val previous = worker
        generation++
        val current = generation
        previous?.cancel()
        download.close()
        worker = scope.launch {
            previous?.join()
            core.recoverOperation(
                onFailure = { error ->
                    synchronized(this@ManagedAppUpdater) {
                        if (current == generation) {
                            state.value =
                                UpdateState.Failed(error.message ?: "Update failed", version)
                            visible.value = reportErrors || version?.forceUpdate == true
                        }
                    }
                }
            ) {
                action()
            }
        }
    }

    override suspend fun checkForUpdates(
        platform: String,
        channel: String,
        autoDownload: Boolean,
        silent: Boolean,
    ) = beginCheck(platform, channel, autoDownload, silent)

    @Synchronized
    private fun beginCheck(
        platform: String,
        channel: String,
        autoDownload: Boolean,
        silent: Boolean,
    ) {
        if (busy()) {
            if (!silent) visible.value = true
            return
        }
        if (state.value is UpdateState.Downloaded) {
            if (!silent) visible.value = true
            return
        }
        state.value = UpdateState.Checking
        visible.value = !silent
        start(reportErrors = !silent) {
            val result = requireNotNull(checker(platform, channel)) { "Update check failed" }
            if (!result.hasUpdate || result.latestVersion == null) {
                version = null
                state.value = UpdateState.NoUpdate
                return@start
            }
            val info = result.latestVersion
            target.validate(info)
            version = info
            state.value = UpdateState.Available(info)
            visible.value = true
            if (autoDownload) downloadNow(info)
        }
    }

    override suspend fun downloadUpdate(versionInfo: AppVersionInfo) = beginDownload(versionInfo)

    @Synchronized
    private fun beginDownload(versionInfo: AppVersionInfo) {
        if (busy()) return
        target.validate(versionInfo)
        version = versionInfo
        state.value = UpdateState.Downloading(0, versionInfo.fileSize, versionInfo)
        visible.value = true
        start { downloadNow(versionInfo) }
    }

    private suspend fun downloadNow(info: AppVersionInfo) {
        if (journal.read()?.version?.sha256?.let { it != info.sha256 } == true) download.cancel()
        journal.directory.mkdirs()
        journal.directory
            .listFiles()
            ?.filter {
                it.name.matches(Regex("[0-9a-f]{64}\\.(apk|msi|dmg|deb)(\\.part(\\.etag)?)?")) &&
                    !it.name.startsWith(info.sha256)
            }
            ?.forEach { check(it.delete()) { "Cannot clean old installer" } }
        journal.write(
            UpdatePlan(
                info,
                "DOWNLOADING",
                journal.read()?.takeIf { it.version.sha256 == info.sha256 }?.downloadId ?: -1,
            )
        )
        state.value = UpdateState.Downloading(0, info.fileSize, info)
        val activeJob = currentCoroutineContext().job
        val file =
            download.download(info) { progress, total ->
                if (activeJob.isActive)
                    state.value = UpdateState.Downloading(progress.coerceIn(0, 100), total, info)
            }
        currentCoroutineContext().ensureActive()
        state.value = UpdateState.Verifying(info)
        verifyArtifact(file, info)
        installer.validate(file, info)
        currentCoroutineContext().ensureActive()
        journal.write(UpdatePlan(info, "READY"))
        state.value = UpdateState.Downloaded(file.absolutePath, info)
        visible.value = true
    }

    override suspend fun installUpdate(filePath: String) = beginInstall(filePath)

    @Synchronized
    private fun beginInstall(filePath: String) {
        val ready = state.value as? UpdateState.Downloaded ?: return
        val info = ready.versionInfo
        require(File(filePath).canonicalFile == journal.artifact(info).canonicalFile) {
            "Installer path is not the verified update"
        }
        state.value = UpdateState.Preparing(info)
        start {
            val file = journal.artifact(info)
            verifyArtifact(file, info)
            installer.validate(file, info)
            withTimeout(20000) { lifecycle.prepare() }
            File(journal.directory, "receipt.json").delete()
            journal.write(UpdatePlan(info, "INSTALLING"))
            state.value = UpdateState.Installing
            val shouldExit = installer.handoff(file, info)
            state.value = UpdateState.HandedOff(info)
            if (shouldExit) lifecycle.exit()
        }
    }

    override suspend fun restore() {
        beginRestore()?.join()
    }

    @Synchronized
    private fun beginRestore(): Job? {
        if (busy()) return null
        state.value = UpdateState.Checking
        start {
            val plan = journal.read()
            if (plan == null) {
                state.value = UpdateState.Idle
                return@start
            }
            target.validate(plan.version)
            version = plan.version
            if (versionAtLeast(lifecycle.installedVersion, plan.version.versionName)) {
                journal.artifact(plan.version).delete()
                journal.clear()
                File(journal.directory, "receipt.json").delete()
                state.value = UpdateState.Completed(lifecycle.installedVersion)
                visible.value = true
            } else if (plan.phase == "DOWNLOADING") downloadNow(plan.version)
            else {
                val file = journal.artifact(plan.version)
                state.value = UpdateState.Verifying(plan.version)
                verifyArtifact(file, plan.version)
                installer.validate(file, plan.version)
                val receipt = if (plan.phase == "INSTALLING") journal.receipt() else null
                if (receipt != null && receipt.exitCode !in setOf(0, 3010)) {
                    state.value =
                        UpdateState.Failed(
                            "Installer exited with code ${receipt.exitCode}; current version retained",
                            plan.version,
                        )
                } else {
                    journal.write(plan.copy(phase = "READY", downloadId = -1))
                    state.value = UpdateState.Downloaded(file.absolutePath, plan.version)
                }
                visible.value = true
            }
        }
        return worker
    }

    @Synchronized
    override fun cancelDownload() {
        val installing =
            state.value is UpdateState.Preparing || state.value is UpdateState.Installing
        if (version?.forceUpdate == true || installing) return
        worker?.cancel()
        download.cancel()
        start {
            version?.let { info ->
                for (file in
                    listOf(
                        journal.artifact(info),
                        File(journal.artifact(info).path + ".part"),
                        File(journal.artifact(info).path + ".part.etag"),
                    )) check(!file.exists() || file.delete()) { "Cannot clean cancelled installer" }
            }
            journal.clear()
            state.value = UpdateState.Idle
            visible.value = false
        }
    }

    override fun dismiss() {
        if (version?.forceUpdate != true) visible.value = false
    }

    @Synchronized
    override fun close() {
        generation++
        worker?.cancel()
        scope.cancel()
        download.close()
    }
}
