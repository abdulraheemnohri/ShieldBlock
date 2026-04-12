package com.example.shieldblock.data

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class FilterSource(
    val id: String,
    val name: String,
    val url: String,
    val type: String = "URL",
    var enabled: Boolean = true,
    val category: String = "General",
    var domainCount: Int = 0,
    val isWhitelist: Boolean = false
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
    private val sourceCountsKey = "source_domain_counts"

    val defaultFilters = listOf(
        FilterSource("ads", "Standard Adblock", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts", category = "Privacy"),
        FilterSource("social", "Social Media Blocker", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/social/hosts", category = "Privacy"),
        FilterSource("fakenews", "Fake News Blocker", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/fakenews/hosts", category = "Security"),
        FilterSource("gambling", "Gambling Blocker", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/gambling/hosts", category = "Content"),
        FilterSource("porn", "Adult Content Blocker", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts", category = "Family"),
        FilterSource("crypto", "Crypto Blocker", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/fakenews-gambling-porn-social/hosts", category = "Security"),
        // Premium additions
        FilterSource("tracking", "Premium Tracking Protection", "https://hostfiles.frogeye.it/firstparty-trackers-hosts.txt", category = "Ultra"),
        FilterSource("telemetry", "Manufacturer Telemetry", "https://raw.githubusercontent.com/crazy-max/WindowsSpyBlocker/master/data/hosts/spy.txt", category = "Ultra"),
        FilterSource("malware", "Active Malware Domains", "https://osint.digitalside.it/Threat-Intel/lists/latestdomains.txt", category = "Security"),
        FilterSource("spam", "Spam & Phishing", "https://raw.githubusercontent.com/PolishFiltersTeam/PolishAnnoyanceFilters/master/pp_annoyance_host.txt", category = "Ultra")
    )

    val dnsProfiles = listOf(
        DnsProfile("Google DNS", "8.8.8.8", "Fast and reliable."),
        DnsProfile("Cloudflare", "1.1.1.1", "Privacy-focused."),
        DnsProfile("AdGuard DNS", "94.140.14.14", "Built-in blocking."),
        DnsProfile("CleanBrowsing", "185.228.168.168", "Family-safe."),
        DnsProfile("Quad9", "9.9.9.9", "Security-focused."),
        DnsProfile("Mullvad DNS", "194.242.2.2", "Ad & Tracker blocking.")
    )

    fun getEnabledFilterIds(): Set<String> {
        return prefs.getStringSet(enabledFiltersKey, setOf("ads", "tracking")) ?: setOf("ads", "tracking")
    }

    fun setFilterEnabled(id: String, enabled: Boolean) {
        val current = getEnabledFilterIds().toMutableSet()
        if (enabled) current.add(id) else current.remove(id)
        prefs.edit().putStringSet(enabledFiltersKey, current).apply()
    }

    fun getAllSources(): List<FilterSource> {
        val counts = getSourceCounts()
        val all = defaultFilters + getCustomSources()
        all.forEach { it.domainCount = counts.optInt(it.id, 0) }
        return all
    }

    private fun getCustomSources(): List<FilterSource> {
        val json = prefs.getString(customSourcesKey, "[]") ?: "[]"
        val array = JSONArray(json)
        val list = mutableListOf<FilterSource>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val id = obj.getString("id")
            list.add(FilterSource(
                id, obj.getString("name"), obj.getString("url"),
                obj.optString("type", "URL"), getEnabledFilterIds().contains(id),
                obj.optString("category", "Custom"),
                isWhitelist = obj.optBoolean("isWhitelist", false)
            ))
        }
        return list
    }

    fun addCustomSource(name: String, url: String, type: String = "URL", category: String = "Custom", isWhitelist: Boolean = false) {
        val sources = getCustomSources().toMutableList()
        val id = "custom_${System.currentTimeMillis()}"
        sources.add(FilterSource(id, name, url, type, true, category, isWhitelist = isWhitelist))
        saveCustomSources(sources)
        setFilterEnabled(id, true)
    }

    fun removeCustomSource(id: String) {
        val sources = getCustomSources().filter { it.id != id }
        saveCustomSources(sources)
        setFilterEnabled(id, false)
    }

    fun resetToDefaults() {
        prefs.edit().remove(customSourcesKey).remove(enabledFiltersKey).remove(sourceCountsKey).apply()
    }

    private fun saveCustomSources(sources: List<FilterSource>) {
        val array = JSONArray()
        sources.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
                .put("name", it.name)
                .put("url", it.url)
                .put("type", it.type)
                .put("category", it.category)
                .put("isWhitelist", it.isWhitelist)
            array.put(obj)
        }
        prefs.edit().putString(customSourcesKey, array.toString()).apply()
    }

    private fun getSourceCounts(): JSONObject {
        return JSONObject(prefs.getString(sourceCountsKey, "{}") ?: "{}")
    }

    fun updateSourceCount(id: String, count: Int) {
        val counts = getSourceCounts()
        counts.put(id, count)
        prefs.edit().putString(sourceCountsKey, counts.toString()).apply()
    }

    fun getCustomRules(): Set<String> {
        return prefs.getStringSet(customRulesKey, emptySet()) ?: emptySet()
    }

    fun addCustomRule(rule: String) {
        val current = getCustomRules().toMutableSet()
        current.add(rule)
        prefs.edit().putStringSet(customRulesKey, current).apply()
    }

    fun applyProfile(profile: String) {
        when(profile) {
            "KIDS" -> {
                prefs.edit()
                    .putBoolean("safe_search", true)
                    .putBoolean("strict_mode", false)
                    .putBoolean("smart_filtering", true)
                    .apply()
                setFilterEnabled("porn", true)
                setFilterEnabled("gambling", true)
            }
            "WORK" -> {
                prefs.edit()
                    .putBoolean("safe_search", false)
                    .putBoolean("strict_mode", false)
                    .putBoolean("smart_filtering", false)
                    .apply()
                setFilterEnabled("social", true)
            }
        }
    }
}
