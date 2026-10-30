package component.user

/** Navigation callbacks owned by the host of the contact directory. */
data class DirectoryActions(
    val onOpenSearch: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenApplications: () -> Unit = {},
    val onOpenProfile: () -> Unit = {},
    val onOpenDiscovery: () -> Unit = {},
)
