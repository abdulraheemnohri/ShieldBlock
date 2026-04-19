package com.example.shieldblock

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.shieldblock.data.StatsManager
import com.example.shieldblock.databinding.ActivityAppDossierBinding

class AppDossierActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAppDossierBinding
    private val statsManager by lazy { StatsManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppDossierBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val packageName = intent.getStringExtra("package_name") ?: ""
        val appName = intent.getStringExtra("app_name") ?: "Unknown Agent"

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        binding.toolbar.title = "Dossier: $appName"

        setupBottomNavigation()
        loadDossier(packageName)
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_apps
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            binding.bottomNavigation.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            when (item.itemId) {
                R.id.nav_home -> { startActivity(Intent(this, MainActivity::class.java)); finish(); true }
                R.id.nav_analytics -> { startActivity(Intent(this, AnalyticsActivity::class.java)); finish(); true }
                R.id.nav_apps -> { finish(); true }
                R.id.nav_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); finish(); true }
                else -> false
            }
        }
    }

    private fun loadDossier(pkg: String) {
        val blockedCount = statsManager.getAppBlockedCount(pkg)
        binding.blockCountText.text = blockedCount.toString()
        binding.packageText.text = pkg

        // Simulated telemetry data for technical aesthetic
        binding.lastActivityText.text = "LAST MITIGATION: ${System.currentTimeMillis() - (1000..50000).random()} (UNIX)"
        binding.threatLevelText.text = if (blockedCount > 50) "CRITICAL" else if (blockedCount > 10) "ELEVATED" else "MINIMAL"
        binding.threatLevelText.setTextColor(if (blockedCount > 50) 0xFFFF5555.toInt() else if (blockedCount > 10) 0xFFFFAA00.toInt() else 0xFF86FEA7.toInt())
    }
}
