package com.example.taskmanager.auth

import android.content.Context

// Defaults to the Android emulator's alias for the host machine's localhost,
// so the bundled backend is reachable out of the box during development.
// Real usage requires pointing this at wherever /backend is actually hosted.
object ServerConfig {
    private const val PREFS_NAME = "server_config"
    private const val KEY_BASE_URL = "base_url"
    const val DEFAULT_BASE_URL = "http://10.0.2.2:3000/api/"

    fun getBaseUrl(context: Context): String {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        return if (stored.endsWith("/")) stored else "$stored/"
    }

    fun setBaseUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BASE_URL, url.trim())
            .apply()
    }
}
