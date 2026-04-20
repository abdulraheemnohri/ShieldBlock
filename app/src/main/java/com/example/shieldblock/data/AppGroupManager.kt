package com.example.shieldblock.data

import android.content.Context
import androidx.preference.PreferenceManager

class AppGroupManager(private val context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    fun createGroup(name: String, packages: Set<String>) {
        prefs.edit().putStringSet("group_$name", packages).apply()
    }

    fun getGroup(name: String): Set<String> {
        return prefs.getStringSet("group_$name", emptySet()) ?: emptySet()
    }

    fun applyGroupExclusion(name: String, filterManager: FilterManager) {
        val packages = getGroup(name)
        packages.forEach { filterManager.addExcludedPackage(it) }
    }
}
