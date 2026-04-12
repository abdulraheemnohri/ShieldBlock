package com.example.shieldblock.data

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

data class AppGroup(
    val name: String,
    val packages: Set<String>
)

class AppGroupManager(private val context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val groupsKey = "custom_app_groups"

    fun getGroups(): List<AppGroup> {
        val json = prefs.getString(groupsKey, "[]") ?: "[]"
        val array = JSONArray(json)
        val list = mutableListOf<AppGroup>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val name = obj.getString("name")
            val pkgs = obj.getJSONArray("packages")
            val pkgSet = mutableSetOf<String>()
            for (j in 0 until pkgs.length()) pkgSet.add(pkgs.getString(j))
            list.add(AppGroup(name, pkgSet))
        }
        return list
    }

    fun saveGroup(name: String, packages: Set<String>) {
        val current = getGroups().toMutableList()
        current.removeAll { it.name == name }
        current.add(AppGroup(name, packages))
        saveAll(current)
    }

    private fun saveAll(groups: List<AppGroup>) {
        val array = JSONArray()
        groups.forEach {
            val obj = JSONObject()
            obj.put("name", it.name)
            val pkgs = JSONArray()
            it.packages.forEach { p -> pkgs.put(p) }
            obj.put("packages", pkgs)
            array.put(obj)
        }
        prefs.edit().putString(groupsKey, array.toString()).apply()
    }
}
