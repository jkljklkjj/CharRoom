package core

import model.MessageIdGenerator
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.test.*

class MessageIdGeneratorTest {
    @Test fun parallelGenerationDoesNotDependOnTimestampOrAccountCounters() {
        val ids = ConcurrentHashMap.newKeySet<String>()
        val workers = List(4) { thread { repeat(2500) {
            ids.add(MessageIdGenerator.generateMessageId(1, "same", 1))
            ids.add(MessageIdGenerator.generateGroupMessageId(2, 1, "same", 1))
        } } }
        workers.forEach { it.join() }
        assertEquals(20000, ids.size)
        ids.forEach { assertEquals(it.removePrefix("local_"), UUID.fromString(it.removePrefix("local_")).toString()) }
    }
}
