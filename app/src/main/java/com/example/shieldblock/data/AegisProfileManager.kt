package com.example.shieldblock.data

import android.content.Context
import androidx.preference.PreferenceManager

data class AegisProfile(
    val id: String,
    val name: String,
    val strictMode: Boolean,
    val smartFilter: Boolean,
    val dataSaver: Boolean,
    val doh: Boolean,
    val stealth: Boolean,
    val color: Int
)

class AegisProfileManager(private val context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    val profiles = listOf(
        AegisProfile("balanced", "BALANCED", false, true, false, false, false, 0xFF86FEA7.toInt()),
        AegisProfile("stealth", "STEALTH", false, true, true, true, true, 0xFFBB86FC.toInt()),
        AegisProfile("fortress", "FORTRESS", true, true, true, true, true, 0xFFFF5555.toInt())
    )

    fun getActiveProfileId(): String = prefs.getString("active_profile", "balanced") ?: "balanced"

    fun applyProfile(id: String) {
        val profile = profiles.find { it.id == id } ?: return
        prefs.edit().apply {
            putString("active_profile", id)
            putBoolean("strict_mode", profile.strictMode)
            putBoolean("smart_filtering", profile.smartFilter)
            putBoolean("data_saver", profile.dataSaver)
            putBoolean("dns_over_https", profile.doh)
            putBoolean("stealth_mode", profile.stealth)
            apply()
        }
    }
}
