package com.example.shieldblock

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.example.shieldblock.data.BackupManager
import com.example.shieldblock.data.FilterManager
import com.example.shieldblock.data.StatsManager
import com.example.shieldblock.databinding.SettingsActivityBinding
import com.example.shieldblock.vpn.MyVpnService
import com.example.shieldblock.ui.AegisDialog

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: SettingsActivityBinding
    private val filterManager by lazy { FilterManager(this) }
    private val statsManager by lazy { StatsManager(this) }
    private val backupManager by lazy { BackupManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        setupBottomNavigation()
        refreshSwitches()

        binding.strictModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("strict_mode", isChecked).apply()
            notifyServiceReload()
        }
        binding.smartFilterSwitch.setOnCheckedChangeListener { _, isChecked ->
            PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("smart_filtering", isChecked).apply()
            notifyServiceReload()
        }
        binding.dataSaverSwitch.setOnCheckedChangeListener { _, isChecked ->
            PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("data_saver", isChecked).apply()
            notifyServiceReload()
        }
        binding.blockIpv6Switch.setOnCheckedChangeListener { _, isChecked ->
            PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("block_ipv6", isChecked).apply()
            notifyServiceReload()
        }
        binding.dnsOverHttpsSwitch.setOnCheckedChangeListener { _, isChecked ->
            PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("dns_over_https", isChecked).apply()
            notifyServiceReload()
        }

        binding.dnsConfigLayout.setOnClickListener { showDnsProfileDialog() }
        binding.networkRulesLayout.setOnClickListener { startActivity(Intent(this, NetworkRulesActivity::class.java)) }

        binding.forceUpdateBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            androidx.work.WorkManager.getInstance(this).enqueueUniqueWork(
                "blacklist_update_manual",
                androidx.work.ExistingWorkPolicy.REPLACE,
                androidx.work.OneTimeWorkRequestBuilder<BlacklistWorker>().build()
            )
            Toast.makeText(this, "Updating Filter Databases...", Toast.LENGTH_SHORT).show()
        }

        binding.backupConfigBtn.setOnClickListener { showBackupDialog() }
        binding.viewLogsButton.setOnClickListener { startActivity(Intent(this, LogActivity::class.java)) }
        binding.aboutButton.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }
        binding.resetStatsBtn.setOnClickListener { showResetStatsDialog() }
    }

    private fun notifyServiceReload() {
        if (MyVpnService.instance != null) {
            startService(Intent(this, MyVpnService::class.java).apply { putExtra("action", "reload") })
        }
    }

    private fun refreshSwitches() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        binding.strictModeSwitch.isChecked = prefs.getBoolean("strict_mode", false)
        binding.smartFilterSwitch.isChecked = prefs.getBoolean("smart_filtering", false)
        binding.dataSaverSwitch.isChecked = prefs.getBoolean("data_saver", false)
        binding.blockIpv6Switch.isChecked = prefs.getBoolean("block_ipv6", true)
        binding.dnsOverHttpsSwitch.isChecked = prefs.getBoolean("dns_over_https", false)
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_settings
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            binding.bottomNavigation.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            when (item.itemId) {
                R.id.nav_home -> { startActivity(Intent(this, MainActivity::class.java)); overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right); finish(); true }
                R.id.nav_analytics -> { startActivity(Intent(this, AnalyticsActivity::class.java)); overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right); finish(); true }
                R.id.nav_apps -> { startActivity(Intent(this, AppExclusionActivity::class.java)); overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right); finish(); true }
                R.id.nav_settings -> true
                else -> false
            }
        }
    }

    private fun showDnsProfileDialog() {
        val profiles = filterManager.dnsProfiles
        val names = (profiles.map { "${it.name} (${it.primaryDns})" } + "Manual Input").toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select DNS Provider")
            .setItems(names) { _, which ->
                if (which < profiles.size) {
                    val selected = profiles[which]
                    PreferenceManager.getDefaultSharedPreferences(this)
                        .edit().putString("custom_dns", selected.primaryDns).apply()
                    notifyServiceReload()
                } else {
                    showManualDnsDialog()
                }
            }
            .show()
    }

    private fun showManualDnsDialog() {
        val input = EditText(this)
        val currentDns = PreferenceManager.getDefaultSharedPreferences(this).getString("custom_dns", "8.8.8.8")
        input.setText(currentDns)
        AlertDialog.Builder(this)
            .setTitle("Manual DNS Input")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val dns = input.text.toString().trim()
                if (dns.isNotEmpty()) {
                    PreferenceManager.getDefaultSharedPreferences(this)
                        .edit().putString("custom_dns", dns).apply()
                    notifyServiceReload()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBackupDialog() {
        val options = arrayOf("Export Configuration", "Import Configuration")
        AlertDialog.Builder(this)
            .setTitle("Backup & Restore")
            .setItems(options) { _, which ->
                if (which == 0) {
                    val json = backupManager.createBackup()
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_TEXT, json)
                    }
                    startActivity(Intent.createChooser(intent, "Save Backup"))
                } else {
                    showImportDialog()
                }
            }.show()
    }

    private fun showImportDialog() {
        val input = EditText(this)
        input.hint = "Paste backup JSON here"
        AlertDialog.Builder(this)
            .setTitle("Import Config")
            .setView(input)
            .setPositiveButton("Import") { _, _ ->
                val json = input.text.toString()
                if (backupManager.restoreBackup(json)) {
                    Toast.makeText(this, "Configuration Restored!", Toast.LENGTH_LONG).show()
                    finishAffinity()
                    startActivity(Intent(this, MainActivity::class.java))
                } else {
                    Toast.makeText(this, "Invalid Backup Format", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showResetStatsDialog() {
        AegisDialog(this)
            .setTitle("Purge Data?")
            .setMessage("Clear all telemetry counters and system logs?")
            .setPositiveButton("Purge") {
                statsManager.resetStats()
                Toast.makeText(this, "Data Purged", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel")
            .show()
    }
}
