package com.chatlite.charroom

import android.content.Context
import androidx.core.content.edit

object AndroidTokenStorage {
    private const val PREFS_NAME = "charroom_prefs"
    private const val KEY_TOKEN = "charroom_token"
    private const val KEY_REFRESH_TOKEN = "charroom_refresh_token"
    private const val KEY_ACCOUNT_ID = "charroom_account_id"

    data class StoredAuth(
        val token: String,
        val refreshToken: String,
        val accountId: Int
    )

    fun save(context: Context, token: String, accountId: Int, refreshToken: String = "") {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putInt(KEY_ACCOUNT_ID, accountId)
            .apply()
    }

    fun load(context: Context): StoredAuth? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_TOKEN, null)
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, "") ?: ""
        val accountId = prefs.getInt(KEY_ACCOUNT_ID, 0)
        return if (!token.isNullOrBlank() && accountId != 0) {
            StoredAuth(token = token, refreshToken = refreshToken, accountId = accountId)
        } else {
            null
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                remove(KEY_TOKEN)
                    .remove(KEY_REFRESH_TOKEN)
                    .remove(KEY_ACCOUNT_ID)
            }
    }
}
