package core

import kotlinx.coroutines.*
import kotlin.test.*

class WeightedLoadCacheTest {
    @Test fun concurrentReadersShareLoadAndOneCancelledReaderDoesNotCancelOthers() = runBlocking {
        val cache = WeightedLoadCache<String>(10) { it.length.toLong() }
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val first = async { cache.load("a") { calls++; gate.await(); "hello" } }
        yield()
        val second = async { cache.load("a") { error("duplicate load") } }
        yield(); first.cancel(); gate.complete(Unit)
        assertEquals("hello", second.await()); assertEquals(1,calls)
        assertEquals("hello", cache.get("a"))
    }
    @Test fun byteBudgetEvictsLeastRecentAndClearRejectsLateLoads() = runBlocking {
        val cache = WeightedLoadCache<String>(6) { it.length.toLong() }
        cache.load("a") { "aaa" };cache.load("b") { "bbb" };cache.get("a")
        cache.load("c") { "ccc" };assertNull(cache.get("b"));assertEquals("aaa",cache.get("a"))
        val entered = CompletableDeferred<Unit>();val release = CompletableDeferred<Unit>()
        val pending = async { cache.load("old") { withContext(NonCancellable) { entered.complete(Unit);release.await(); "old" } } }
        entered.await();cache.clear();release.complete(Unit)
        runCatching { pending.await() };assertNull(cache.get("old"));assertNull(cache.get("a"))
        assertEquals("new",cache.load("old") { "new" })
    }
    @Test fun avatarKeysIncludeObjectIdentityAndDecodeSize() {
        assertEquals(imageMemoryKey("url1","object",40),imageMemoryKey("url2","object",50))
        assertNotEquals(imageMemoryKey("url","object",40),imageMemoryKey("url","object",100))
        assertNotEquals(imageMemoryKey("url",null,40),imageMemoryKey("url",null,null))
    }
}
