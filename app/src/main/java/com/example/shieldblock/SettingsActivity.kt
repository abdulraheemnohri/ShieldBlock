package com.example.shieldblock

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.example.shieldblock.data.BackupManager
import com.example.shieldblock.data.BlacklistManager
import com.example.shieldblock.data.FilterManager
import com.example.shieldblock.data.StatsManager
import com.example.shieldblock.data.WhitelistManager
import com.example.shieldblock.databinding.SettingsActivityBinding
import com.example.shieldblock.data.DnsProfile
import com.example.shieldblock.vpn.MyVpnService

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
        setupListeners()
        refreshSwitches()
        updateDnsText()
        updateThemeText()
        updatePerformanceProfileText()
        updateUpdateFreqText()
    }

    private fun setupListeners() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        binding.strictModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("strict_mode", isChecked).apply()
            notifyServiceReload()
        }
        binding.smartFilteringSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("smart_filtering", isChecked).apply()
            notifyServiceReload()
        }
        binding.dataSaverSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("data_saver", isChecked).apply()
            notifyServiceReload()
        }
        binding.blockIpv6Switch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("block_ipv6", isChecked).apply()
            notifyServiceReload()
        }
        binding.autoStartSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_start", isChecked).apply()
        }
        binding.logAllQueriesSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("log_all_queries", isChecked).apply()
            notifyServiceReload()
        }

        binding.updateFreqLayout.setOnClickListener { showUpdateFreqDialog() }

        // Profiles
        binding.profileKidsBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            filterManager.applyProfile("KIDS")
            refreshSwitches()
            notifyServiceReload()
            Toast.makeText(this, "Kids Mode Applied", Toast.LENGTH_SHORT).show()
        }
        binding.profileWorkBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            filterManager.applyProfile("WORK")
            refreshSwitches()
            notifyServiceReload()
            Toast.makeText(this, "Work Mode Applied", Toast.LENGTH_SHORT).show()
        }

        // Filtering
        binding.manageBlacklistLayout.setOnClickListener { startActivity(Intent(this, SourceManagementActivity::class.java)) }
        binding.ruleEditorLayout.setOnClickListener { startActivity(Intent(this, RuleEditorActivity::class.java)) }
        binding.manageWhitelistLayout.setOnClickListener {
            startActivity(Intent(this, WhitelistActivity::class.java))
        }

        // DNS
        binding.dnsSettingsLayout.setOnClickListener { showDnsProfileDialog() }
        binding.dnsLatencyLayout.setOnClickListener { startActivity(Intent(this, DnsLatencyActivity::class.java)) }
        binding.networkRulesLayout.setOnClickListener { startActivity(Intent(this, NetworkRulesActivity::class.java)) }

        // General
        binding.themeSettingsLayout.setOnClickListener { showThemeDialog() }
        binding.performanceProfileLayout.setOnClickListener { showPerformanceProfileDialog() }

        // Support & System
        binding.forceUpdateBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            androidx.work.WorkManager.getInstance(this).enqueueUniqueWork(
                "blacklist_update_manual",
                androidx.work.ExistingWorkPolicy.REPLACE,
                androidx.work.OneTimeWorkRequestBuilder<BlacklistWorker>().build()
            )
            Toast.makeText(this, R.string.update_triggered, Toast.LENGTH_SHORT).show()
        }

        binding.backupConfigBtn.setOnClickListener { showBackupDialog() }
        binding.resetStatsBtn.setOnClickListener { showResetStatsDialog() }
        binding.viewLogsButton.setOnClickListener { startActivity(Intent(this, LogActivity::class.java)) }
        binding.aboutButton.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }
    }

    private fun notifyServiceReload() {
        if (MyVpnService.instance != null) {
            startService(Intent(this, MyVpnService::class.java).apply { putExtra("action", "reload") })
        }
    }

    private fun refreshSwitches() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        binding.strictModeSwitch.isChecked = prefs.getBoolean("strict_mode", false)
        binding.smartFilteringSwitch.isChecked = prefs.getBoolean("smart_filtering", false)
        binding.dataSaverSwitch.isChecked = prefs.getBoolean("data_saver", false)
        binding.blockIpv6Switch.isChecked = prefs.getBoolean("block_ipv6", true)
        binding.autoStartSwitch.isChecked = prefs.getBoolean("auto_start", false)
        binding.logAllQueriesSwitch.isChecked = prefs.getBoolean("log_all_queries", false)
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_settings
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            binding.bottomNavigation.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                    finish()
                    true
                }
                R.id.nav_analytics -> {
                    startActivity(Intent(this, AnalyticsActivity::class.java))
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                    finish()
                    true
                }
                R.id.nav_apps -> {
                    startActivity(Intent(this, AppExclusionActivity::class.java))
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                    finish()
                    true
                }
                R.id.nav_settings -> true
                else -> false
            }
        }
    }

    private fun updateDnsText() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        binding.currentDnsText.text = prefs.getString("custom_dns", "8.8.8.8")
    }

    private fun updateThemeText() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        binding.currentThemeText.text = (prefs.getString("app_theme", "system") ?: "system").replaceFirstChar { it.uppercase() }
    }

    private fun updatePerformanceProfileText() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        binding.currentProfileText.text = (prefs.getString("perf_profile", "balanced") ?: "balanced").replaceFirstChar { it.uppercase() }
    }

    private fun showDnsProfileDialog() {
        val profiles = filterManager.dnsProfiles
        val names = (profiles.map { "${it.name} (${it.primaryDns})" } + "Manual Input").toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select DNS Profile")
            .setItems(names) { _, which ->
                if (which < profiles.size) {
                    val selected = profiles[which]
                    PreferenceManager.getDefaultSharedPreferences(this)
                        .edit().putString("custom_dns", selected.primaryDns).apply()
                    updateDnsText()
                    notifyServiceReload()
                } else {
                    showManualDnsDialog()
                }
            }
            .show()
    }

    private fun showManualDnsDialog() {
        val input = EditText(this)
        input.setText(binding.currentDnsText.text)
        AlertDialog.Builder(this)
            .setTitle("Manual DNS Input")
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val dns = input.text.toString().trim()
                if (dns.isNotEmpty()) {
                    PreferenceManager.getDefaultSharedPreferences(this)
                        .edit().putString("custom_dns", dns).apply()
                    updateDnsText()
                    notifyServiceReload()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showThemeDialog() {
        val themes = arrayOf("System Default", "Light", "Dark")
        val values = arrayOf("system", "light", "dark")
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getString("app_theme", "system")
        val checkedItem = values.indexOf(current ?: "system")

        AlertDialog.Builder(this)
            .setTitle("Choose Theme")
            .setSingleChoiceItems(themes, checkedItem) { dialog, which ->
                val selected = values[which]
                prefs.edit().putString("app_theme", selected).apply()
                updateThemeText()
                applyTheme(selected)
                dialog.dismiss()
            }.show()
    }

    private fun applyTheme(theme: String) {
        when (theme) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun showPerformanceProfileDialog() {
        val options = arrayOf("Performance", "Balanced", "Battery Saver")
        val values = arrayOf("performance", "balanced", "battery_saver")
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getString("perf_profile", "balanced")
        val checkedItem = values.indexOf(current ?: "balanced")

        AlertDialog.Builder(this)
            .setTitle("Performance Profile")
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                prefs.edit().putString("perf_profile", values[which]).apply()
                updatePerformanceProfileText()
                dialog.dismiss()
            }.show()
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
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun showUpdateFreqDialog() {
        val options = arrayOf("Every 12 Hours", "Every 24 Hours", "Every 48 Hours")
        val values = intArrayOf(12, 24, 48)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getInt("update_frequency", 24)
        val checkedItem = values.indexOf(current)

        AlertDialog.Builder(this)
            .setTitle(R.string.update_freq)
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                prefs.edit().putInt("update_frequency", values[which]).apply()
                updateUpdateFreqText()
                dialog.dismiss()
            }.show()
    }

    private fun updateUpdateFreqText() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getInt("update_frequency", 24)
        binding.currentUpdateFreqText.text = "Every $current Hours"
    }

    private fun showResetStatsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Reset Data?")
            .setMessage("This will clear all blocked counters and live feed history.")
            .setPositiveButton("Reset") { _, _ ->
                statsManager.resetStats()
                Toast.makeText(this, "Statistics Reset", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null).show()
    }
}
