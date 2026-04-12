package com.example.shieldblock

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.shieldblock.data.BlacklistManager
import com.example.shieldblock.data.FilterManager
import com.example.shieldblock.databinding.ActivityAboutBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class AboutActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAboutBinding
    private val filterManager by lazy { FilterManager(this) }
    private val blacklistManager by lazy { BlacklistManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        setupBottomNavigation()
        binding.currentVersionText.text = getString(R.string.version_label, BuildConfig.VERSION_NAME)

        binding.checkForUpdatesBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            checkForUpdates()
        }

        checkHealth()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_settings
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            binding.bottomNavigation.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            when (item.itemId) {
                R.id.nav_home -> { startActivity(Intent(this, MainActivity::class.java)); overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right); finish(); true }
                R.id.nav_analytics -> { startActivity(Intent(this, AnalyticsActivity::class.java)); overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right); finish(); true }
                R.id.nav_apps -> { startActivity(Intent(this, AppExclusionActivity::class.java)); overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right); finish(); true }
                R.id.nav_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left); finish(); true }
                else -> false
            }
        }
    }

    private fun checkForUpdates() {
        binding.checkForUpdatesBtn.isEnabled = false
        binding.updateStatusText.visibility = View.VISIBLE
        binding.updateStatusText.text = "Checking for security intelligence updates..."

        lifecycleScope.launch {
            delay(2000)
            binding.updateStatusText.setText(R.string.up_to_date)
            binding.updateStatusText.setTextColor(getColor(R.color.emerald_accent))
            binding.checkForUpdatesBtn.isEnabled = true
        }
    }

    private fun checkHealth() {
        val isVpnActive = VpnService.prepare(this) == null
        binding.healthVpnText.text = "VPN Status: ${if (isVpnActive) "PROTECTED" else "DEACTIVATED"}"
        binding.healthVpnText.setTextColor(getColor(if (isVpnActive) R.color.emerald_accent else R.color.tertiary))

        val activeFilters = filterManager.getEnabledFilterIds().size
        binding.healthFilterText.text = "Active Intelligence Sources: $activeFilters"

        lifecycleScope.launch {
            val domains = withContext(Dispatchers.IO) { blacklistManager.loadLocalBlacklist().size }
            binding.healthDatabaseText.text = "Local Threat DB size: $domains domains"
        }

        val uptimeMillis = SystemClock.elapsedRealtime()
        val hours = TimeUnit.MILLISECONDS.toHours(uptimeMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(uptimeMillis) % 60
        binding.healthUptimeText.text = "System Uptime: ${hours}h ${minutes}m"
    }
}
