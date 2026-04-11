package com.example.shieldblock

import android.net.VpnService
import android.os.Bundle
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.shieldblock.data.BlacklistManager
import com.example.shieldblock.data.FilterManager
import com.example.shieldblock.databinding.ActivityAboutBinding
import kotlinx.coroutines.Dispatchers
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
        binding.toolbar.setNavigationOnClickListener { finish() }

        checkHealth()
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
