package com.example.shieldblock.data

import android.content.Context
import androidx.preference.PreferenceManager

class WhitelistManager(context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val whitelistKey = "whitelist_domains"

    fun getWhitelist(): Set<String> {
        return prefs.getStringSet(whitelistKey, emptySet()) ?: emptySet()
    }

    fun addToWhitelist(domain: String) {
        val current = getWhitelist().toMutableSet()
        current.add(domain)
        prefs.edit().putStringSet(whitelistKey, current).apply()
    }

    fun removeFromWhitelist(domain: String) {
        val current = getWhitelist().toMutableSet()
        current.remove(domain)
        prefs.edit().putStringSet(whitelistKey, current).apply()
    }
}
