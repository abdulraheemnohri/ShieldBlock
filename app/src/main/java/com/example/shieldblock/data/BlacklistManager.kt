package com.example.shieldblock.data

import android.content.Context
import androidx.preference.PreferenceManager
import java.io.File

class BlacklistManager(private val context: Context) {
    private val blacklistFile = File(context.filesDir, "blacklist.txt")

    fun loadLocalBlacklist(): List<String> {
        return if (blacklistFile.exists()) {
            blacklistFile.readLines()
        } else {
            emptyList()
        }
    }

    fun updateBlacklist(domains: List<String>) {
        blacklistFile.writeText(domains.joinToString("\n"))
    }
}
