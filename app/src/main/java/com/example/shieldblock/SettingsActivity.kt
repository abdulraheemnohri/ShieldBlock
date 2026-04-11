package com.example.shieldblock

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.work.*
import com.example.shieldblock.data.*
import com.example.shieldblock.databinding.SettingsActivityBinding
import com.example.shieldblock.analytics.EventLogger
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupBottomNavigation()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // Ultra Protection
        binding.strictModeSwitch.isChecked = prefs.getBoolean("strict_mode", false)
        binding.strictModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("strict_mode", isChecked).apply()
        }

        binding.smartFilteringSwitch.isChecked = prefs.getBoolean("smart_filtering", false)
        binding.smartFilteringSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("smart_filtering", isChecked).apply()
        }

        binding.dataSaverSwitch.isChecked = prefs.getBoolean("data_saver", false)
        binding.dataSaverSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("data_saver", isChecked).apply()
        }

        // Profiles
        binding.profileKidsBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            filterManager.applyProfile("KIDS")
            refreshSwitches()
            Toast.makeText(this, "Kids Mode Applied", Toast.LENGTH_SHORT).show()
        }
        binding.profileWorkBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            filterManager.applyProfile("WORK")
            refreshSwitches()
            Toast.makeText(this, "Work Mode Applied", Toast.LENGTH_SHORT).show()
        }

        // Filtering
        binding.manageBlacklistLayout.setOnClickListener {
            startActivity(Intent(this, SourceManagementActivity::class.java))
        }
        binding.ruleEditorLayout.setOnClickListener {
            val intent = Intent(this, TextEditorActivity::class.java)
            intent.putExtra("file_path", BlacklistManager(this).getCustomFilePath())
            intent.putExtra("file_name", "blacklist_custom.txt")
            startActivity(intent)
        }
        binding.manageWhitelistLayout.setOnClickListener {
            val intent = Intent(this, TextEditorActivity::class.java)
            intent.putExtra("file_path", WhitelistManager(this).getManualFilePath())
            intent.putExtra("file_name", "whitelist_manual.txt")
            startActivity(intent)
        }

        // DNS & Tools
        updateDnsText()
        binding.dnsSettingsLayout.setOnClickListener { showDnsProfileDialog() }
        binding.dnsLatencyLayout.setOnClickListener {
            startActivity(Intent(this, DnsLatencyActivity::class.java))
        }
        binding.networkRulesLayout.setOnClickListener {
            startActivity(Intent(this, NetworkRulesActivity::class.java))
        }

        // Maintenance (Backup & Restore)
        binding.backupConfigBtn.setOnClickListener { showBackupDialog() }
        binding.resetStatsBtn.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset Data?")
                .setMessage("This will clear all blocked counters and live feed history.")
                .setPositiveButton("Reset") { _, _ ->
                    statsManager.resetStats()
                    Toast.makeText(this, "Statistics Reset", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null).show()
        }

        // General
        updateThemeText()
        binding.themeSettingsLayout.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showThemeDialog()
        }
        updatePerformanceProfileText()
        binding.performanceProfileLayout.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showPerformanceProfileDialog()
        }

        // Support
        binding.viewLogsButton.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
        binding.aboutButton.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
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
                    finishAffinity() // Restart app
                } else {
                    Toast.makeText(this, "Invalid Backup Format", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun refreshSwitches() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        binding.strictModeSwitch.isChecked = prefs.getBoolean("strict_mode", false)
        binding.smartFilteringSwitch.isChecked = prefs.getBoolean("smart_filtering", false)
        binding.dataSaverSwitch.isChecked = prefs.getBoolean("data_saver", false)
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
                R.id.nav_settings -> true
                else -> false
            }
        }
    }

    private fun showPerformanceProfileDialog() {
        val options = arrayOf("Performance", "Balanced", "Battery Saver")
        val values = arrayOf("performance", "balanced", "battery_saver")
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getString("perf_profile", "balanced")
        val checked = values.indexOf(current ?: "balanced")

        AlertDialog.Builder(this)
            .setTitle("Performance Profile")
            .setSingleChoiceItems(options, checked) { dialog, which ->
                prefs.edit().putString("perf_profile", values[which]).apply()
                updatePerformanceProfileText()
                dialog.dismiss()
            }.show()
    }

    private fun updatePerformanceProfileText() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        binding.currentProfileText.text = prefs.getString("perf_profile", "balanced")?.replaceFirstChar { it.uppercase() }
    }

    private fun updateDnsText() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        binding.currentDnsText.text = prefs.getString("custom_dns", "8.8.8.8")
    }

    private fun showDnsProfileDialog() {
        val profiles: List<DnsProfile> = filterManager.dnsProfiles
        val names: Array<String> = (profiles.map { "${it.name} (${it.primaryDns})" } + "Manual Input").toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select DNS Profile")
            .setItems(names) { _, which ->
                if (which < profiles.size) {
                    val selected = profiles[which]
                    PreferenceManager.getDefaultSharedPreferences(this)
                        .edit().putString("custom_dns", selected.primaryDns).apply()
                    updateDnsText()
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
            .setPositiveButton("Save") { _, _ ->
                val dns = input.text.toString().trim()
                if (dns.isNotEmpty()) {
                    PreferenceManager.getDefaultSharedPreferences(this)
                        .edit().putString("custom_dns", dns).apply()
                    updateDnsText()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateThemeText() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val theme = prefs.getString("app_theme", "system")
        binding.currentThemeText.text = theme?.replaceFirstChar { it.uppercase() }
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
                applyTheme(selected)
                updateThemeText()
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
}
