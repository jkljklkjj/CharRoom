package core

import com.chatlite.i18n.updateCurrentStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.prefs.Preferences

/**
 * Theme configuration: follows system preference, or explicit light/dark.
 */
enum class ThemeMode(val value: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2);

    companion object {
        fun fromValue(v: Int): ThemeMode =
            entries.firstOrNull { it.value == v } ?: SYSTEM
    }
}

/**
 * Persistent user preferences (theme, language) backed by Java Preferences.
 * Exposes reactive [StateFlow]s for Compose integration.
 */
interface AppSettingsStorage {
    fun readTheme(): Int
    fun readLanguage(): String
    fun writeTheme(value: Int)
    fun writeLanguage(value: String)
}

object AppSettings {
    // Android installs SharedPreferences before UI creation; never initialize JVM preferences there.
    private val prefs: Preferences? by lazy { try {
        Preferences.userNodeForPackage(AppSettings::class.java)
    } catch (e: Exception) {
        null
    } }
    private var storage: AppSettingsStorage? = null
    private var initialized = false

    fun installStorage(value: AppSettingsStorage) {
        storage = value
        initialized = false
        restore()
        updateCurrentStrings(_language.value)
    }

    private fun restore() {
        if (initialized) return
        initialized = true
        _themeMode.value = try { ThemeMode.fromValue(storage?.readTheme() ?: prefs?.getInt("theme_mode", 0) ?: 0) } catch (_: Exception) { ThemeMode.SYSTEM }
        _language.value = try { storage?.readLanguage() ?: prefs?.get("language", "follow") ?: "follow" } catch (_: Exception) { "follow" }
    }

    // ── Theme ──

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> get() { restore(); return _themeMode.asStateFlow() }

    fun setThemeMode(mode: ThemeMode) {
        restore()
        _themeMode.value = mode
        try {
            storage?.writeTheme(mode.value) ?: run { prefs?.putInt("theme_mode", mode.value); prefs?.flush() }
        } catch (_: Exception) {}
    }

    /**
     * Resolve the effective dark/light boolean for a given (or current) [ThemeMode].
     * SYSTEM mode attempts OS-level detection; defaults to light on failure.
     */
    fun isDarkMode(mode: ThemeMode = themeMode.value): Boolean {
        return when (mode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> detectSystemDarkMode()
        }
    }

    private fun detectSystemDarkMode(): Boolean {
        return try {
            val os = System.getProperty("os.name").lowercase()
            when {
                os.contains("mac") || os.contains("os x") -> {
                    val proc = ProcessBuilder(
                        "defaults", "read", "-g", "AppleInterfaceStyle"
                    ).redirectErrorStream(true).start()
                    proc.inputStream.bufferedReader().readText().trim()
                        .equals("dark", ignoreCase = true)
                }
                os.contains("nix") || os.contains("nux") || os.contains("aix") -> {
                    val proc = ProcessBuilder(
                        "gsettings", "get", "org.gnome.desktop.interface", "color-scheme"
                    ).redirectErrorStream(true).start()
                    proc.inputStream.bufferedReader().readText().trim()
                        .contains("dark", ignoreCase = true)
                }
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    // ── Language ──

    private val _language = MutableStateFlow("follow")
    val language: StateFlow<String> get() { restore(); return _language.asStateFlow() }

    fun setLanguage(lang: String) {
        restore()
        _language.value = lang
        try {
            storage?.writeLanguage(lang) ?: run { prefs?.put("language", lang); prefs?.flush() }
        } catch (_: Exception) {}
        // Update the global strings singleton used outside composable scope
        updateCurrentStrings(lang)
    }

    fun getEffectiveLanguage(): String {
        restore()
        val lang = _language.value
        if (lang == "follow") {
            val defaultLocale = java.util.Locale.getDefault()
            return when (defaultLocale.language) {
                "ja" -> "ja"
                "zh" -> "zh"
                else -> "en"
            }
        }
        return lang
    }
}
