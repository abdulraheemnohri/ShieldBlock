package com.example.shieldblock

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.shieldblock.data.BlacklistManager
import com.example.shieldblock.data.FilterManager
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BlacklistWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val blacklistManager = BlacklistManager(applicationContext)
        val filterManager = FilterManager(applicationContext)
        val client = OkHttpClient()

        val enabledIds = filterManager.getEnabledFilterIds()
        val sourcesToFetch = filterManager.defaultFilters.filter { enabledIds.contains(it.id) }

        val allHosts = mutableSetOf<String>()

        for (source in sourcesToFetch) {
            try {
                val request = Request.Builder().url(source.url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val hosts = response.body?.string()?.lines()
                        ?.filter { it.startsWith("0.0.0.0 ") || it.startsWith("127.0.0.1 ") }
                        ?.map { line ->
                            line.replace("0.0.0.0 ", "")
                                .replace("127.0.0.1 ", "")
                                .trim()
                        }
                        ?.filter { it.isNotEmpty() && it != "localhost" }
                        ?: emptyList()
                    allHosts.addAll(hosts)
                }
            } catch (e: Exception) {
                Log.e("BlacklistWorker", "Failed to fetch ${source.name}: ${e.message}")
            }
        }

        if (allHosts.isNotEmpty()) {
            blacklistManager.updateBlacklist(allHosts.toList())
            Result.success()
        } else {
            Result.retry()
        }
    }
}
