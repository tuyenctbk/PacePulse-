package com.example.ui

import android.content.Context
import android.content.SharedPreferences

enum class SuggestionType { NONE, RATE, SHARE, UPDATE }

class SuggestionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("suggestion_prefs", Context.MODE_PRIVATE)

    fun shouldShowRate(sessionCount: Int): Boolean {
        if (prefs.getBoolean("rated", false)) return false
        if (sessionCount < 5) return false
        val lastShown = prefs.getLong("last_rate_shown", 0)
        if (System.currentTimeMillis() - lastShown < 7 * 24 * 60 * 60 * 1000L) return false
        return true
    }

    fun markRateShown() {
        prefs.edit().putLong("last_rate_shown", System.currentTimeMillis()).apply()
    }

    fun markRated() {
        prefs.edit().putBoolean("rated", true).apply()
    }

    fun shouldShowShare(sessionCount: Int): Boolean {
        if (sessionCount < 10) return false
        val lastShown = prefs.getLong("last_share_shown", 0)
        if (System.currentTimeMillis() - lastShown < 30 * 24 * 60 * 60 * 1000L) return false
        return true
    }

    fun markShareShown() {
        prefs.edit().putLong("last_share_shown", System.currentTimeMillis()).apply()
    }
}
