package com.chatlite.i18n

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Internationalization (i18n) infrastructure for the ChatLite Compose Desktop client.
 *
 * Usage:
 *   val strings = LocalStrings.current
 *   Text(text = strings["login.title"])
 *
 * The Strings class wraps a Map<String, String>. If a key is missing, the key itself
 * is returned as a fallback, which makes missing keys visible during development.
 *
 * To switch locale at runtime, wrap the root composable with
 * CompositionLocalProvider(LocalStrings provides enStrings) { ... }
 */
class Strings(private val map: Map<String, String>) {
    operator fun get(key: String): String = map[key] ?: key
}

val LocalStrings = staticCompositionLocalOf { Strings(mapOf()) }

/**
 * Resolve a [Strings] instance for a given locale code.
 */
fun stringsForLocale(locale: String): Strings = when (locale) {
    "zh" -> zhStrings
    "en" -> enStrings
    "ja" -> jaStrings
    else -> enStrings
}

/**
 * Update the global [currentStrings] singleton to reflect the selected locale.
 * Call this whenever the user changes language so non-composable code picks it up.
 */
fun updateCurrentStrings(locale: String) {
    val effective = if (locale == "follow") java.util.Locale.getDefault().language else locale
    currentStrings = stringsForLocale(effective)
}

/** Global active strings singleton for use outside composable scope (ViewModels, repositories). */
var currentStrings: Strings = run {
    val defaultLocale = java.util.Locale.getDefault()
    when (defaultLocale.language) {
        "ja" -> jaStrings
        "zh" -> zhStrings
        else -> enStrings
    }
}
