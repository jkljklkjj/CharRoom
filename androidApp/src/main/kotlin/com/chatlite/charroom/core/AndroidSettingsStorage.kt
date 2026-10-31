package com.chatlite.charroom.core

import android.content.Context
import androidx.core.content.edit
import core.AppSettingsStorage

class AndroidSettingsStorage(context: Context) : AppSettingsStorage {
    private val preferences =
        context.applicationContext.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    override fun readTheme() = preferences.getInt("theme_mode", 0)

    override fun readLanguage() = preferences.getString("language", "follow") ?: "follow"

    override fun writeTheme(value: Int) {
        preferences.edit { putInt("theme_mode", value) }
    }

    override fun writeLanguage(value: String) {
        preferences.edit { putString("language", value) }
    }
}
