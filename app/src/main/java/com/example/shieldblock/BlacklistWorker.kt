package com.example.shieldblock

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.shieldblock.data.BlacklistManager
import okhttp3.OkHttpClient
import okhttp3.Request

class BlacklistWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val blacklistManager = BlacklistManager(applicationContext)
        val client = OkHttpClient()

        // Example blacklist source
        val request = Request.Builder()
            .url("https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts")
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val hosts = response.body?.string()?.lines()
                    ?.filter { it.startsWith("0.0.0.0 ") }
                    ?.map { it.substringAfter("0.0.0.0 ").trim() }
                    ?: emptyList()

                blacklistManager.updateBlacklist(hosts)
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
