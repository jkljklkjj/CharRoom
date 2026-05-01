package com.chatlite.charroom

import android.content.Context

object AndroidTokenStorage {
    private const val PREFS_NAME = "charroom_prefs"
    private const val KEY_TOKEN = "charroom_token"
    private const val KEY_ACCOUNT_ID = "charroom_account_id"

    fun save(context: Context, token: String, accountId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .putInt(KEY_ACCOUNT_ID, accountId)
            .apply()
    }

    fun load(context: Context): Pair<String, Int>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_TOKEN, null)
        val accountId = prefs.getInt(KEY_ACCOUNT_ID, 0)
        return if (!token.isNullOrBlank() && accountId != 0) {
            token to accountId
        } else {
            null
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_TOKEN)
            .remove(KEY_ACCOUNT_ID)
            .apply()
    }
}
