package com.example.shieldblock.data

import android.content.Context
import androidx.preference.PreferenceManager

class FirewallManager(private val context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    fun getBlockedIps(): Set<String> {
        return prefs.getStringSet("firewall_blocked_ips", emptySet()) ?: emptySet()
    }

    fun addBlockedIp(ip: String) {
        val current = getBlockedIps().toMutableSet()
        current.add(ip)
        prefs.edit().putStringSet("firewall_blocked_ips", current).apply()
    }

    fun removeBlockedIp(ip: String) {
        val current = getBlockedIps().toMutableSet()
        current.remove(ip)
        prefs.edit().putStringSet("firewall_blocked_ips", current).apply()
    }
}
