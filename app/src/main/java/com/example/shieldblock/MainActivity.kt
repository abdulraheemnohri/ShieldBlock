package com.example.shieldblock

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.shieldblock.data.WhitelistManager
import com.example.shieldblock.databinding.ActivityMainBinding
import com.example.shieldblock.vpn.MyVpnService
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val whitelistManager by lazy { WhitelistManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // VPN Control Buttons
        binding.startVpnButton.setOnClickListener { startVpn() }
        binding.stopVpnButton.setOnClickListener { stopVpn() }

        // Whitelist Logic
        binding.addWhitelistButton.setOnClickListener {
            val domain = binding.whitelistEditText.text.toString()
            if (domain.isNotBlank()) {
                whitelistManager.addToWhitelist(domain)
                Toast.makeText(this, "$domain added to whitelist", Toast.LENGTH_SHORT).show()
                binding.whitelistEditText.text?.clear()
            }
        }

        // Settings Button
        binding.settingsButton.setOnClickListener {
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
            binding.statusText.text = "VPN Status: Connected"
        }
    }

    private fun stopVpn() {
        startService(Intent(this, MyVpnService::class.java).apply {
            putExtra("action", "stop")
        })
        binding.statusText.text = "VPN Status: Disconnected"
    }
}
