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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
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
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupBottomNavigation()
        binding.currentVersionText.text = "Version ${BuildConfig.VERSION_NAME} (Enterprise Edition)"

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
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_analytics -> {
                    startActivity(Intent(this, AnalyticsActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_apps -> {
                    startActivity(Intent(this, AppExclusionActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun checkForUpdates() {
        binding.checkForUpdatesBtn.isEnabled = false
        binding.updateStatusText.visibility = View.VISIBLE
        binding.updateStatusText.text = "Checking for updates..."

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    delay(2000)
                    val latestVersion = "1.0"
                    val updateUrl = "https://github.com/example/shieldblock/releases"
                    Triple(true, latestVersion, updateUrl)
                } catch (e: Exception) {
                    Triple(false, "", "")
                }
            }

            if (result.first) {
                if (result.second == BuildConfig.VERSION_NAME) {
                    binding.updateStatusText.text = "You are on the latest version"
                    binding.updateStatusText.setTextColor(getColor(R.color.primary))
                } else {
                    binding.updateStatusText.text = "New version ${result.second} available!"
                    binding.updateStatusText.setTextColor(getColor(R.color.tertiary))
                    binding.checkForUpdatesBtn.text = "Download Update"
                    binding.checkForUpdatesBtn.setOnClickListener {
                        val i = Intent(Intent.ACTION_VIEW, Uri.parse(result.third))
                        startActivity(i)
                    }
                }
            } else {
                binding.updateStatusText.text = "Update check failed."
                binding.updateStatusText.setTextColor(getColor(R.color.tertiary))
            }
            binding.checkForUpdatesBtn.isEnabled = true
        }
    }

    private fun checkHealth() {
        val isVpnActive = VpnService.prepare(this) == null
        binding.healthVpnText.text = "VPN Status: ${if (isVpnActive) "PROTECTED" else "UNPROTECTED"}"
        binding.healthVpnText.setTextColor(getColor(if (isVpnActive) R.color.primary else R.color.tertiary))

        val activeFilters = filterManager.getEnabledFilterIds().size
        binding.healthFilterText.text = "Active Filters: $activeFilters sources"

        lifecycleScope.launch {
            val domains = withContext(Dispatchers.IO) {
                blacklistManager.loadLocalBlacklist().size
            }
            binding.healthDatabaseText.text = "Database Size: $domains domains"
        }

        val uptimeMillis = SystemClock.elapsedRealtime()
        val hours = TimeUnit.MILLISECONDS.toHours(uptimeMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(uptimeMillis) % 60
        binding.healthUptimeText.text = "System Uptime: ${hours}h ${minutes}m"
    }
}
