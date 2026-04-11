package com.example.shieldblock.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.example.shieldblock.MainActivity
import com.example.shieldblock.R
import com.example.shieldblock.data.BlacklistManager
import com.example.shieldblock.data.WhitelistManager
import com.example.shieldblock.data.FilterManager
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class MyVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var dnsProxyJob: Job? = null
    private lateinit var blacklistManager: BlacklistManager
    private lateinit var whitelistManager: WhitelistManager
    private lateinit var filterManager: FilterManager
    private var dnsProxy: DnsProxy? = null

    companion object {
        const val CHANNEL_ID = "ShieldBlockVPNChannel"
        const val NOTIFICATION_ID = 1
        var instance: MyVpnService? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        blacklistManager = BlacklistManager(this)
        whitelistManager = WhitelistManager(this)
        filterManager = FilterManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getStringExtra("action")) {
            "start" -> {
                if (shouldExcludeCurrentNetwork() || !isWithinSchedule()) {
                    stopVpn()
                } else {
                    startVpn()
                }
            }
            "stop" -> stopVpn()
        }
        return START_STICKY
    }

    fun getTrafficStats(): Pair<Long, Long> = dnsProxy?.getTrafficStats() ?: (0L to 0L)

    private fun isWithinSchedule(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean("scheduled_enabled", false)) return true

        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val nowStr = sdf.format(Date())
        val start = prefs.getString("scheduled_start", "00:00") ?: "00:00"
        val end = prefs.getString("scheduled_end", "23:59") ?: "23:59"

        return if (start <= end) {
            nowStr in start..end
        } else {
            nowStr >= start || nowStr <= end
        }
    }

    private fun shouldExcludeCurrentNetwork(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val excludedSsids = prefs.getStringSet("excluded_wifi_ssids", emptySet()) ?: emptySet()
        if (excludedSsids.isEmpty()) return false

        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ssid = wm.connectionInfo?.ssid?.replace("\"", "") ?: ""
        return excludedSsids.contains(ssid)
    }

    private fun startVpn() {
        if (vpnInterface != null) return
        setupVpn()
        if (vpnInterface == null) return

        val proxy = DnsProxy(vpnInterface!!.fileDescriptor, this)
        dnsProxy = proxy
        proxy.updateBlacklist(blacklistManager.loadLocalBlacklist())
        proxy.updateWhitelist(whitelistManager.getWhitelist())
        proxy.updateCustomRules(filterManager.getCustomRules())

        dnsProxyJob = CoroutineScope(Dispatchers.IO).launch {
            proxy.run()
        }
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun setupVpn() {
        val builder = Builder()
        builder.setSession("ShieldBlockVPN")
        builder.addAddress("10.0.0.2", 24)
        builder.addDnsServer("8.8.8.8")
        builder.addRoute("0.0.0.0", 0)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val excludedApps = prefs.getStringSet("excluded_apps", emptySet()) ?: emptySet()
        excludedApps.forEach {
            try {
                builder.addDisallowedApplication(it)
            } catch (e: Exception) {
            }
        }

        vpnInterface = builder.establish()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "ShieldBlock VPN Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, MyVpnService::class.java).apply { putExtra("action", "stop") }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ShieldBlock VPN")
            .setContentText("Your connection is secure and ads are being blocked.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    private fun stopVpn() {
        dnsProxyJob?.cancel()
        vpnInterface?.close()
        vpnInterface = null
        dnsProxy = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        instance = null
        super.onDestroy()
    }
}
