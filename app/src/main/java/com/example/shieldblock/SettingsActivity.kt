package com.example.shieldblock

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.appcompat.app.AppCompatActivity
import com.example.shieldblock.databinding.SettingsActivityBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: SettingsActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = "Aegis Technical Configuration"

        binding.diagnosticsBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }

        binding.ruleForgeBtn.setOnClickListener {
            startActivity(Intent(this, RuleEditorActivity::class.java))
        }

        binding.sourceMgmtBtn.setOnClickListener {
            startActivity(Intent(this, SourceManagementActivity::class.java))
        }

        binding.scheduleBtn.setOnClickListener {
            startActivity(Intent(this, SentinelScheduleActivity::class.java))
        }

        binding.vaultBtn.setOnClickListener {
            startActivity(Intent(this, VaultActivity::class.java))
        }

        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_settings
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { startActivity(Intent(this, MainActivity::class.java)); finish(); true }
                R.id.nav_analytics -> { startActivity(Intent(this, AnalyticsActivity::class.java)); finish(); true }
                R.id.nav_apps -> { startActivity(Intent(this, AppExclusionActivity::class.java)); finish(); true }
                else -> false
            }
        }
    }
}
