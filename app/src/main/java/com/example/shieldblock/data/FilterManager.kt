package com.example.shieldblock.data

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

data class FilterSource(
    val id: String,
    val name: String,
    val url: String,
    var enabled: Boolean = true
)

class FilterManager(context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val enabledFiltersKey = "enabled_filters"
    private val customRulesKey = "custom_blocking_rules"
    private val customSourcesKey = "custom_filter_sources"

    val defaultFilters = listOf(
        FilterSource("ads", "Standard Adblock", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"),
        FilterSource("social", "Social Media Blocker", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/social/hosts"),
        FilterSource("fakenews", "Fake News Blocker", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/fakenews/hosts")
    )

    fun getEnabledFilterIds(): Set<String> {
        return prefs.getStringSet(enabledFiltersKey, defaultFilters.map { it.id }.toSet()) ?: emptySet()
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
                true // We'll manage enable/disable via enabledFiltersKey
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
