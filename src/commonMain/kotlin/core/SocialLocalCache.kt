package core

import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.*

data class SocialFilters(val query: String = "", val tag: String = "", val channel: String = "all")

data class SocialScrollPosition(val index: Int = 0, val offset: Int = 0)

@Serializable
data class SocialAnswerDraft(val day: String, val content: String, val revision: String)

@Serializable private data class CachedOwnProfile(val savedAt: Long, val profile: SocialProfile)

private data class SocialDirectoryPage(val savedAt: Long, val value: SocialDirectory, val size: Int)

/**
 * One active login. Public pages stay in bounded memory; only own profile and draft reach disk.
 * Disk methods run on IO and use a separate lock; UI staging never waits for fsync. Pending logout
 * deletions are drained before any later disk access for the same store.
 */
class SocialLocalCache(
    private val root: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val writeFile: (File, String) -> Unit = { file, value ->
        AtomicHistoryFile.write(file, value)
    },
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var current: Session? = null
    private val diskLock = Any()
    private val pendingDeletes = mutableSetOf<String>()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun drainDeletes() {
        val accounts = synchronized(this) { pendingDeletes.toList() }
        accounts.forEach { account ->
            listOf("profile", "draft").forEach { kind ->
                val file = File(root, "$account-$kind.json")
                check(!file.exists() || file.delete()) { "Cannot clear social cache" }
            }
            synchronized(this) { pendingDeletes.remove(account) }
        }
    }

    private fun <T> disk(work: () -> T): T =
        synchronized(diskLock) {
            drainDeletes()
            work()
        }

    @Synchronized
    fun activate(account: String): Session {
        require(account.matches(Regex("[0-9]+")))
        // Token refresh can recreate the screen; only account switch/logout changes the owner.
        return current?.takeIf { it.account == account } ?: Session(account).also { current = it }
    }

    fun logout() {
        synchronized(this) {
            current?.let { pendingDeletes.add(it.account) }
            current = null
        }
        ioScope.launch {
            try {
                disk {}
            } catch (_: Exception) {
                /* The next disk access retries deletion before reading. */
            }
        }
    }

    inner class Session internal constructor(val account: String) {
        private val pages = linkedMapOf<SocialFilters, SocialDirectoryPage>()
        private var stagedDraftVersion: String? = null
        private var stagedDraft: SocialAnswerDraft? = null
        private var clearedDraftVersion: String? = null
        private var cachedDaily: Pair<Long, DailyQuestion>? = null
        private val positions = linkedMapOf<String, SocialScrollPosition>()
        var filters = SocialFilters()
            private set

        var revision: Long = 0
            private set

        val active: Boolean
            get() = synchronized(this@SocialLocalCache) { current === this }

        internal fun file(kind: String) = File(root, "$account-$kind.json")

        fun today(): String =
            Instant.ofEpochMilli(clock())
                .atZone(ZoneId.of("Asia/Shanghai"))
                .toLocalDate()
                .toString()

        fun invalidate() =
            synchronized(this@SocialLocalCache) {
                if (active) {
                    revision++
                    pages.clear()
                    cachedDaily = null
                    positions.clear()
                }
            }

        fun rememberFilters(value: SocialFilters) =
            synchronized(this@SocialLocalCache) {
                if (active) filters = value
            }

        fun position(key: String): SocialScrollPosition =
            synchronized(this@SocialLocalCache) {
                if (active) positions[key] ?: SocialScrollPosition() else SocialScrollPosition()
            }

        fun rememberPosition(key: String, value: SocialScrollPosition) =
            synchronized(this@SocialLocalCache) {
                if (active) {
                    positions.remove(key)
                    positions[key] = value
                    while (positions.size > 8) positions.remove(positions.keys.first())
                }
            }

        fun directory(key: SocialFilters): SocialDirectory? =
            synchronized(this@SocialLocalCache) {
                if (!active) return@synchronized null
                pages[key]?.takeIf { clock() - it.savedAt in 0..20_000 }?.value
            }

        fun putDirectory(key: SocialFilters, value: SocialDirectory, expected: Long) =
            synchronized(this@SocialLocalCache) {
                if (active && expected == revision) {
                    val size = json.encodeToString(value).length
                    pages.remove(key)
                    if (size <= 250_000) pages[key] = SocialDirectoryPage(clock(), value, size)
                    while (pages.size > 8 || pages.values.sumOf { it.size } > 250_000) pages.remove(
                        pages.keys.first()
                    )
                }
            }

        fun daily(): DailyQuestion? =
            synchronized(this@SocialLocalCache) {
                cachedDaily
                    ?.takeIf {
                        active && clock() - it.first in 0..20_000 && it.second.day == today()
                    }
                    ?.second
            }

        fun putDaily(value: DailyQuestion, expected: Long) =
            synchronized(this@SocialLocalCache) {
                if (active && expected == revision) {
                    val validDay = value.day == today()
                    if (validDay && json.encodeToString(value).length <= 250_000)
                        cachedDaily = clock() to value
                }
            }

        private inline fun <reified T> read(kind: String): T? {
            val path = file(kind)
            val available = active && path.isFile
            if (!available || path.length() > 250_000) return null
            return try {
                json.decodeFromString<T>(path.readText())
            } catch (_: Exception) {
                null
            }
        }

        fun profile(): SocialProfile? = disk {
            read<CachedOwnProfile>("profile")
                ?.takeIf { clock() - it.savedAt in 0..86_400_000 }
                ?.profile
                ?.takeIf { it.id == account && it.relation == "SELF" }
        }

        fun saveProfile(value: SocialProfile) {
            val encoded = json.encodeToString(CachedOwnProfile(clock(), value))
            disk {
                val ownedProfile = active && value.id == account
                val validProfile = value.relation == "SELF" && encoded.length <= 250_000
                if (ownedProfile && validProfile) writeFile(file("profile"), encoded)
            }
        }

        fun draft(): SocialAnswerDraft? {
            synchronized(this@SocialLocalCache) {
                if (!active || stagedDraft != null)
                    return stagedDraft?.takeIf { active && validDraft(it) }
            }
            val stored = disk { read<SocialAnswerDraft>("draft") }
            return synchronized(this@SocialLocalCache) {
                (stagedDraft ?: stored)?.takeIf { active && validDraft(it) }
            }
        }

        private fun validDraft(value: SocialAnswerDraft) =
            value.day == today() &&
                value.content.length <= 500 &&
                value.revision != clearedDraftVersion

        fun stageDraft(version: String) =
            synchronized(this@SocialLocalCache) {
                if (active) stagedDraftVersion = version
            }

        fun stageDraft(value: SocialAnswerDraft) =
            synchronized(this@SocialLocalCache) {
                if (active) {
                    stagedDraftVersion = value.revision
                    stagedDraft = value
                }
            }

        fun saveDraft(day: String, content: String, version: String) {
            val encoded = json.encodeToString(SocialAnswerDraft(day, content, version))
            disk {
                val valid =
                    synchronized(this@SocialLocalCache) {
                        active &&
                            version != clearedDraftVersion &&
                            (stagedDraftVersion == null || stagedDraftVersion == version) &&
                            day == today() &&
                            content.length <= 500
                    }
                if (valid) writeFile(file("draft"), encoded)
            }
        }

        fun clearSubmittedDraft(version: String) = disk {
            val stored = draft()
            val clear =
                synchronized(this@SocialLocalCache) {
                    val currentDraft = stagedDraftVersion == null || stagedDraftVersion == version
                    val ownedDraft = active && currentDraft
                    if (ownedDraft && stored?.revision == version) {
                        clearedDraftVersion = version
                        stagedDraft = null
                        true
                    } else false
                }
            if (clear) file("draft").delete()
        }
    }
}

object NativeSocialCache {
    // Android sets its private files directory before composing UI; no JVM preferences on Android.
    var cache = SocialLocalCache(File(System.getProperty("user.home"), ".charroom/social"))
        private set

    fun installDirectory(root: File) {
        cache = SocialLocalCache(root)
    }
}
