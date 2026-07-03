package com.example.taskmanager.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object TokenStore {
    private const val PREFS_NAME = "auth_store"
    private const val KEY_TOKEN = "token"
    private const val KEY_EMAIL = "email"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getToken(context: Context): String? = prefs(context).getString(KEY_TOKEN, null)

    fun getEmail(context: Context): String? = prefs(context).getString(KEY_EMAIL, null)

    fun save(context: Context, token: String, email: String) {
        prefs(context).edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_EMAIL, email)
            .apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
