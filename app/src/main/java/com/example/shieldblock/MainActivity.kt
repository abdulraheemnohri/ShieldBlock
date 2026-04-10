package com.example.shieldblock

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.shieldblock.data.WhitelistManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val whitelistManager by lazy { WhitelistManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // VPN Control Buttons
        startVpnButton.setOnClickListener { startVpn() }
        stopVpnButton.setOnClickListener { stopVpn() }

        // Whitelist Logic
        addWhitelistButton.setOnClickListener {
            val domain = whitelistEditText.text.toString()
            if (domain.isNotBlank()) {
                whitelistManager.addToWhitelist(domain)
                Toast.makeText(this, "$domain added to whitelist", Toast.LENGTH_SHORT).show()
                whitelistEditText.text?.clear()
            }
        }

        // Settings Button
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Setup WorkManager for periodic blacklist updates
        val blacklistWork = PeriodicWorkRequestBuilder<BlacklistWorker>(
            24, TimeUnit.HOURS
        ).build()
        WorkManager.getInstance(this).enqueue(blacklistWork)
    }

    private fun startVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, 0)
        } else {
            startService(Intent(this, MyVpnService::class.java).apply {
                putExtra("action", "start")
            })
            statusText.text = "VPN Status: Connected"
        }
    }

    private fun stopVpn() {
        startService(Intent(this, MyVpnService::class.java).apply {
            putExtra("action", "stop")
        })
        statusText.text = "VPN Status: Disconnected"
    }
}