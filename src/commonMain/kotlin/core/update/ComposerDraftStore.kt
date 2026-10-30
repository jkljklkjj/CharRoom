package core.update

import core.AtomicHistoryFile
import java.io.File
import kotlinx.coroutines.*
import model.ConversationKey

/** Plain text drafts belong to an account and typed conversation, independently of update tasks. */
class ComposerDraftStore(private val directory: File) {
    private data class Draft(val text: String, val revision: Long)

    private val lock = Any()
    private val disk = Any()
    private val values = linkedMapOf<String, Draft>()
    private val pending = mutableSetOf<String>()
    private val scope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO +
                CoroutineExceptionHandler { _, error ->
                    core.AppLogger.d("Composer draft save failed: ${error.javaClass.simpleName}")
                }
        )
    private var saveJob: Job? = null
    private var revision = 0L

    private fun key(account: Int, conversation: ConversationKey): String {
        require(account > 0)
        return "$account-${conversation.value.replace(':', '_')}"
    }

    fun stage(account: Int, conversation: ConversationKey, text: String) {
        val key = key(account, conversation)
        synchronized(lock) {
            if (values[key]?.text == text) return
            values.remove(key)
            values[key] = Draft(text, ++revision)
            pending.add(key)
            saveJob?.cancel()
            saveJob = scope.launch {
                delay(250)
                flush()
            }
        }
    }

    fun read(account: Int, conversation: ConversationKey): String {
        val key = key(account, conversation)
        synchronized(lock) {
            values[key]?.let {
                return it.text
            }
        }
        val text =
            synchronized(disk) {
                File(directory, "$key.txt").takeIf { it.exists() }?.readText().orEmpty()
            }
        return synchronized(lock) { values[key]?.text ?: text }
    }

    fun flush() =
        synchronized(disk) {
            while (true) {
                val batch = synchronized(lock) { pending.map { it to values.getValue(it) } }
                if (batch.isEmpty()) break
                for ((key, value) in batch) persistDraft(key, value)
            }
            synchronized(lock) {
                while (values.size > 64) {
                    val key = values.keys.first()
                    if (key in pending) break
                    values.remove(key)
                }
            }
        }

    private fun persistDraft(key: String, value: Draft) {
        val file = File(directory, "$key.txt")
        if (value.text.isEmpty())
            check(!file.exists() || file.delete()) { "Cannot clear composer draft" }
        else AtomicHistoryFile.write(file, value.text)
        synchronized(lock) { if (values[key]?.revision == value.revision) pending.remove(key) }
    }
}

object NativeComposerDrafts {
    var store = ComposerDraftStore(File(System.getProperty("user.home"), ".charroom/drafts"))
        private set

    fun installDirectory(directory: File) {
        store = ComposerDraftStore(directory)
    }
}
