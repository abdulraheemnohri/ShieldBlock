package com.example.shieldblock

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.work.*
import com.example.shieldblock.data.StatsManager
import com.example.shieldblock.data.WhitelistManager
import com.example.shieldblock.data.BlacklistManager
import com.example.shieldblock.data.FilterManager
import com.example.shieldblock.data.DnsProfile
import com.example.shieldblock.databinding.SettingsActivityBinding
import com.example.shieldblock.analytics.EventLogger
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: SettingsActivityBinding
    private val statsManager by lazy { StatsManager(this) }
    private val whitelistManager by lazy { WhitelistManager(this) }
    private val blacklistManager by lazy { BlacklistManager(this) }
    private val filterManager by lazy { FilterManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Theme
        updateThemeText()
        binding.themeSettingsLayout.setOnClickListener { showThemeDialog() }

        // Profile
        updatePerformanceProfileText()
        binding.performanceProfileLayout.setOnClickListener { showPerformanceProfileDialog() }

        // Schedule
        binding.scheduledProtectionLayout.setOnClickListener { showScheduleDialog() }
        updateScheduleText()

        // Filtering
        binding.manageBlacklistLayout.setOnClickListener {
            startActivity(Intent(this, SourceManagementActivity::class.java))
        }
        binding.ruleEditorLayout.setOnClickListener {
            startActivity(Intent(this, RuleEditorActivity::class.java))
        }
        binding.manageWhitelistLayout.setOnClickListener {
            startActivity(Intent(this, WhitelistActivity::class.java))
        }
        binding.updateFrequencyLayout.setOnClickListener { showUpdateFrequencyDialog() }
        updateFrequencyText()

        // Manual Editors
        binding.manualWhitelistEditorLayout.setOnClickListener {
            val intent = Intent(this, TextEditorActivity::class.java)
            intent.putExtra("file_path", whitelistManager.getManualFilePath())
            intent.putExtra("file_name", "whitelist_manual.txt")
            startActivity(intent)
        }
        binding.manualRulesEditorLayout.setOnClickListener {
            val intent = Intent(this, TextEditorActivity::class.java)
            intent.putExtra("file_path", blacklistManager.getCustomFilePath())
            intent.putExtra("file_name", "blacklist_custom.txt")
            startActivity(intent)
        }

        // DNS & Apps
        updateDnsText()
        binding.dnsSettingsLayout.setOnClickListener { showDnsProfileDialog() }
        binding.dnsLatencyLayout.setOnClickListener {
            startActivity(Intent(this, DnsLatencyActivity::class.java))
        }
        binding.appExclusionLayout.setOnClickListener {
            startActivity(Intent(this, AppExclusionActivity::class.java))
        }
        binding.networkRulesLayout.setOnClickListener {
            startActivity(Intent(this, NetworkRulesActivity::class.java))
        }

        // Maintenance
        updateLastUpdateText()
        binding.updateBlocklistsButton.setOnClickListener {
            scheduleWork(PreferenceManager.getDefaultSharedPreferences(this).getInt("update_frequency", 24))
            Toast.makeText(this, "Update scheduled", Toast.LENGTH_SHORT).show()
        }
        binding.backupConfigLayout.setOnClickListener { showBackupRestoreDialog() }
        binding.resetAllButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Factory Reset?")
                .setMessage("This will clear ALL settings, rules, and statistics. This cannot be undone.")
                .setPositiveButton("Reset Everything") { _, _ ->
                    PreferenceManager.getDefaultSharedPreferences(this).edit().clear().apply()
                    statsManager.resetStats()
                    Toast.makeText(this, "App reset. Please restart.", Toast.LENGTH_LONG).show()
                    finishAffinity()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Support
        binding.viewLogsButton.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
        binding.aboutButton.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    private fun showScheduleDialog() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val options = arrayOf("Enabled", "Disabled", "Set Start Time", "Set End Time")
        AlertDialog.Builder(this)
            .setTitle("Scheduled Protection")
            .setItems(options) { _, which ->
                when(which) {
                    0 -> { prefs.edit().putBoolean("scheduled_enabled", true).apply(); updateScheduleText() }
                    1 -> { prefs.edit().putBoolean("scheduled_enabled", false).apply(); updateScheduleText() }
                    2 -> showTimePicker("scheduled_start")
                    3 -> showTimePicker("scheduled_end")
                }
            }.show()
    }

    private fun showTimePicker(key: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        TimePickerDialog(this, { _, h, m ->
            prefs.edit().putString(key, String.format("%02d:%02d", h, m)).apply()
            updateScheduleText()
        }, 0, 0, true).show()
    }

    private fun updateScheduleText() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val enabled = prefs.getBoolean("scheduled_enabled", false)
        if (!enabled) {
            binding.currentScheduleText.text = "Disabled"
        } else {
            val start = prefs.getString("scheduled_start", "00:00")
            val end = prefs.getString("scheduled_end", "00:00")
            binding.currentScheduleText.text = "Active between $start - $end"
        }
    }

    private fun showPerformanceProfileDialog() {
        val options = arrayOf("Performance (Real-time stats)", "Balanced (Every 5s)", "Battery Saver (Manual only)")
        val values = arrayOf("performance", "balanced", "battery_saver")
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getString("perf_profile", "balanced")
        val checked = values.indexOf(current ?: "balanced")

        AlertDialog.Builder(this)
            .setTitle("Performance Profile")
            .setSingleChoiceItems(options, checked) { dialog, which ->
                val selected = values[which]
                prefs.edit().putString("perf_profile", selected).apply()
                updatePerformanceProfileText()
                dialog.dismiss()
            }.show()
    }

    private fun updatePerformanceProfileText() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getString("perf_profile", "balanced")
        binding.currentProfileText.text = current?.replace("_", " ")?.replaceFirstChar { it.uppercase() }
    }

    private fun showUpdateFrequencyDialog() {
        val options = arrayOf("Every 12 hours", "Every 24 hours", "Every 48 hours")
        val values = intArrayOf(12, 24, 48)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getInt("update_frequency", 24)
        val checked = values.indexOf(current)

        AlertDialog.Builder(this)
            .setTitle("Update Frequency")
            .setSingleChoiceItems(options, checked) { dialog, which ->
                val freq = values[which]
                prefs.edit().putInt("update_frequency", freq).apply()
                scheduleWork(freq)
                updateFrequencyText()
                dialog.dismiss()
            }.show()
    }

    private fun scheduleWork(hours: Int) {
        val work = PeriodicWorkRequestBuilder<BlacklistWorker>(hours.toLong(), TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "blacklist_update",
            ExistingPeriodicWorkPolicy.REPLACE,
            work
        )
    }

    private fun updateFrequencyText() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getInt("update_frequency", 24)
        binding.currentFrequencyText.text = "Every $current hours"
    }

    private fun showBackupRestoreDialog() {
        val options = arrayOf("Export Config", "Import Config")
        AlertDialog.Builder(this)
            .setTitle("Backup & Restore")
            .setItems(options) { _, which ->
                if (which == 0) exportConfig() else importConfig()
            }.show()
    }

    private fun exportConfig() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val all = prefs.all
        val json = JSONObject(all)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TEXT, json.toString(4))
            putExtra(Intent.EXTRA_SUBJECT, "ShieldBlock Config Backup")
        }
        startActivity(Intent.createChooser(intent, "Export Config"))
    }

    private fun importConfig() {
        val input = EditText(this)
        input.hint = "Paste JSON config here"
        AlertDialog.Builder(this)
            .setTitle("Import Config")
            .setView(input)
            .setPositiveButton("Import") { _, _ ->
                try {
                    val json = JSONObject(input.text.toString())
                    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                    val editor = prefs.edit()
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        when (val value = json.get(key)) {
                            is Boolean -> editor.putBoolean(key, value)
                            is String -> editor.putString(key, value)
                            is Int -> editor.putInt(key, value)
                        }
                    }
                    editor.apply()
                    Toast.makeText(this, "Config imported. Restart recommended.", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Invalid JSON config", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateDnsText() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        binding.currentDnsText.text = prefs.getString("custom_dns", "8.8.8.8")
    }

    private fun updateLastUpdateText() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val last = prefs.getString("last_blacklist_update", "Never")
        binding.lastUpdateText.text = "Last updated: $last"
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
                    Toast.makeText(this, "${selected.name} applied", Toast.LENGTH_SHORT).show()
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
        val themeValues = arrayOf("system", "light", "dark")
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val currentTheme = prefs.getString("app_theme", "system")
        val checkedItem = themeValues.indexOf(currentTheme ?: "system")

        AlertDialog.Builder(this)
            .setTitle("Choose Theme")
            .setSingleChoiceItems(themes, checkedItem) { dialog, which ->
                val selected = themeValues[which]
                prefs.edit().putString("app_theme", selected).apply()
                applyTheme(selected)
                updateThemeText()
                dialog.dismiss()
            }
            .show()
    }

    private fun applyTheme(theme: String) {
        when (theme) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}
