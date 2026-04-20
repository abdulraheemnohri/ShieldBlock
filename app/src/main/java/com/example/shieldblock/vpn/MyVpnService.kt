package com.example.shieldblock.vpn

import android.content.*
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.shieldblock.MainActivity
import com.example.shieldblock.R
import com.example.shieldblock.analytics.EventLogger

class MyVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var dnsProxy: DnsProxy? = null
    private var proxyThread: Thread? = null
    private val eventLogger by lazy { EventLogger(this) }

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.NETWORK_STATE_CHANGED_ACTION) {
                dnsProxy?.updateSettings()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        val builder = Builder()
            .setSession("Aegis Ultra Plus")
            .addAddress("10.0.0.2", 24)
            .addDnsServer("10.0.0.2")
            .addRoute("0.0.0.0", 0)
            .setBlocking(true)
            .setConfigureIntent(android.app.PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), android.app.PendingIntent.FLAG_IMMUTABLE))

        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val excludedApps = prefs.getStringSet("excluded_apps", emptySet()) ?: emptySet()
        excludedApps.forEach { packageName ->
            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: Exception) {}
        }

        vpnInterface = builder.establish()

        vpnInterface?.let {
            dnsProxy = DnsProxy(it, this)
            dnsProxy?.updateSettings()
            proxyThread = Thread(dnsProxy, "AegisProxyThread")
            proxyThread?.start()
        }

        registerReceiver(wifiReceiver, IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION))
        eventLogger.logEvent("Aegis Node: Service Online")
    }

    private fun stopVpn() {
        try { unregisterReceiver(wifiReceiver) } catch (e: Exception) {}
        proxyThread?.interrupt()
        vpnInterface?.close()
        vpnInterface = null
        dnsProxy = null
        stopSelf()
        eventLogger.logEvent("Aegis Node: Service Offline")
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
