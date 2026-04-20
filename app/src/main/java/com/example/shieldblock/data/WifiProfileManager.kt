package com.example.shieldblock.data

import android.content.Context
import android.net.wifi.WifiManager
import androidx.preference.PreferenceManager

class WifiProfileManager(private val context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun saveProfileForSsid(ssid: String, strictMode: Boolean, smartFiltering: Boolean) {
        prefs.edit().apply {
            putBoolean("wifi_${ssid}_strict", strictMode)
            putBoolean("wifi_${ssid}_smart", smartFiltering)
            putStringSet("profiled_ssids", (prefs.getStringSet("profiled_ssids", emptySet()) ?: emptySet()) + ssid)
        }.apply()
    }

    fun getProfileForCurrentSsid(): Profile? {
        val ssid = getCurrentSsid() ?: return null
        if (!isSsidProfiled(ssid)) return null

        return Profile(
            strictMode = prefs.getBoolean("wifi_${ssid}_strict", false),
            smartFiltering = prefs.getBoolean("wifi_${ssid}_smart", false)
        )
    }

    private fun getCurrentSsid(): String? {
        val info = wifiManager.connectionInfo
        return if (info != null && info.ssid != "<unknown ssid>") info.ssid.removeSurrounding("\"") else null
    }

    private fun isSsidProfiled(ssid: String): Boolean {
        return (prefs.getStringSet("profiled_ssids", emptySet()) ?: emptySet()).contains(ssid)
    }

    data class Profile(val strictMode: Boolean, val smartFiltering: Boolean)
}
