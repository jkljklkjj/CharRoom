package core.update

/** Hooks belong to the active UI/session; failures stop installation before application exit. */
object UpdatePreparation {
    private val hooks = linkedMapOf<Any, suspend () -> Unit>()
    @Synchronized fun register(owner: Any, save: suspend () -> Unit) { hooks[owner] = save }
    @Synchronized fun remove(owner: Any) { hooks.remove(owner) }
    suspend fun save() {
        NativeComposerDrafts.store.flush()
        val pending = synchronized(this) { hooks.values.toList() }
        for (hook in pending) hook()
    }
}
