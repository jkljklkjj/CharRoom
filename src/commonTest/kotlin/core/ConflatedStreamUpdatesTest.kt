package core

import kotlinx.coroutines.*
import kotlin.test.*

class ConflatedStreamUpdatesTest {
    @Test fun cumulativeTextReplacesPreviousAndTerminalIsNotLost() = runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val result=CompletableDeferred<AgentSnapshot>()
        val updates=ConflatedStreamUpdates(scope,30) { if(it.terminal)result.complete(it) }
        try {
            updates.submit(AgentSnapshot("1","A",false))
            updates.submit(AgentSnapshot("1","AB",false))
            updates.submit(AgentSnapshot("1","AB",true))
            updates.submit(AgentSnapshot("1","late chunk",false))
            assertEquals("AB",withTimeout(2000){result.await()}.text)
        } finally { scope.cancel() }
    }
}
