package component.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chatlite.i18n.LocalStrings
import core.GlobalAppUpdateManager
import core.UpdateState
import core.update.UpdateRuntime
import kotlinx.coroutines.launch

/** One prompt at the application root, including the login screen. Never installs without confirmation. */
@Composable
fun UpdateHost() {
    val manager = GlobalAppUpdateManager
    val state by manager.updateState.collectAsState()
    val visible by manager.promptVisible.collectAsState()
    val scope = rememberCoroutineScope()
    val strings = LocalStrings.current
    LaunchedEffect(manager) {
        manager.restore()
        if (manager.updateState.value is UpdateState.Idle) manager.checkForUpdates(if (UpdateRuntime.target.os == "android") "android" else "desktop", silent = true)
    }
    if (!visible) return
    val info = when (val value = state) {
        is UpdateState.Available -> value.versionInfo
        is UpdateState.Downloading -> value.versionInfo
        is UpdateState.Downloaded -> value.versionInfo
        is UpdateState.Verifying -> value.versionInfo
        is UpdateState.Preparing -> value.versionInfo
        is UpdateState.HandedOff -> value.versionInfo
        is UpdateState.Failed -> value.versionInfo
        else -> null
    }
    val working = state is UpdateState.Checking || state is UpdateState.Verifying || state is UpdateState.Preparing || state is UpdateState.Installing
    AlertDialog(
        onDismissRequest = { if (!working) manager.dismiss() },
        title = { Text(if (info != null) strings["settings.update.found"].format(info.versionName) else strings["settings.update.title"]) },
        text = {
            Column(Modifier.widthIn(max = 480.dp).heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (val value = state) {
                    is UpdateState.Checking -> Text(strings["settings.update.checking"])
                    is UpdateState.NoUpdate -> Text(strings["settings.up.to.date"])
                    is UpdateState.Completed -> Text(strings["update.completed"].format(value.version))
                    is UpdateState.Downloading -> {
                        Text(strings["update.progress"].format(value.progress, value.total / 1024 / 1024))
                        LinearProgressIndicator(value.progress / 100f, Modifier.fillMaxWidth())
                    }
                    is UpdateState.Verifying -> { Text(strings["update.verifying"]); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    is UpdateState.Preparing -> { Text(strings["update.preparing"]); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    is UpdateState.Installing -> Text(strings["update.installing"])
                    is UpdateState.HandedOff -> Text(strings["update.handedOff"])
                    is UpdateState.Failed -> { Text(strings["update.failed"], color = MaterialTheme.colors.error); Text(value.error) }
                    is UpdateState.Downloaded -> Text(strings["update.ready"])
                    else -> Unit
                }
                info?.let {
                    if (it.forceUpdate) Text(strings["settings.update.force"], color = MaterialTheme.colors.error)
                    Text(it.updateContent)
                    Text("${it.packageType.uppercase()} · ${it.architecture} · ${it.fileSize / 1024 / 1024} MiB", style = MaterialTheme.typography.caption)
                }
            }
        },
        confirmButton = {
            when (val value = state) {
                is UpdateState.Downloaded -> Button(onClick = { scope.launch { manager.installUpdate(value.filePath) } }) { Text(strings["settings.update.install"]) }
                is UpdateState.Available -> Button(onClick = { scope.launch { manager.downloadUpdate(value.versionInfo) } }) { Text(strings["settings.update.now"]) }
                is UpdateState.Failed -> Button(onClick = { scope.launch {
                    if (value.versionInfo != null) manager.downloadUpdate(value.versionInfo)
                    else manager.checkForUpdates(if (UpdateRuntime.target.os == "android") "android" else "desktop")
                } }) { Text(strings["update.retry"]) }
                is UpdateState.NoUpdate, is UpdateState.Completed -> TextButton(onClick = manager::dismiss) { Text(strings["settings.confirm"]) }
                is UpdateState.HandedOff -> Button(onClick = { scope.launch { manager.restore() } }) { Text(strings["update.recheck"]) }
                else -> Unit
            }
        },
        dismissButton = {
            if (state is UpdateState.Failed && info != null) {
                TextButton(onClick = { scope.launch { manager.checkForUpdates(if (UpdateRuntime.target.os == "android") "android" else "desktop") } }) {
                    Text(strings["update.recheckRelease"])
                }
            }
            if (info?.forceUpdate != true && !working && state !is UpdateState.NoUpdate && state !is UpdateState.Completed) {
                TextButton(onClick = { if (state is UpdateState.Downloading) manager.cancelDownload() else manager.dismiss() }) {
                    Text(strings[if (state is UpdateState.Downloading) "settings.cancel" else "settings.update.later"])
                }
            }
        }
    )
}
