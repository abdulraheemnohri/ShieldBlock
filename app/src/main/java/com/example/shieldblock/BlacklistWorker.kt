package com.example.shieldblock

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.shieldblock.data.BlacklistManager
import com.example.shieldblock.data.FilterManager
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class BlacklistWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val blacklistManager = BlacklistManager(applicationContext)
        val filterManager = FilterManager(applicationContext)
        val client = OkHttpClient()

        val enabledIds = filterManager.getEnabledFilterIds()
        val allSources = filterManager.getAllSources()
        val sourcesToProcess = allSources.filter { enabledIds.contains(it.id) }

        val allHosts = mutableSetOf<String>()

        for (source in sourcesToProcess) {
            try {
                val hosts = if (source.type == "LOCAL") {
                    File(source.url).readLines()
                } else {
                    val request = Request.Builder().url(source.url).build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        response.body?.string()?.lines() ?: emptyList()
                    } else emptyList()
                }

                val parsed = hosts.filter { it.startsWith("0.0.0.0 ") || it.startsWith("127.0.0.1 ") }
                    .map { line ->
                        line.replace("0.0.0.0 ", "")
                            .replace("127.0.0.1 ", "")
                            .trim()
                    }
                    .filter { it.isNotEmpty() && it != "localhost" }

                filterManager.updateSourceCount(source.id, parsed.size)
                allHosts.addAll(parsed)
            } catch (e: Exception) {
                Log.e("BlacklistWorker", "Failed to process ${source.name}: ${e.message}")
            }
        }

        if (allHosts.isNotEmpty()) {
            blacklistManager.updateBlacklist(allHosts.toList())

            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            val timestamp = sdf.format(Date())
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
                .edit().putString("last_blacklist_update", timestamp).apply()

            Result.success()
        } else {
            Result.retry()
        }
    }
}
