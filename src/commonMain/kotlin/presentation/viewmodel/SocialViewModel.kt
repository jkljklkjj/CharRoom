package presentation.viewmodel

import core.NativeSocialCache
import core.SocialFilters
import core.SocialScrollPosition
import data.repository.SocialRepository
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import model.*

data class SocialUiState(
    val tab: String = "people",
    val people: List<SocialProfile> = emptyList(),
    val cursor: Int? = null,
    val profile: SocialProfile? = null,
    val daily: DailyQuestion? = null,
    val room: EncounterRoom = EncounterRoom(),
    val blocked: List<SocialBlockedUser> = emptyList(),
    val busy: Boolean = false,
    val error: String = "",
    val profileReady: Boolean = false,
    val answerDraft: String = "",
)

class SocialViewModel(private val repository: SocialRepository, private val userId: String) :
    AutoCloseable {
    private val cache = NativeSocialCache.cache.activate(userId)
    val filters
        get() = cache.filters

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(SocialUiState())
    private val roomState = EncounterRoomState(mutable)
    val state = mutable.asStateFlow()
    private val roomMutex = Mutex()
    private var profileGeneration = 0
    private var directoryGeneration = 0
    private var dailyGeneration = 0
    private var pendingOperations = 0
    private var draftVersion = UUID.randomUUID().toString()
    private var draftDay: String? = null
    private var draftJob: Job? = null
    private var pendingDraft: core.SocialAnswerDraft? = null
    private val pollWake = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        core.update.UpdatePreparation.register(this) {
            val draft = withContext(Dispatchers.Main.immediate) { pendingDraft }
            if (draft != null) withContext(Dispatchers.IO) { persistDraft(draft) }
        }
        search(filters.query, filters.tag, filters.channel)
        scope.launch {
            combine(core.AppActivity.foreground, mutable.map { it.tab }.distinctUntilChanged()) {
                    foreground,
                    tab ->
                    foreground && tab == "encounter"
                }
                .distinctUntilChanged()
                .collectLatest { active ->
                    if (!active) return@collectLatest
                    var failures = 0
                    while (isActive && cache.active) {
                        if (mutable.value.busy) {
                            delay(500)
                            continue
                        }
                        core.recoverOperation(
                            onFailure = { failure ->
                                failures++
                                mutable.update { it.copy(error = failure.message.orEmpty()) }
                            }
                        ) {
                            roomMutex.withLock {
                                roomState.merge(
                                    repository.room(
                                        mutable.value.room.messages.lastOrNull()?.seq ?: 0
                                    )
                                )
                            }
                            failures = 0
                            mutable.update { it.copy(error = "") }
                        }
                        withTimeoutOrNull(
                            core.encounterPollDelay(mutable.value.room.state, failures)
                        ) {
                            pollWake.first()
                        }
                    }
                }
        }
    }

    private fun run(work: suspend () -> Unit) {
        scope.launch {
            if (!cache.active) return@launch
            pendingOperations++
            mutable.update { it.copy(busy = true, error = "") }
            try {
                core.recoverOperation(
                    onFailure = { failure ->
                        mutable.update { it.copy(error = failure.message.orEmpty()) }
                    }
                ) {
                    work()
                }
            } finally {
                pendingOperations--
                mutable.update { it.copy(busy = pendingOperations > 0) }
            }
        }
    }

    fun tab(value: String) {
        profileGeneration++
        mutable.update { it.copy(tab = value, profile = null, error = "") }
        if (value == "me") profile(userId)
        if (value == "daily") daily()
        if (value == "people") search(filters.query, filters.tag, filters.channel)
    }

    fun search(
        query: String = "",
        tag: String = "",
        channel: String = "all",
        append: Boolean = false,
    ) {
        val generation = ++directoryGeneration
        val key = SocialFilters(query, tag, channel)
        val expected = cache.revision
        cache.rememberFilters(key)
        if (!append) {
            val saved = cache.directory(key)
            mutable.update { it.copy(people = saved?.users.orEmpty(), cursor = saved?.nextCursor) }
            if (saved != null) return
        }
        val cursor = directoryCursor(append, mutable.value.cursor) ?: return
        run {
            val result = repository.directory(query, tag, channel, cursor)
            val currentOwner = cache.active && expected == cache.revision
            if (currentOwner && generation == directoryGeneration) {
                val merged =
                    if (append)
                        result.copy(
                            users =
                                (mutable.value.people + result.users).distinctBy(SocialProfile::id)
                        )
                    else result
                cache.putDirectory(key, merged, expected)
                mutable.update { it.copy(people = merged.users, cursor = merged.nextCursor) }
            }
        }
    }

    fun profile(id: String) {
        val generation = ++profileGeneration
        mutable.update { it.copy(profile = null, profileReady = false, blocked = emptyList()) }
        run {
            if (id == userId) {
                val saved = withContext(Dispatchers.IO) { cache.profile() }
                if (cache.active && generation == profileGeneration)
                    mutable.update { it.copy(profile = saved) }
            }
            coroutineScope {
                val profile = async { repository.profile(id) }
                val blocked = async { if (id == userId) repository.blocked() else emptyList() }
                val result = profile.await()
                val blockedUsers = blocked.await()
                if (cache.active && generation == profileGeneration) {
                    mutable.update {
                        it.copy(profile = result, blocked = blockedUsers, profileReady = true)
                    }
                    if (id == userId) withContext(Dispatchers.IO) { cache.saveProfile(result) }
                }
            }
        }
    }

    fun unblock(id: Int) {
        run {
            repository.unblock(id)
            cache.invalidate()
            mutable.update { it.copy(blocked = it.blocked.filterNot { user -> user.id == id }) }
        }
    }

    fun closeProfile() {
        profileGeneration++
        mutable.update { it.copy(profile = null, tab = if (it.tab == "me") "people" else it.tab) }
        if (mutable.value.tab == "people") search(filters.query, filters.tag, filters.channel)
    }

    fun save(edit: SocialEdit) {
        if (!mutable.value.profileReady) return
        run {
            val result = repository.save(edit)
            cache.invalidate()
            if (cache.active) mutable.update { it.copy(profile = result) }
            withContext(Dispatchers.IO) { cache.saveProfile(result) }
        }
    }

    fun friend(reason: String) {
        val id = mutable.value.profile?.id ?: return
        run {
            repository.friend(id, reason)
            cache.invalidate()
            profile(id)
        }
    }

    fun block() {
        val id = mutable.value.profile?.id ?: return
        run {
            repository.block(id)
            cache.invalidate()
            mutable.update {
                it.copy(profile = null, people = it.people.filterNot { p -> p.id == id })
            }
        }
    }

    fun report(reason: String) {
        val id = mutable.value.profile?.id
        val room = if (id == null) mutable.value.room.roomId else null
        run {
            repository.report(id, room, reason)
            mutable.update { it.copy(error = "", profile = null) }
            if (room != null) act("leave")
        }
    }

    fun daily(append: Boolean = false) {
        val generation = ++dailyGeneration
        val expected = cache.revision
        run {
            val saved = if (append) null else cache.daily()
            val result = saved ?: repository.daily(dailyCursor(append))
            val currentGeneration = generation == dailyGeneration && expected == cache.revision
            if (!cache.active || !currentGeneration) return@run
            val merged = mergeDailyPage(mutable.value.daily, result, append)
            if (saved == null) cache.putDaily(merged, expected)
            mutable.update { it.copy(daily = merged) }
            if (draftDay != merged.day) restoreDailyDraft(merged, generation)
        }
    }

    private suspend fun restoreDailyDraft(page: DailyQuestion, generation: Int) {
        val version = draftVersion
        val draft = withContext(Dispatchers.IO) { cache.draft() }
        val currentDraft = generation == dailyGeneration && version == draftVersion
        if (cache.active && currentDraft) {
            draftDay = page.day
            val forDay = draft?.takeIf { it.day == page.day }
            forDay?.let { draftVersion = it.revision }
            mutable.update { it.copy(answerDraft = forDay?.content ?: page.myAnswer) }
        }
    }

    fun editAnswer(content: String) {
        val day = mutable.value.daily?.day ?: return
        draftDay = day
        draftVersion = UUID.randomUUID().toString()
        val draft = core.SocialAnswerDraft(day, content.take(500), draftVersion)
        pendingDraft = draft
        mutable.update { it.copy(answerDraft = draft.content) }
        draftJob?.cancel()
        cache.stageDraft(draft)
        draftJob = scope.launch {
            delay(250)
            try {
                persistDraft(draft)
            } catch (failure: java.io.IOException) {
                mutable.update { it.copy(error = failure.message.orEmpty()) }
            }
        }
    }

    private suspend fun persistDraft(draft: core.SocialAnswerDraft) {
        try {
            withContext(Dispatchers.IO) {
                cache.saveDraft(draft.day, draft.content, draft.revision)
            }
        } catch (failure: java.io.IOException) {
            mutable.update { it.copy(error = failure.message.orEmpty()) }
            return
        }
        if (pendingDraft?.revision == draft.revision) pendingDraft = null
    }

    fun answer(content: String) {
        val day = mutable.value.daily?.day ?: return
        val version = draftVersion
        run {
            draftJob?.cancel()
            pendingDraft?.let { persistDraft(it) }
            repository.answer(day, content)
            cache.invalidate()
            withContext(Dispatchers.IO) { cache.clearSubmittedDraft(version) }
            if (cache.active) daily()
        }
    }

    fun position(key: String) = cache.position(key)

    fun rememberPosition(key: String, index: Int, offset: Int) =
        cache.rememberPosition(key, SocialScrollPosition(index, offset))

    fun act(operation: String, messageId: String? = null, content: String? = null) {
        val expectedRoom = mutable.value.room.roomId
        run {
            roomMutex.withLock {
                roomState.merge(
                    repository.act(EncounterAction(operation, expectedRoom, messageId, content))
                )
                if (operation == "exchange") cache.invalidate()
            }
            pollWake.tryEmit(Unit)
        }
    }

    override fun close() {
        core.update.UpdatePreparation.remove(this)
        val draft = pendingDraft
        scope.cancel()
        if (draft != null) {
            // A captured owner is checked again under the cache lock, even after logout.
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    persistDraft(draft)
                } catch (_: java.io.IOException) {
                    /* Disk unavailable; keep no UI task alive. */
                }
            }
        }
    }
}

private fun directoryCursor(append: Boolean, current: Int?): Int? = if (append) current else 0

private fun mergeDailyPage(
    previous: DailyQuestion?,
    result: DailyQuestion,
    append: Boolean,
): DailyQuestion =
    if (append)
        result.copy(
            answers =
                (previous?.takeIf { it.day == result.day }?.answers.orEmpty() + result.answers)
                    .distinctBy(DailyAnswer::id)
        )
    else result

private fun SocialViewModel.dailyCursor(append: Boolean): String =
    if (append) state.value.daily?.nextCursor.orEmpty() else ""
