package core

import core.model.PublishedAppVersion
import kotlinx.serialization.json.Json
import kotlin.test.*

class PublishedAppVersionTest {
    @Test fun backendResponseProducesTheNativeUpdatePrompt() {
        val response = Json { ignoreUnknownKeys = true }.decodeFromString<PublishedAppVersion>("""
            {"platform":"android","latestVersion":"2.0.0","updateAvailable":true,
             "forceUpdate":true,"packageType":"apk","architecture":"universal","fileSize":1024,"sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","versionCode":2,"downloadUrl":"https://example.com/app.apk","releaseNotes":"Fixes","message":"Update"}
        """.trimIndent())
        val result = response.toCheckResult()
        assertTrue(result.hasUpdate)
        assertEquals("2.0.0", result.latestVersion?.versionName)
        assertEquals("https://example.com/app.apk", result.latestVersion?.downloadUrl)
        assertEquals(true, result.latestVersion?.forceUpdate)
    }
    @Test fun currentVersionDoesNotOfferAnInstaller() {
        val result = PublishedAppVersion("2.0.0", false, false, "https://example.com/app.apk", "Fixes", "apk", "universal", 1024, "a".repeat(64), 2).toCheckResult()
        assertFalse(result.hasUpdate)
        assertNull(result.latestVersion)
    }
}
