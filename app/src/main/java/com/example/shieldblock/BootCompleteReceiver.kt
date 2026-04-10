package com.example.shieldblock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager

class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val autoStartEnabled = prefs.getBoolean("auto_start", false)
            if (autoStartEnabled) {
                val vpnIntent = Intent(context, MyVpnService::class.java).apply {
                    putExtra("action", "start")
                }
                context.startForegroundService(vpnIntent)
            }
        }
    }
}