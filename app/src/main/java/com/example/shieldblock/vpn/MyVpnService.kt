package com.example.shieldblock.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.shieldblock.MainActivity
import com.example.shieldblock.R
import com.example.shieldblock.data.BlacklistManager
import com.example.shieldblock.data.WhitelistManager
import kotlinx.coroutines.*

class MyVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var dnsProxyJob: Job? = null
    private lateinit var blacklistManager: BlacklistManager
    private lateinit var whitelistManager: WhitelistManager
    private lateinit var dnsProxy: DnsProxy

    companion object {
        const val CHANNEL_ID = "ShieldBlockVPNChannel"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        blacklistManager = BlacklistManager(this)
        whitelistManager = WhitelistManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getStringExtra("action")) {
            "start" -> startVpn()
            "stop" -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        setupVpn()
        dnsProxy = DnsProxy(vpnInterface!!.fileDescriptor, this)
        dnsProxy.updateBlacklist(blacklistManager.loadLocalBlacklist())
        dnsProxy.updateWhitelist(whitelistManager.getWhitelist())
        dnsProxyJob = CoroutineScope(Dispatchers.IO).launch {
            dnsProxy.run()
        }
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun setupVpn() {
        val builder = Builder()
        builder.setSession("ShieldBlockVPN")
        builder.addAddress("10.0.0.2", 24)
        builder.addDnsServer("8.8.8.8")
        builder.addRoute("0.0.0.0", 0)
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
            .build()
    }

    private fun stopVpn() {
        dnsProxyJob?.cancel()
        vpnInterface?.close()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}