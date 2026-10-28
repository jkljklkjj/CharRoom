package core

import core.model.AppVersionInfo
import core.model.VersionCheckResult
import core.update.*
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.test.*

class UpdatePipelineTest {
    private val bytes = "signed-installer-fixture".toByteArray()
    private fun info(force: Boolean = false) = AppVersionInfo("2.0.0", "Fixes", "https://example.com/app.msi", "msi", "x64",
        bytes.size.toLong(), MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, 0, force)
    private class Response(private val status: Int, private val body: ByteArray, private val headers: Map<String, String>) : HttpURLConnection(URI("https://example.com/app.msi").toURL()) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getHeaderField(name: String) = headers[name]
        override fun getInputStream() = ByteArrayInputStream(body)
    }
    private suspend fun httpFixture(action: suspend (UpdateJournal, AppVersionInfo) -> Unit) {
        val directory = Files.createTempDirectory("update-http-test").toFile()
        try { action(UpdateJournal(directory), info()) } finally { directory.deleteRecursively() }
    }
    @Test fun serverIgnoringRangeRestartsInsteadOfAppendingDuplicateBytes() = runBlocking {
        httpFixture { journal, info ->
            File(journal.artifact(info).path + ".part").writeBytes(bytes.take(4).toByteArray())
            lateinit var response: Response
            val engine = HttpUpdateDownload(journal) { Response(200, bytes, mapOf("Content-Length" to "${bytes.size}")).also { response = it } }
            assertContentEquals(bytes, engine.download(info) { _, _ -> }.readBytes())
            assertEquals("bytes=4-", response.getRequestProperty("Range"))
        }
    }
    @Test fun validPartialResponseResumesAtTheExactByteOffset() = runBlocking {
        httpFixture { journal, info ->
            File(journal.artifact(info).path + ".part").writeBytes(bytes.take(4).toByteArray())
            val engine = HttpUpdateDownload(journal) { Response(206, bytes.drop(4).toByteArray(), mapOf("Content-Range" to "bytes 4-${bytes.size-1}/${bytes.size}", "Content-Length" to "${bytes.size-4}")) }
            assertContentEquals(bytes, engine.download(info) { _, _ -> }.readBytes())
        }
    }
    @Test fun errorPagesAndInvalidRangesNeverBecomeInstallableFiles() = runBlocking {
        httpFixture { journal, info ->
            val error = HttpUpdateDownload(journal) { Response(500, "error".toByteArray(), emptyMap()) }
            assertFailsWith<IllegalArgumentException> { error.download(info) { _, _ -> } }
            assertFalse(journal.artifact(info).exists())
            File(journal.artifact(info).path + ".part").writeBytes(bytes.take(4).toByteArray())
            val wrong = HttpUpdateDownload(journal) { Response(206, bytes, mapOf("Content-Range" to "bytes 0-22/23")) }
            assertFailsWith<IllegalArgumentException> { wrong.download(info) { _, _ -> } }
            assertFalse(journal.artifact(info).exists())
        }
    }
    @Test fun corruptOrTruncatedDownloadsAreRejectedAndCorruptPartialsAreRemoved() = runBlocking {
        httpFixture { journal, info ->
            val truncated = HttpUpdateDownload(journal) { Response(200, bytes.take(4).toByteArray(), emptyMap()) }
            assertFailsWith<IllegalArgumentException> { truncated.download(info) { _, _ -> } }
            assertFalse(journal.artifact(info).exists())
            val corrupt = HttpUpdateDownload(journal) { Response(200, ByteArray(bytes.size), mapOf("Content-Length" to "${bytes.size}")) }
            assertFailsWith<IllegalArgumentException> { corrupt.download(info) { _, _ -> } }
            assertFalse(File(journal.artifact(info).path + ".part").exists())
        }
    }
    @Test fun redirectsCannotDowngradeToUnencryptedDownloads() = runBlocking {
        httpFixture { journal, info ->
            val engine = HttpUpdateDownload(journal) { Response(302, byteArrayOf(), mapOf("Location" to "http://example.com/app.msi")) }
            assertFailsWith<IllegalArgumentException> { engine.download(info) { _, _ -> } }
            assertFalse(journal.artifact(info).exists())
        }
    }
    @Test fun corruptCachedInstallerCanBeReplacedOnRetry() = runBlocking {
        httpFixture { journal, info ->
            journal.artifact(info).writeText("corrupt")
            val engine = HttpUpdateDownload(journal) { Response(200, bytes, mapOf("Content-Length" to "${bytes.size}")) }
            assertContentEquals(bytes, engine.download(info) { _, _ -> }.readBytes())
        }
    }
    @Test fun resumeUsesStrongEtagAndRejectsAChangedArtifact() = runBlocking {
        httpFixture { journal, info ->
            val partial = File(journal.artifact(info).path + ".part")
            partial.writeBytes(bytes.take(4).toByteArray()); File(partial.path + ".etag").writeText("\"first\"")
            lateinit var response: Response
            val engine = HttpUpdateDownload(journal) {
                Response(206, bytes.drop(4).toByteArray(), mapOf("ETag" to "\"changed\"", "Content-Range" to "bytes 4-${bytes.size-1}/${bytes.size}")).also { response = it }
            }
            assertFailsWith<IllegalStateException> { engine.download(info) { _, _ -> } }
            assertEquals("\"first\"", response.getRequestProperty("If-Range"))
            assertFalse(partial.exists()); assertFalse(journal.artifact(info).exists())
        }
    }
    @Test fun platformArchitectureAndVersionChecksAreExplicit() {
        assertFailsWith<IllegalArgumentException> { UpdateTarget("android", "arm64").validate(info()) }
        assertFailsWith<IllegalArgumentException> { UpdateTarget("windows", "arm64").validate(info()) }
        assertFailsWith<IllegalArgumentException> { UpdateTarget("windows", "x64").validate(info().copy(sha256 = "")) }
        assertTrue(versionAtLeast("2.0", "2.0.0")); assertFalse(versionAtLeast("1.9", "2.0"))
    }
    @Test fun androidRequiresThePublishedVersionSameApplicationAndSameSigningIdentity() {
        val installed = ApkIdentity("com.chatlite", "1.0.0", 1, setOf("certificate"))
        val archive = ApkIdentity("com.chatlite", "2.0.0", 2, setOf("certificate"))
        val expected = info().copy(packageType = "apk", architecture = "universal", versionCode = 2)
        validateAndroidUpgrade(installed, archive, expected)
        for (invalid in listOf(archive.copy(packageName = "other"), archive.copy(code = 1), archive.copy(code = 3), archive.copy(version = "3.0.0"), archive.copy(signers = setOf("other")), archive.copy(signers = emptySet()))) {
            assertFailsWith<IllegalArgumentException> { validateAndroidUpgrade(installed, invalid, expected) }
        }
    }
    private class Installer : UpdateInstaller {
        var handoffs = 0
        override suspend fun validate(file: File, info: AppVersionInfo) {}
        override suspend fun handoff(file: File, info: AppVersionInfo): Boolean { handoffs++; return true }
    }
    private suspend fun managedFixture(prepare: suspend () -> Unit = {}, version: String = "1.0.0", action: suspend (ManagedAppUpdater, UpdateJournal, Installer, () -> Int) -> Unit) {
        val directory = Files.createTempDirectory("update-state-test").toFile()
        val journal = UpdateJournal(directory); val installer = Installer(); var exits = 0
        val engine = object : UpdateDownload {
            override suspend fun download(info: AppVersionInfo, progress: (Int, Long) -> Unit): File = journal.artifact(info).also { it.writeBytes(bytes) }
            override fun cancel() {}
            override fun close() {}
        }
        val updater = ManagedAppUpdater(UpdateTarget("windows", "x64"), journal, engine, installer,
            checker = { _, _ -> VersionCheckResult(true, info()) }, prepare = prepare, exit = { exits++ }, installedVersion = version)
        try { action(updater, journal, installer) { exits } } finally { updater.close(); directory.deleteRecursively() }
    }
    private suspend inline fun <reified T : UpdateState> await(updater: ManagedAppUpdater): T = withTimeout(5000) { updater.updateState.first { it is T } as T }
    @Test fun failedMessageSaveStopsBeforeInstallerHandoffAndExit() = runBlocking {
        managedFixture(prepare = { error("disk unavailable") }) { updater, _, installer, exits ->
            updater.downloadUpdate(info()); val ready = await<UpdateState.Downloaded>(updater)
            updater.installUpdate(ready.filePath); await<UpdateState.Failed>(updater)
            assertEquals(0, installer.handoffs); assertEquals(0, exits())
        }
    }
    @Test fun tamperingAfterDownloadIsDetectedAgainBeforeInstallation() = runBlocking {
        managedFixture { updater, _, installer, exits ->
            updater.downloadUpdate(info()); val ready = await<UpdateState.Downloaded>(updater)
            File(ready.filePath).writeBytes(ByteArray(bytes.size))
            updater.installUpdate(ready.filePath); await<UpdateState.Failed>(updater)
            assertEquals(0, installer.handoffs); assertEquals(0, exits())
        }
    }
    @Test fun verifiedInstallSavesFirstAndOnlyReportsHandoffUntilNextStartup() = runBlocking {
        var saved = false
        managedFixture(prepare = { saved = true }) { updater, journal, installer, exits ->
            updater.downloadUpdate(info()); val ready = await<UpdateState.Downloaded>(updater)
            updater.installUpdate(ready.filePath); await<UpdateState.HandedOff>(updater)
            withTimeout(5000) { while(exits()==0) delay(5) }
            assertTrue(saved); assertEquals(1, installer.handoffs)
            assertEquals("INSTALLING", journal.read()?.phase)
        }
        managedFixture(version = "2.0.0") { updater, journal, _, _ ->
            journal.write(UpdatePlan(info(), "INSTALLING"))
            updater.restore(); assertIs<UpdateState.Completed>(updater.updateState.value)
            assertNull(journal.read())
        }
    }
    @Test fun restartRestoresReadyInstallerWithoutClaimingInstallationSucceeded() = runBlocking {
        managedFixture { updater, journal, _, _ ->
            journal.artifact(info()).writeBytes(bytes); journal.write(UpdatePlan(info(), "INSTALLING"))
            updater.restore(); assertIs<UpdateState.Downloaded>(updater.updateState.value)
        }
    }
    @Test fun forceUpdateCannotBeDismissedAndStillRequiresAnInstallClick() = runBlocking {
        managedFixture { updater, _, installer, exits ->
            updater.downloadUpdate(info(true)); await<UpdateState.Downloaded>(updater)
            updater.dismiss(); updater.cancelDownload()
            assertTrue(updater.promptVisible.value); assertEquals(0, installer.handoffs); assertEquals(0, exits())
        }
    }
    @Test fun installerFailureReceiptIsDisplayedOnNextStartup() = runBlocking {
        managedFixture { updater, journal, _, _ ->
            journal.artifact(info()).writeBytes(bytes); journal.write(UpdatePlan(info(), "INSTALLING"))
            File(journal.directory, "receipt.json").writeText("{\"exitCode\":1602}")
            updater.restore(); assertIs<UpdateState.Failed>(updater.updateState.value)
        }
    }
    @Test fun restartResumesInterruptedPlanAndInvalidMetadataCanBeReplaced() = runBlocking {
        managedFixture { updater, journal, _, _ ->
            journal.write(UpdatePlan(info(), "DOWNLOADING"))
            updater.restore(); assertIs<UpdateState.Downloaded>(updater.updateState.value)
        }
        managedFixture { updater, journal, _, _ ->
            File(journal.directory, "update.json").writeText("{broken")
            updater.restore(); assertIs<UpdateState.Failed>(updater.updateState.value)
            assertNull(journal.read())
            updater.downloadUpdate(info()); await<UpdateState.Downloaded>(updater)
        }
    }
    @Test fun cancelledDownloadCannotPublishLateProgressOrCompletion() = runBlocking {
        val directory = Files.createTempDirectory("update-cancel").toFile()
        val journal = UpdateJournal(directory); val entered = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val engine = object : UpdateDownload {
            override suspend fun download(info: AppVersionInfo, progress: (Int, Long) -> Unit): File = withContext(NonCancellable) {
                entered.complete(Unit); finish.await(); progress(100, info.fileSize)
                journal.artifact(info).also { it.writeBytes(bytes) }
            }
            override fun cancel() { finish.complete(Unit) }
            override fun close() {}
        }
        val updater = ManagedAppUpdater(UpdateTarget("windows", "x64"), journal, engine, Installer(), { _, _ -> null })
        try {
            updater.downloadUpdate(info()); withTimeout(5000) { entered.await() }
            updater.cancelDownload(); await<UpdateState.Idle>(updater)
            assertFalse(updater.promptVisible.value); assertNull(journal.read()); assertFalse(journal.artifact(info()).exists())
        } finally { updater.close(); directory.deleteRecursively() }
    }
    @Test fun draftsAreAccountAndConversationIsolatedAndEmptyDraftsStayDeleted() {
        val directory = Files.createTempDirectory("update-drafts").toFile()
        try {
            val store = ComposerDraftStore(directory)
            val privateChat = model.ConversationKey.privateChat(7); val group = model.ConversationKey.group(7)
            store.stage(42, privateChat, "private"); store.stage(42, group, "group"); store.stage(43, privateChat, "other"); store.flush()
            val reopened = ComposerDraftStore(directory)
            assertEquals("private", reopened.read(42, privateChat)); assertEquals("group", reopened.read(42, group)); assertEquals("other", reopened.read(43, privateChat))
            store.stage(42, privateChat, ""); store.flush()
            assertEquals("", reopened.read(42, privateChat))
        } finally { directory.deleteRecursively() }
    }
}
