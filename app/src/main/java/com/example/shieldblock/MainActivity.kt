package com.example.shieldblock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.appcompat.app.AppCompatActivity
import com.example.shieldblock.databinding.ActivityMainBinding
import com.example.shieldblock.vpn.MyVpnService

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var isVpnActive = false

    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val domain = intent?.getStringExtra("domain") ?: ""
            val action = intent?.getStringExtra("action") ?: ""
            val app = intent?.getStringExtra("appName") ?: "System"
            binding.threatTicker.text = "MITIGATED: $domain from $app"
            binding.threatTicker.isSelected = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.aegisCore.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            toggleVpn()
        }

        binding.firewallFab.setOnClickListener {
            startActivity(Intent(this, FirewallActivity::class.java))
        }

        setupBottomNavigation()
    }

    private fun toggleVpn() {
        if (!isVpnActive) {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                startActivityForResult(intent, 0)
            } else {
                onActivityResult(0, RESULT_OK, null)
            }
        } else {
            stopService(Intent(this, MyVpnService::class.java))
            isVpnActive = false
            updateUiState()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            startService(Intent(this, MyVpnService::class.java))
            isVpnActive = true
            updateUiState()
        }
    }

    private fun updateUiState() {
        if (isVpnActive) {
            binding.statusHud.text = "PROTOCOL: SECURE (AEGIS-ULTRA)\nSENTINEL: ACTIVE"
            binding.aegisCore.setGlowColor(0x86FEA7)
        } else {
            binding.statusHud.text = "PROTOCOL: STANDBY\nSYSTEM: UNPROTECTED"
            binding.aegisCore.setGlowColor(0xFF5252)
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_home
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_analytics -> { startActivity(Intent(this, AnalyticsActivity::class.java)); finish(); true }
                R.id.nav_apps -> { startActivity(Intent(this, AppExclusionActivity::class.java)); finish(); true }
                R.id.nav_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); finish(); true }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(eventReceiver, IntentFilter("com.example.shieldblock.PACKET_EVENT"))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(eventReceiver)
    }
}
