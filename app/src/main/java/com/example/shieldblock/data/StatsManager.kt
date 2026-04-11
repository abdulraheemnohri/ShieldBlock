package com.example.shieldblock.data

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONObject

class StatsManager(context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val blockedAdsKey = "blocked_ads_count"
    private val blockedWebsitesKey = "blocked_websites_count"
    private val topBlockedKey = "top_blocked_domains"

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

    fun logBlockedDomain(domain: String) {
        val json = prefs.getString(topBlockedKey, "{}") ?: "{}"
        val obj = JSONObject(json)
        val count = obj.optInt(domain, 0)
        obj.put(domain, count + 1)
        prefs.edit().putString(topBlockedKey, obj.toString()).apply()
    }

    fun getTopBlockedDomains(limit: Int = 5): List<Pair<String, Int>> {
        val json = prefs.getString(topBlockedKey, "{}") ?: "{}"
        val obj = JSONObject(json)
        val list = mutableListOf<Pair<String, Int>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            list.add(key to obj.getInt(key))
        }
        return list.sortedByDescending { it.second }.take(limit)
    }

    fun getDataSavedEstimates(): String {
        // Average ad size is roughly 50KB
        val kb = getBlockedAdsCount() * 50
        return if (kb > 1024) {
            String.format("%.2f MB", kb / 1024.0)
        } else {
            " KB"
        }
    }

    fun resetStats() {
        prefs.edit()
            .putInt(blockedAdsKey, 0)
            .putInt(blockedWebsitesKey, 0)
            .putString(topBlockedKey, "{}")
            .apply()
    }
}
