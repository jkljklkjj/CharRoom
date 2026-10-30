package core.update

import core.AppConfig

/** Application lifecycle hooks kept separate from artifact transport and installation. */
data class UpdateLifecycle(
    val prepare: suspend () -> Unit = { UpdatePreparation.save() },
    val exit: suspend () -> Unit = {},
    val installedVersion: String = AppConfig.VERSION_NAME,
)
