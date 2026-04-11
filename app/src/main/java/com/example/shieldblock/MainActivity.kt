package com.example.shieldblock

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.shieldblock.data.StatsManager
import com.example.shieldblock.data.WhitelistManager
import com.example.shieldblock.databinding.ActivityMainBinding
import com.example.shieldblock.vpn.MyVpnService
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val whitelistManager by lazy { WhitelistManager(this) }
    private val statsManager by lazy { StatsManager(this) }
    private val handler = Handler(Looper.getMainLooper())
    private val updateStatsRunnable = object : Runnable {
        override fun run() {
            updateStats()
            handler.postDelayed(this, 5000) // Update every 5 seconds
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply saved theme before inflation
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        applyTheme(prefs.getString("app_theme", "system") ?: "system")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initial UI State
        updateVpnUi(VpnService.prepare(this) == null)
        updateStats()

        // VPN Control Buttons
        binding.startVpnButton.setOnClickListener { startVpn() }
        binding.stopVpnButton.setOnClickListener { stopVpn() }

        // Whitelist Logic
        binding.addWhitelistButton.setOnClickListener {
            val domain = binding.whitelistEditText.text.toString()
            if (domain.isNotBlank()) {
                whitelistManager.addToWhitelist(domain)
                Toast.makeText(this, " added to whitelist", Toast.LENGTH_SHORT).show()
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

    private fun applyTheme(theme: String) {
        when (theme) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateStatsRunnable)
        updateVpnUi(VpnService.prepare(this) == null)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateStatsRunnable)
    }

    private fun updateStats() {
        binding.blockedAdsCountText.text = statsManager.getBlockedAdsCount().toString()
        binding.blockedWebsitesCountText.text = statsManager.getBlockedWebsitesCount().toString()
    }

    private fun startVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, 0)
        } else {
            startService(Intent(this, MyVpnService::class.java).apply {
                putExtra("action", "start")
            })
            updateVpnUi(true)
        }
    }

    private fun stopVpn() {
        startService(Intent(this, MyVpnService::class.java).apply {
            putExtra("action", "stop")
        })
        updateVpnUi(false)
    }

    private fun updateVpnUi(isConnected: Boolean) {
        if (isConnected) {
            binding.statusText.text = getString(R.string.vpn_status_connected)
            binding.statusText.setTextColor(getColor(R.color.primary))
            binding.statusIcon.setColorFilter(getColor(R.color.primary))
            binding.startVpnButton.visibility = View.GONE
            binding.stopVpnButton.visibility = View.VISIBLE
        } else {
            binding.statusText.text = getString(R.string.vpn_status_disconnected)
            binding.statusText.setTextColor(getColor(R.color.tertiary))
            binding.statusIcon.setColorFilter(getColor(R.color.tertiary))
            binding.startVpnButton.visibility = View.VISIBLE
            binding.stopVpnButton.visibility = View.GONE
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            startVpn()
        }
    }
}
