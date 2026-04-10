package com.example.shieldblock.analytics

import android.content.Context
import android.util.Log
import com.example.shieldblock.R
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class EventLogger(private val context: Context) {
    private val logFileName = "shieldblock_logs.txt"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun logEvent(event: String) {
        val timestamp = dateFormat.format(Date())
        val logMessage = "[$timestamp] $event\n"

        Log.d("ShieldBlock", logMessage.trim())

        try {
            val logFile = File(context.filesDir, logFileName)
            FileWriter(logFile, true).use { writer ->
                writer.append(logMessage)
            }
        } catch (e: Exception) {
            Log.e("ShieldBlock", "Failed to write log: ${e.message}")
        }
    }

    fun getLogs(): String {
        return try {
            val logFile = File(context.filesDir, logFileName)
            if (logFile.exists()) {
                logFile.readText()
            } else {
                context.getString(R.string.no_logs_found)
            }
        } catch (e: Exception) {
            Log.e("ShieldBlock", "Failed to read logs: ${e.message}")
            context.getString(R.string.error_reading_logs)
        }
    }

    fun clearLogs() {
        try {
            val logFile = File(context.filesDir, logFileName)
            if (logFile.exists()) {
                logFile.delete()
            }
        } catch (e: Exception) {
            Log.e("ShieldBlock", "Failed to clear logs: ${e.message}")
        }
    }
}