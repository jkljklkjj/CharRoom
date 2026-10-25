package core

import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsStorageTest {
    private class MemoryStorage(var theme: Int = 0, var locale: String = "follow") : AppSettingsStorage {
        override fun readTheme() = theme
        override fun readLanguage() = locale
        override fun writeTheme(value: Int) { theme = value }
        override fun writeLanguage(value: String) { locale = value }
    }
    @Test fun platformPreferencesRestoreAndPersistTheActualUiChoice() {
        val storage = MemoryStorage(2, "ja")
        AppSettings.installStorage(storage)
        assertEquals(ThemeMode.DARK, AppSettings.themeMode.value)
        assertEquals("ja", AppSettings.language.value)
        AppSettings.setThemeMode(ThemeMode.LIGHT)
        AppSettings.setLanguage("en")
        assertEquals(1, storage.theme)
        assertEquals("en", storage.locale)
    }
    @Test fun unknownPersistedThemeFallsBackToSystem() {
        AppSettings.installStorage(MemoryStorage(99))
        assertEquals(ThemeMode.SYSTEM, AppSettings.themeMode.value)
    }
}
