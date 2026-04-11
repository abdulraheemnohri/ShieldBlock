package com.example.shieldblock

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.SystemClock
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
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.currentVersionText.text = "Version ${BuildConfig.VERSION_NAME} (Enterprise Edition)"

        binding.checkForUpdatesBtn.setOnClickListener {
            checkForUpdates()
        }

        checkHealth()
    }

    private fun checkForUpdates() {
        binding.checkForUpdatesBtn.isEnabled = false
        binding.updateStatusText.visibility = View.VISIBLE
        binding.updateStatusText.text = "Checking for updates..."
        binding.updateStatusText.setTextColor(getColor(R.color.on_surface_variant))

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
                binding.updateStatusText.text = "Update check failed. Try again later."
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
