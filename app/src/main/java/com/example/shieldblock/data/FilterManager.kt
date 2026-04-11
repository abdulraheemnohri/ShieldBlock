package com.example.shieldblock.data

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

data class FilterSource(
    val id: String,
    val name: String,
    val url: String,
    var enabled: Boolean = true,
    val category: String = "General"
)

data class DnsProfile(
    val name: String,
    val primaryDns: String,
    val description: String
)

class FilterManager(private val context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val enabledFiltersKey = "enabled_filters"
    private val customRulesKey = "custom_blocking_rules"
    private val customSourcesKey = "custom_filter_sources"

    val defaultFilters = listOf(
        FilterSource("ads", "Standard Adblock", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts", category = "Privacy"),
        FilterSource("social", "Social Media Blocker", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/social/hosts", category = "Privacy"),
        FilterSource("fakenews", "Fake News Blocker", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/fakenews/hosts", category = "Security"),
        FilterSource("gambling", "Gambling Blocker", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/gambling/hosts", category = "Content"),
        FilterSource("porn", "Adult Content Blocker", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts", category = "Family"),
        FilterSource("crypto", "Crypto/Mining Blocker", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/fakenews-gambling-porn-social/hosts", category = "Security"),
        // Regional
        FilterSource("eu", "EU Regional Filters", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/social/hosts", category = "Regional"),
        FilterSource("asia", "Asia Regional Filters", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/fakenews/hosts", category = "Regional")
    )

    val dnsProfiles = listOf(
        DnsProfile("Google DNS", "8.8.8.8", "Fast and reliable, global coverage."),
        DnsProfile("Cloudflare", "1.1.1.1", "Privacy-focused, very fast."),
        DnsProfile("AdGuard DNS", "94.140.14.14", "Built-in ad and tracker blocking."),
        DnsProfile("CleanBrowsing", "185.228.168.168", "Family-safe, blocks adult content."),
        DnsProfile("Quad9", "9.9.9.9", "Security-focused, blocks malicious sites.")
    )

    fun getEnabledFilterIds(): Set<String> {
        return prefs.getStringSet(enabledFiltersKey, setOf("ads")) ?: setOf("ads")
    }

    fun setFilterEnabled(id: String, enabled: Boolean) {
        val current = getEnabledFilterIds().toMutableSet()
        if (enabled) current.add(id) else current.remove(id)
        prefs.edit().putStringSet(enabledFiltersKey, current).apply()
    }

    fun getCustomSources(): List<FilterSource> {
        val json = prefs.getString(customSourcesKey, "[]") ?: "[]"
        val array = JSONArray(json)
        val list = mutableListOf<FilterSource>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(FilterSource(
                obj.getString("id"),
                obj.getString("name"),
                obj.getString("url"),
                category = "Custom"
            ))
        }
        return list
    }

    fun addCustomSource(name: String, url: String) {
        val sources = getCustomSources().toMutableList()
        val id = "custom_${System.currentTimeMillis()}"
        sources.add(FilterSource(id, name, url))
        saveCustomSources(sources)
        setFilterEnabled(id, true)
    }

    fun removeCustomSource(id: String) {
        val sources = getCustomSources().filter { it.id != id }
        saveCustomSources(sources)
        setFilterEnabled(id, false)
    }

    private fun saveCustomSources(sources: List<FilterSource>) {
        val array = JSONArray()
        sources.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("name", it.name)
            obj.put("url", it.url)
            array.put(obj)
        }
        prefs.edit().putString(customSourcesKey, array.toString()).apply()
    }

    fun getAllSources(): List<FilterSource> {
        return defaultFilters + getCustomSources()
    }

    fun getCustomRules(): Set<String> {
        return prefs.getStringSet(customRulesKey, emptySet()) ?: emptySet()
    }

    fun addCustomRule(rule: String) {
        val current = getCustomRules().toMutableSet()
        current.add(rule)
        prefs.edit().putStringSet(customRulesKey, current).apply()
    }

    fun removeCustomRule(rule: String) {
        val current = getCustomRules().toMutableSet()
        current.remove(rule)
        prefs.edit().putStringSet(customRulesKey, current).apply()
    }
}
