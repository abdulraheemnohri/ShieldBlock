package com.example.shieldblock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import com.example.shieldblock.vpn.MyVpnService

class NetworkChangeReceiver : BroadcastReceiver() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onReceive(context: Context, intent: Intent?) {
        // Debounce network changes to avoid rapid restarts
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return@postDelayed
            val caps = cm.getNetworkCapabilities(network) ?: return@postDelayed

            val isOnline = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (isOnline) {
                // Check if VPN is running and needs reload or restart for new network
                if (MyVpnService.instance != null) {
                    val vpnIntent = Intent(context, MyVpnService::class.java).apply {
                        putExtra("action", "reload")
                    }
                    context.startService(vpnIntent)
                }
            }
        }, 2000) // 2 second stabilization window
    }
}
