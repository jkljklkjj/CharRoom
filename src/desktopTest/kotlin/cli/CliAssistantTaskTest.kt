package cli

import kotlinx.coroutines.*
import kotlin.test.*

class CliAssistantTaskTest {
    @Test fun cancellationDropsLateRepliesAndDoesNotBlockNewCommands() = runBlocking {
        val oldStarted = CompletableDeferred<Unit>()
        val oldReply = CompletableDeferred<String>()
        val answers = mutableListOf<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val task = CliAssistantTask(scope, { text ->
                if (text == "old") { oldStarted.complete(Unit); withContext(NonCancellable) { oldReply.await() } }
                else "new answer"
            }, answers::add)
            assertTrue(task.start("old")); oldStarted.await()
            assertFalse(task.start("duplicate"))
            assertTrue(task.cancel())
            assertTrue(task.start("new"))
            oldReply.complete("old answer")
            yield()
            assertEquals(listOf("new answer"), answers)
            assertFalse(task.isRunning())
        } finally { scope.cancel() }
    }
}
