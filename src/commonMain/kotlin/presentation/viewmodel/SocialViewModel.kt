package presentation.viewmodel

import data.repository.SocialRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import model.*

data class SocialUiState(val tab: String = "people", val people: List<SocialProfile> = emptyList(), val cursor: Int? = null, val profile: SocialProfile? = null, val daily: DailyQuestion? = null, val room: EncounterRoom = EncounterRoom(), val blocked: List<SocialBlockedUser> = emptyList(), val busy: Boolean = false, val error: String = "")

class SocialViewModel(private val repository: SocialRepository, private val userId: String) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(SocialUiState())
    val state = mutable.asStateFlow()
    private val roomMutex = Mutex()
    private var profileGeneration = 0
    private var directoryGeneration = 0
    init {
        search()
        scope.launch {
            while (isActive) {
                if (mutable.value.tab == "encounter") {
                    try { roomMutex.withLock { mergeRoom(repository.room(mutable.value.room.messages.lastOrNull()?.seq ?: 0)) } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { mutable.update { it.copy(error = failure.message.orEmpty()) } }
                }
                delay(1500)
            }
        }
    }
    private fun run(work: suspend () -> Unit) { scope.launch {
        mutable.update { it.copy(busy = true,error = "") }
        try { work() } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { mutable.update { it.copy(error = failure.message.orEmpty()) } }
        finally { mutable.update { it.copy(busy = false) } }
    } }
    fun tab(value: String) {
        profileGeneration++; mutable.update { it.copy(tab = value,profile = null,error = "") }
        if (value == "me") profile(userId)
        if (value == "daily") daily()
    }
    fun search(query: String = "", tag: String = "", channel: String = "all", append: Boolean = false) {
        val generation=++directoryGeneration
        run { val result=repository.directory(query,tag,channel,if(append) mutable.value.cursor ?: 0 else 0); if(generation==directoryGeneration) mutable.update { it.copy(people=if(append) (it.people+result.users).distinctBy(SocialProfile::id) else result.users,cursor=result.nextCursor) } }
    }
    fun profile(id: String) { val generation=++profileGeneration; mutable.update { it.copy(profile=null) }; run { val result=repository.profile(id);val blocked=if(id==userId)repository.blocked() else emptyList();if(generation==profileGeneration)mutable.update { it.copy(profile=result,blocked=blocked) } } }
    fun unblock(id: Int) { run { repository.unblock(id);mutable.update { it.copy(blocked=it.blocked.filterNot { user->user.id==id }) } } }
    fun closeProfile() { profileGeneration++;mutable.update { it.copy(profile=null,tab=if(it.tab=="me")"people" else it.tab) } }
    fun save(edit: SocialEdit) { run { val result=repository.save(edit);mutable.update { it.copy(profile=result) } } }
    fun friend(reason: String) { val id=mutable.value.profile?.id ?: return;run { repository.friend(id,reason);profile(id) } }
    fun block() { val id=mutable.value.profile?.id ?: return;run { repository.block(id);mutable.update { it.copy(profile=null,people=it.people.filterNot { p->p.id==id }) } } }
    fun report(reason: String) { val id=mutable.value.profile?.id;val room=if(id==null)mutable.value.room.roomId else null;run { repository.report(id,room,reason);mutable.update { it.copy(error="",profile=null) };if(room!=null)act("leave") } }
    fun daily(append: Boolean = false) { run { val result=repository.daily(if(append) mutable.value.daily?.nextCursor.orEmpty() else "");mutable.update { it.copy(daily=if(append) result.copy(answers=(it.daily?.answers.orEmpty()+result.answers).distinctBy(DailyAnswer::id)) else result) } } }
    fun answer(content: String) { val day=mutable.value.daily?.day ?: return;run { repository.answer(day,content);val result=repository.daily();mutable.update { it.copy(daily=result) } } }
    fun act(operation: String, messageId: String? = null, content: String? = null) {
        val expectedRoom=mutable.value.room.roomId
        run { roomMutex.withLock { mergeRoom(repository.act(EncounterAction(operation,expectedRoom,messageId,content))) } }
    }
    private fun mergeRoom(room: EncounterRoom) {
        mutable.update { old-> val messages=if(room.roomId==old.room.roomId && room.reason!="BLOCKED") (old.room.messages+room.messages).distinctBy(EncounterMessage::seq).sortedBy(EncounterMessage::seq) else room.messages;old.copy(room=room.copy(messages=messages)) }
    }
    override fun close() { scope.cancel() }
}
