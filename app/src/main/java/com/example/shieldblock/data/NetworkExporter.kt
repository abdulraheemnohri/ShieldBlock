package com.example.shieldblock.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NetworkExporter(private val context: Context) {
    fun exportToCsv(lines: List<String>): Uri? {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "Aegis_Audit_$timestamp.csv"
            val file = File(context.cacheDir, fileName)

            FileOutputStream(file).use { out ->
                out.write("LogEntry\n".toByteArray())
                lines.forEach { line ->
                    out.write("$line\n".toByteArray())
                }
            }

            return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            return null
        }
    }
}
