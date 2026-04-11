package com.example.shieldblock.data

import android.content.Context
import java.io.File

class WhitelistManager(private val context: Context) {
    private val whitelistFile = File(context.filesDir, "whitelist.txt")
    private val manualWhitelistFile = File(context.filesDir, "whitelist_manual.txt")

    fun getWhitelist(): Set<String> {
        val base = if (whitelistFile.exists()) whitelistFile.readLines() else emptyList()
        val manual = if (manualWhitelistFile.exists()) manualWhitelistFile.readLines() else emptyList()
        return (base + manual + listOf("google.com", "android.com", "connectivitycheck.gstatic.com")).toSet()
    }

    fun getManualFilePath(): String = manualWhitelistFile.absolutePath

    fun addToWhitelist(domain: String) {
        val currentManual = if (manualWhitelistFile.exists()) manualWhitelistFile.readLines().toMutableList() else mutableListOf()
        if (!currentManual.contains(domain)) {
            currentManual.add(domain)
            manualWhitelistFile.writeText(currentManual.joinToString("\n"))
        }
    }

    fun removeFromWhitelist(domain: String) {
        if (manualWhitelistFile.exists()) {
            val currentManual = manualWhitelistFile.readLines().filter { it != domain }
            manualWhitelistFile.writeText(currentManual.joinToString("\n"))
        }
    }
}
