package com.example.shieldblock.data

import android.content.Context
import java.io.File

class BlacklistManager(private val context: Context) {
    private val blacklistFile = File(context.filesDir, "blacklist.txt")
    private val customBlacklistFile = File(context.filesDir, "blacklist_custom.txt")

    fun loadLocalBlacklist(): List<String> {
        val base = if (blacklistFile.exists()) blacklistFile.readLines() else emptyList()
        val custom = if (customBlacklistFile.exists()) customBlacklistFile.readLines() else emptyList()
        return base + custom
    }

    fun getCustomFilePath(): String = customBlacklistFile.absolutePath

    fun updateBlacklist(domains: List<String>) {
        blacklistFile.writeText(domains.joinToString("\n"))
    }
}
