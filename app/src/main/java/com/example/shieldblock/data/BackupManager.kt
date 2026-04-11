package com.example.shieldblock.data

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class BackupManager(private val context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val whitelistManager = WhitelistManager(context)
    private val blacklistManager = BlacklistManager(context)

    fun createBackup(): String {
        val backup = JSONObject()

        // 1. Settings (All Preferences)
        val settings = JSONObject()
        prefs.all.forEach { (key, value) ->
            settings.put(key, value)
        }
        backup.put("settings", settings)

        // 2. Manual Files
        val whitelistFile = File(whitelistManager.getManualFilePath())
        if (whitelistFile.exists()) {
            backup.put("whitelist_manual", whitelistFile.readText())
        }

        val blacklistFile = File(blacklistManager.getCustomFilePath())
        if (blacklistFile.exists()) {
            backup.put("blacklist_custom", blacklistFile.readText())
        }

        return backup.toString(4)
    }

    fun restoreBackup(jsonStr: String): Boolean {
        return try {
            val backup = JSONObject(jsonStr)

            // 1. Restore Settings
            val settings = backup.optJSONObject("settings")
            if (settings != null) {
                val editor = prefs.edit()
                val keys = settings.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = settings.get(key)
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is String -> editor.putString(key, value)
                    }
                }
                editor.apply()
            }

            // 2. Restore Manual Files
            if (backup.has("whitelist_manual")) {
                File(whitelistManager.getManualFilePath()).writeText(backup.getString("whitelist_manual"))
            }

            if (backup.has("blacklist_custom")) {
                File(blacklistManager.getCustomFilePath()).writeText(backup.getString("blacklist_custom"))
            }

            true
        } catch (e: Exception) {
            false
        }
    }
}
