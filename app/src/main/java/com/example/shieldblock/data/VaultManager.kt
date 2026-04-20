package com.example.shieldblock.data

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

class VaultManager(private val context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    fun exportConfig(): String {
        val root = JSONObject()
        root.put("version", "1.0")
        root.put("timestamp", System.currentTimeMillis())

        val rules = JSONObject()
        rules.put("custom_rules", JSONArray(prefs.getStringSet("custom_rules", emptySet())?.toList()))
        rules.put("excluded_packages", JSONArray(prefs.getStringSet("excluded_packages", emptySet())?.toList()))
        rules.put("firewall_ips", JSONArray(prefs.getStringSet("firewall_blocked_ips", emptySet())?.toList()))

        val settings = JSONObject()
        settings.put("custom_dns", prefs.getString("custom_dns", "8.8.8.8"))
        settings.put("strict_mode", prefs.getBoolean("strict_mode", false))
        settings.put("smart_filtering", prefs.getBoolean("smart_filtering", false))

        root.put("rules", rules)
        root.put("settings", settings)

        return root.toString(4)
    }

    fun importConfig(jsonStr: String): Boolean {
        return try {
            val root = JSONObject(jsonStr)
            val rules = root.getJSONObject("rules")
            val settings = root.getJSONObject("settings")

            val editor = prefs.edit()

            val cr = rules.getJSONArray("custom_rules")
            val crSet = mutableSetOf<String>()
            for (i in 0 until cr.length()) crSet.add(cr.getString(i))
            editor.putStringSet("custom_rules", crSet)

            val ep = rules.getJSONArray("excluded_packages")
            val epSet = mutableSetOf<String>()
            for (i in 0 until ep.length()) epSet.add(ep.getString(i))
            editor.putStringSet("excluded_packages", epSet)

            val fips = rules.getJSONArray("firewall_ips")
            val fipsSet = mutableSetOf<String>()
            for (i in 0 until fips.length()) fipsSet.add(fips.getString(i))
            editor.putStringSet("firewall_blocked_ips", fipsSet)

            editor.putString("custom_dns", settings.getString("custom_dns"))
            editor.putBoolean("strict_mode", settings.getBoolean("strict_mode"))
            editor.putBoolean("smart_filtering", settings.getBoolean("smart_filtering"))

            editor.apply()
            true
        } catch (e: Exception) {
            false
        }
    }
}
