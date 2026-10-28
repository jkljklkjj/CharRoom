package core

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

data class AgentSnapshot(val id:String,val text:String,val terminal:Boolean)

/** Snapshot replacement: A, AB is AB. Only the newest pending snapshot per request is rendered. */
class ConflatedStreamUpdates(scope:CoroutineScope,private val interval:Long=16,apply:suspend(AgentSnapshot)->Unit) {
    private val pending=linkedMapOf<String,AgentSnapshot>()
    private val lock=Any()
    private val completed=linkedSetOf<String>()
    private val wake=Channel<Unit>(Channel.CONFLATED)
    init { scope.launch {
        for(signal in wake) {
            delay(interval)
            val batch=synchronized(lock) { pending.values.toList().also { pending.clear() } }
            batch.forEach { currentCoroutineContext().ensureActive();apply(it) }
        }
    } }
    fun submit(value:AgentSnapshot) {
        synchronized(lock) {
            if(value.id in completed)return
            pending[value.id]=value
            if(value.terminal){completed.add(value.id);while(completed.size>200)completed.remove(completed.first())}
        }
        wake.trySend(Unit)
    }
    fun clear() { synchronized(lock) { pending.clear();completed.clear() } }
}
