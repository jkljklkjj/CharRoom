package core.update

object UpdateRuntime {
    var target: UpdateTarget = UpdateTarget.desktop()
    var installedVersion: String = core.AppConfig.VERSION_NAME
}
