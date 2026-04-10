package com.example.shieldblock.data

import android.content.Context
import androidx.preference.PreferenceManager

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
