package component.chat

import androidx.compose.runtime.*
import core.update.NativeComposerDrafts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import model.ConversationKey

private class ComposerState(private val changed: (String) -> Unit) : MutableState<String> {
    private val state = mutableStateOf("")
    private var edited = false
    override var value: String
        get() = state.value
        set(value) { edited = true; state.value = value; changed(value) }
    override fun component1() = value
    override fun component2(): (String) -> Unit = { value = it }
    fun restore(value: String) { if (!edited) state.value = value }
}

@Composable
fun rememberComposerDraft(account: Int?, conversation: ConversationKey): MutableState<String> {
    val draft = remember(account, conversation) {
        ComposerState { text -> if (account != null && account > 0) NativeComposerDrafts.store.stage(account, conversation, text) }
    }
    LaunchedEffect(account, conversation) {
        if (account != null && account > 0) draft.restore(withContext(Dispatchers.IO) { NativeComposerDrafts.store.read(account, conversation) })
    }
    return draft
}
