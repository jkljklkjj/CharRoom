package core.state

import model.ConversationKey

/** Evict data, never the StateFlow itself: existing subscribers keep their bucket identity. */
class ConversationCacheBudget(
    private val maxRows: Int = 4000,
    private val maxCharacters: Long = 2_000_000,
) {
    private data class Entry(
        val protected: () -> Boolean,
        val clear: () -> Unit,
        var rows: Int = 0,
        var characters: Long = 0,
    )

    private val entries = linkedMapOf<ConversationKey, Entry>()
    private var rows = 0
    private var characters = 0L
    private var trimming = false

    @Synchronized
    fun register(key: ConversationKey, protected: () -> Boolean, clear: () -> Unit) {
        entries[key] = Entry(protected, clear)
    }

    @Synchronized
    fun touch(key: ConversationKey) {
        entries.remove(key)?.let { entries[key] = it }
    }

    @Synchronized
    fun changed(key: ConversationKey, count: Int, size: Long) {
        val entry = entries[key] ?: return
        rows += count - entry.rows
        characters += size - entry.characters
        entry.rows = count
        entry.characters = size
        touch(key)
        if (trimming) return
        trimming = true
        try {
            for ((_, candidate) in entries.toList()) {
                if (rows <= maxRows && characters <= maxCharacters) break
                if (candidate.rows > 0 && !candidate.protected()) candidate.clear()
            }
        } finally {
            trimming = false
        }
    }

    @Synchronized
    fun clear() {
        entries.clear()
        rows = 0
        characters = 0
    }
}
