package com.example.shieldblock

import android.content.BroadcastReceiver
import com.example.shieldblock.vpn.MyVpnService
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager

class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val autoStartEnabled = prefs.getBoolean("auto_start", false)
            if (autoStartEnabled) {
                startVpn(context)
            }
        }
    }

    private fun startVpn(context: Context) {
        val vpnIntent = Intent(context, MyVpnService::class.java).apply {
            putExtra("action", "start")
        }
        context.startService(vpnIntent)
    }
}
