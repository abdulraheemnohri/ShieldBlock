package com.example.shieldblock.data

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class StatsManager(private val context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val blockedAdsKey = "blocked_ads_count"
    private val safeQueriesKey = "safe_queries_count"
    private val topBlockedKey = "top_blocked_domains"
    private val appStatsKey = "app_blocked_stats"
    private val recentBlocksKey = "recent_blocked_feed"
    private val hourlyStatsKey = "hourly_blocked_stats"

    fun getBlockedAdsCount(): Int = prefs.getInt(blockedAdsKey, 0)
    fun getSafeQueriesCount(): Int = prefs.getInt(safeQueriesKey, 0)
    fun getTotalQueries(): Int = getBlockedAdsCount() + getSafeQueriesCount()

    fun incrementBlockedAds() {
        val current = getBlockedAdsCount()
        prefs.edit().putInt(blockedAdsKey, current + 1).apply()
        logHourlyBlock()
    }

    fun incrementSafeQueries() {
        val current = getSafeQueriesCount()
        prefs.edit().putInt(safeQueriesKey, current + 1).apply()
    }

    private fun logHourlyBlock() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val json = prefs.getString(hourlyStatsKey, "{}") ?: "{}"
        val obj = JSONObject(json)
        val count = obj.optInt(hour.toString(), 0)
        obj.put(hour.toString(), count + 1)
        prefs.edit().putString(hourlyStatsKey, obj.toString()).apply()
    }

    fun getHourlyStats(): Map<Int, Int> {
        val json = prefs.getString(hourlyStatsKey, "{}") ?: "{}"
        val obj = JSONObject(json)
        val map = mutableMapOf<Int, Int>()
        for (i in 0..23) {
            map[i] = obj.optInt(i.toString(), 0)
        }
        return map
    }

    fun logBlockedDomain(domain: String) {
        val json = prefs.getString(topBlockedKey, "{}") ?: "{}"
        val obj = JSONObject(json)
        val count = obj.optInt(domain, 0)
        obj.put(domain, count + 1)

        val feedJson = prefs.getString(recentBlocksKey, "[]") ?: "[]"
        val feedArray = JSONArray(feedJson)
        val newFeed = JSONArray()
        newFeed.put(domain)
        for (i in 0 until minOf(feedArray.length(), 4)) {
            newFeed.put(feedArray.get(i))
        }

        prefs.edit()
            .putString(topBlockedKey, obj.toString())
            .putString(recentBlocksKey, newFeed.toString())
            .apply()
    }

    fun getRecentBlocks(): List<String> {
        val json = prefs.getString(recentBlocksKey, "[]") ?: "[]"
        val array = JSONArray(json)
        val list = mutableListOf<String>()
        for (i in 0 until array.length()) {
            list.add(array.getString(i))
        }
        return list
    }

    fun logAppBlocked(packageName: String) {
        val json = prefs.getString(appStatsKey, "{}") ?: "{}"
        val obj = JSONObject(json)
        val count = obj.optInt(packageName, 0)
        obj.put(packageName, count + 1)
        prefs.edit().putString(appStatsKey, obj.toString()).apply()
    }

    fun getAppBlockedCount(packageName: String): Int {
        val json = prefs.getString(appStatsKey, "{}") ?: "{}"
        val obj = JSONObject(json)
        return obj.optInt(packageName, 0)
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
        val kb = getBlockedAdsCount() * 50
        return if (kb > 1024) {
            String.format("%.2f MB", kb / 1024.0)
        } else {
            " KB"
        }
    }

    fun getPrivacyScore(): Int {
        val filterManager = FilterManager(context)
        val enabledCount = filterManager.getEnabledFilterIds().size
        val totalFilters = filterManager.getAllSources().size

        var score = (enabledCount.toFloat() / totalFilters.toFloat() * 100).toInt()
        score = minOf(100, score + 10)
        return maxOf(10, score)
    }

    fun resetStats() {
        prefs.edit()
            .putInt(blockedAdsKey, 0)
            .putInt(safeQueriesKey, 0)
            .putString(topBlockedKey, "{}")
            .putString(appStatsKey, "{}")
            .putString(recentBlocksKey, "[]")
            .putString(hourlyStatsKey, "{}")
            .apply()
    }
}
