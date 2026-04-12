package com.example.shieldblock

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.shieldblock.data.BlacklistManager
import com.example.shieldblock.data.FilterManager
import com.example.shieldblock.data.WhitelistManager
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
        val whitelistManager = WhitelistManager(applicationContext)
        val filterManager = FilterManager(applicationContext)
        val client = OkHttpClient()

        val enabledIds = filterManager.getEnabledFilterIds()
        val allSources = filterManager.getAllSources()
        val sourcesToProcess = allSources.filter { enabledIds.contains(it.id) }

        val allHosts = mutableSetOf<String>()
        val allWhites = mutableSetOf<String>()

        for (source in sourcesToProcess) {
            try {
                val hosts = if (source.type == "LOCAL") {
                    val file = File(source.url)
                    if (file.exists()) file.readLines() else emptyList()
                } else {
                    val request = Request.Builder().url(source.url).build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        response.body?.string()?.lines() ?: emptyList()
                    } else emptyList()
                }

                val parsed = hosts.map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .map { line ->
                        line.replace("0.0.0.0 ", "")
                            .replace("127.0.0.1 ", "")
                            .split(" ")[0]
                            .split("#")[0]
                            .trim()
                    }
                    .filter { it.isNotEmpty() && it != "localhost" && it.contains(".") }

                filterManager.updateSourceCount(source.id, parsed.size)
                if (source.isWhitelist) {
                    allWhites.addAll(parsed)
                } else {
                    allHosts.addAll(parsed)
                }
            } catch (e: Exception) {
                Log.e("BlacklistWorker", "Failed to process ${source.name}: ${e.message}")
            }
        }

        if (allHosts.isNotEmpty() || allWhites.isNotEmpty()) {
            if (allHosts.isNotEmpty()) blacklistManager.updateBlacklist(allHosts.toList())
            if (allWhites.isNotEmpty()) whitelistManager.updateWhitelistFromHosts(allWhites.toList())

            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            val timestamp = sdf.format(Date())
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
                .edit().putString("last_blacklist_update", timestamp).apply()

            showCompletionNotification(allHosts.size, allWhites.size)
            Result.success()
        } else {
            Result.retry()
        }
    }

    private fun showCompletionNotification(hostCount: Int, whiteCount: Int) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "ShieldBlockUpdates"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(channelId, "ShieldBlock Updates", android.app.NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }

        val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("ShieldBlock Updated")
            .setContentText("Synced $hostCount blocked and $whiteCount allowed domains.")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify(2, notification)
    }
}
