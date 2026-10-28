package core

import java.nio.file.Files
import kotlin.test.*
import model.*

class SocialLocalCacheTest {
    @Test fun slowDiskWriteDoesNotBlockDraftStagingAndLogoutStillRejectsItsResult() {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        val store = SocialLocalCache(Files.createTempDirectory("slow-social").toFile(), writeFile = { file, value ->
            entered.countDown(); check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
            AtomicHistoryFile.write(file, value)
        })
        val session = store.activate("42")
        try {
            val write = executor.submit { session.saveDraft(session.today(), "old", "v1") }
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS))
            executor.submit { session.stageDraft(SocialAnswerDraft(session.today(), "new", "v2")); store.logout() }
                .get(1, java.util.concurrent.TimeUnit.SECONDS)
            release.countDown(); write.get(2, java.util.concurrent.TimeUnit.SECONDS)
            assertNull(store.activate("42").draft())
        } finally { release.countDown(); executor.shutdownNow() }
    }
    private fun cache(clock: () -> Long = { 1_800_000_000_000L }) =
        SocialLocalCache(Files.createTempDirectory("social-cache-test").toFile(), clock)

    @Test fun pagesExpireWithoutExtendingOnReadAndInvalidationRejectsLateResults() {
        var now = 1_800_000_000_000L
        val cache = cache { now }
        val session = cache.activate("42")
        val revision = session.revision
        session.putDirectory(SocialFilters(), SocialDirectory(listOf(SocialProfile("43", "Friend"))), revision)
        now += 19_000
        assertNotNull(session.directory(SocialFilters()))
        now += 1_001
        assertNull(session.directory(SocialFilters()))
        session.invalidate()
        session.putDirectory(SocialFilters(), SocialDirectory(), revision)
        assertNull(session.directory(SocialFilters()))
    }

    @Test fun accountSwitchAndLogoutRejectOldSessionAndDeleteItsPrivateFiles() {
        val cache = cache()
        val first = cache.activate("42")
        first.saveDraft(first.today(), "old", "v1")
        assertEquals("old", first.draft()?.content)
        cache.activate("43")
        assertFalse(first.active)
        first.saveDraft(first.today(), "late", "v2")
        val next = cache.activate("42")
        cache.logout()
        assertFalse(next.active)
        assertNull(cache.activate("42").draft())
    }

    @Test fun draftSurvivesNewCacheAndSubmittedRevisionCannotEraseNewTyping() {
        val root = Files.createTempDirectory("social-draft-test").toFile()
        val now = { 1_800_000_000_000L }
        val session = SocialLocalCache(root, now).activate("42")
        val day = session.today()
        session.saveDraft(day, "before", "v1")
        session.saveDraft(day, "after", "v2")
        session.clearSubmittedDraft("v1")
        assertEquals("after", SocialLocalCache(root, now).activate("42").draft()?.content)
        session.clearSubmittedDraft("v2")
        assertNull(session.draft())
    }

    @Test fun ownProfileMustMatchOwnerAndExpireAfterOneDay() {
        var now = 1_800_000_000_000L
        val session = cache { now }.activate("42")
        session.saveProfile(SocialProfile("43", "Other", relation = "SELF"))
        assertNull(session.profile())
        session.saveProfile(SocialProfile("42", "Me", relation = "SELF"))
        assertEquals("Me", session.profile()?.username)
        now += 86_400_001
        assertNull(session.profile())
    }

    @Test fun delayedFlushCannotOverwriteNewTypingOrClearItOnSubmit() {
        val session = cache().activate("42")
        val day = session.today()
        session.stageDraft("v1")
        session.saveDraft(day, "submitted", "v1")
        session.stageDraft("v2")
        session.clearSubmittedDraft("v1")
        session.saveDraft(day, "new typing", "v2")
        session.saveDraft(day, "late flush", "v1")
        assertEquals("new typing", session.draft()?.content)
    }

    @Test fun dailyCacheAndDraftExpireAtBeijingMidnight() {
        var now = 1_800_000_000_000L
        val session = cache { now }.activate("42")
        session.putDaily(DailyQuestion(session.today(), "Question"), session.revision)
        session.saveDraft(session.today(), "draft", "v1")
        now += 86_400_000
        assertNull(session.daily())
        assertNull(session.draft())
    }

    @Test fun pagesHaveCountAndSizeLimits() {
        val session = cache().activate("42")
        repeat(9) { index -> session.putDirectory(SocialFilters(query = "$index"), SocialDirectory(), session.revision) }
        assertNull(session.directory(SocialFilters(query = "0")))
        assertNotNull(session.directory(SocialFilters(query = "8")))
        session.putDirectory(SocialFilters(), SocialDirectory(listOf(SocialProfile("43", "x".repeat(250_001)))), session.revision)
        assertNull(session.directory(SocialFilters()))
    }

    @Test fun stagedDraftIsAvailableBeforeDiskFlushAndOldFlushCannotResurrectSubmittedText() {
        val session = cache().activate("42")
        val draft = SocialAnswerDraft(session.today(), "not flushed yet", "v1")
        session.stageDraft(draft)
        assertEquals(draft, session.draft())
        session.clearSubmittedDraft("v1")
        session.saveDraft(draft.day, draft.content, draft.revision)
        assertNull(session.draft())
    }

    @Test fun recreatingScreenForSameAccountKeepsUnflushedDraftButLogoutCreatesNewOwner() {
        val cache = cache()
        val session = cache.activate("42")
        session.stageDraft(SocialAnswerDraft(session.today(), "typing", "v1"))
        assertSame(session, cache.activate("42"))
        assertEquals("typing", cache.activate("42").draft()?.content)
        cache.logout()
        assertNotSame(session, cache.activate("42"))
        assertNull(cache.activate("42").draft())
    }
}
