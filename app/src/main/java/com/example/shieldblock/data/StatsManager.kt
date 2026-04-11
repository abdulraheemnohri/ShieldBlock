package com.example.shieldblock.data

import android.content.Context
import androidx.preference.PreferenceManager

class StatsManager(context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val blockedAdsKey = "blocked_ads_count"
    private val blockedWebsitesKey = "blocked_websites_count"

    fun getBlockedAdsCount(): Int = prefs.getInt(blockedAdsKey, 0)
    fun getBlockedWebsitesCount(): Int = prefs.getInt(blockedWebsitesKey, 0)

    fun incrementBlockedAds() {
        val current = getBlockedAdsCount()
        prefs.edit().putInt(blockedAdsKey, current + 1).apply()
    }

    fun incrementBlockedWebsites() {
        val current = getBlockedWebsitesCount()
        prefs.edit().putInt(blockedWebsitesKey, current + 1).apply()
    }

    fun resetStats() {
        prefs.edit()
            .putInt(blockedAdsKey, 0)
            .putInt(blockedWebsitesKey, 0)
            .apply()
    }
}
