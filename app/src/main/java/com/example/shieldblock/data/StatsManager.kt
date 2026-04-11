package com.example.shieldblock.data

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class StatsManager(private val context: Context) {
    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(context) }
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
        prefs.edit().putInt(blockedAdsKey, getBlockedAdsCount() + 1).apply()
        logHourlyBlock()
    }

    fun incrementSafeQueries() {
        prefs.edit().putInt(safeQueriesKey, getSafeQueriesCount() + 1).apply()
    }

    private fun logHourlyBlock() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY).toString()
        val obj = try { JSONObject(prefs.getString(hourlyStatsKey, "{}") ?: "{}") } catch(e: Exception) { JSONObject() }
        obj.put(hour, obj.optInt(hour, 0) + 1)
        prefs.edit().putString(hourlyStatsKey, obj.toString()).apply()
    }

    fun getHourlyStats(): Map<Int, Int> {
        val obj = try { JSONObject(prefs.getString(hourlyStatsKey, "{}") ?: "{}") } catch(e: Exception) { JSONObject() }
        val map = mutableMapOf<Int, Int>()
        for (i in 0..23) {
            map[i] = obj.optInt(i.toString(), 0)
        }
        return map
    }

    fun logBlockedDomain(domain: String) {
        val obj = try { JSONObject(prefs.getString(topBlockedKey, "{}") ?: "{}") } catch(e: Exception) { JSONObject() }
        obj.put(domain, obj.optInt(domain, 0) + 1)

        val feedArray = try { JSONArray(prefs.getString(recentBlocksKey, "[]") ?: "[]") } catch(e: Exception) { JSONArray() }
        val newFeed = JSONArray()
        newFeed.put(domain)
        for (i in 0 until minOf(feedArray.length(), 9)) {
            newFeed.put(feedArray.get(i))
        }

        prefs.edit()
            .putString(topBlockedKey, obj.toString())
            .putString(recentBlocksKey, newFeed.toString())
            .apply()
    }

    fun getRecentBlocks(): List<String> {
        val array = try { JSONArray(prefs.getString(recentBlocksKey, "[]") ?: "[]") } catch(e: Exception) { JSONArray() }
        val list = mutableListOf<String>()
        for (i in 0 until array.length()) {
            list.add(array.optString(i, "unknown"))
        }
        return list
    }

    fun logAppBlocked(packageName: String) {
        val obj = try { JSONObject(prefs.getString(appStatsKey, "{}") ?: "{}") } catch(e: Exception) { JSONObject() }
        obj.put(packageName, obj.optInt(packageName, 0) + 1)
        prefs.edit().putString(appStatsKey, obj.toString()).apply()
    }

    fun getAppBlockedCount(packageName: String): Int {
        val obj = try { JSONObject(prefs.getString(appStatsKey, "{}") ?: "{}") } catch(e: Exception) { JSONObject() }
        return obj.optInt(packageName, 0)
    }

    fun getTopBlockedDomains(limit: Int = 5): List<Pair<String, Int>> {
        val obj = try { JSONObject(prefs.getString(topBlockedKey, "{}") ?: "{}") } catch(e: Exception) { JSONObject() }
        val list = mutableListOf<Pair<String, Int>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            list.add(key to obj.optInt(key, 0))
        }
        return list.sortedByDescending { it.second }.take(limit)
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
