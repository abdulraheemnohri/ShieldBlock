package com.example.shieldblock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.shieldblock.vpn.MyVpnService

class NetworkChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)

        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            // Re-trigger VPN to check for excluded SSID
            val vpnIntent = Intent(context, MyVpnService::class.java).apply {
                putExtra("action", "start")
            }
            context.startService(vpnIntent)
        }
    }
}
