package com.example.shieldblock

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.example.shieldblock.data.StatsManager
import com.example.shieldblock.data.WhitelistManager
import com.example.shieldblock.data.BlacklistManager
import com.example.shieldblock.data.FilterManager
import com.example.shieldblock.databinding.SettingsActivityBinding
import com.example.shieldblock.databinding.DialogAddSourceBinding
import com.example.shieldblock.analytics.EventLogger

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: SettingsActivityBinding
    private val statsManager by lazy { StatsManager(this) }
    private val whitelistManager by lazy { WhitelistManager(this) }
    private val blacklistManager by lazy { BlacklistManager(this) }
    private val filterManager by lazy { FilterManager(this) }
    private val eventLogger by lazy { EventLogger(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // Automation & Theme
        binding.autoStartSwitch.isChecked = prefs.getBoolean("auto_start", false)
        binding.notificationSwitch.isChecked = prefs.getBoolean("notifications", true)

        binding.autoStartSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_start", isChecked).apply()
        }
        binding.notificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications", isChecked).apply()
        }

        binding.themeSelectionLayout.setOnClickListener { showThemeDialog() }
        updateThemeText()

        // Filtering
        updateDnsText()
        binding.dnsSettingsLayout.setOnClickListener { showDnsDialog() }
        binding.manageWhitelistLayout.setOnClickListener { showWhitelistDialog() }
        binding.manageBlacklistLayout.setOnClickListener { showBlacklistDialog() }
        binding.appExclusionLayout.setOnClickListener {
            startActivity(Intent(this, AppExclusionActivity::class.java))
        }

        // Support
        binding.resetStatsButton.setOnClickListener {
            statsManager.resetStats()
            Toast.makeText(this, "Stats reset", Toast.LENGTH_SHORT).show()
        }
        binding.viewLogsButton.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
        binding.clearLogsButton.setOnClickListener {
            eventLogger.clearLogs()
            Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show()
        }

        binding.exportSettingsButton.setOnClickListener { exportWhitelist() }
        binding.importSettingsButton.setOnClickListener { importWhitelist() }
    }

    private fun updateDnsText() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        binding.currentDnsText.text = prefs.getString("custom_dns", "8.8.8.8")
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
        val checkedItem = themeValues.indexOf(currentTheme)

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

    private fun showDnsDialog() {
        val input = EditText(this)
        input.setText(binding.currentDnsText.text)

        AlertDialog.Builder(this)
            .setTitle("Custom DNS Server")
            .setMessage("Enter the IP address of your preferred DNS server (e.g., 1.1.1.1)")
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

    private fun showWhitelistDialog() {
        val domains = whitelistManager.getWhitelist().toList()
        if (domains.isEmpty()) {
            Toast.makeText(this, "Whitelist is empty", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Manage Whitelist")
            .setItems(domains.toTypedArray()) { _, which ->
                val domain = domains[which]
                whitelistManager.removeFromWhitelist(domain)
                Toast.makeText(this, " removed", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showBlacklistDialog() {
        val allSources = filterManager.getAllSources()
        val names = allSources.map { it.name }.toTypedArray()
        val enabledIds = filterManager.getEnabledFilterIds()
        val checkedItems = allSources.map { enabledIds.contains(it.id) }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle("Blocklist Sources")
            .setMultiChoiceItems(names, checkedItems) { _, which, isChecked ->
                filterManager.setFilterEnabled(allSources[which].id, isChecked)
            }
            .setPositiveButton("Add Source") { _, _ -> showAddSourceDialog() }
            .setNeutralButton("Done", null)
            .show()
    }

    private fun showAddSourceDialog() {
        val dialogBinding = DialogAddSourceBinding.inflate(LayoutInflater.from(this))
        AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Add") { _, _ ->
                val name = dialogBinding.sourceNameEditText.text.toString()
                val url = dialogBinding.sourceUrlEditText.text.toString()
                if (name.isNotBlank() && url.isNotBlank()) {
                    filterManager.addCustomSource(name, url)
                    Toast.makeText(this, "Source added", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportWhitelist() {
        val domains = whitelistManager.getWhitelist().joinToString("\n")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, domains)
            putExtra(Intent.EXTRA_SUBJECT, "ShieldBlock Whitelist Export")
        }
        startActivity(Intent.createChooser(intent, "Export Whitelist"))
    }

    private fun importWhitelist() {
        val input = EditText(this)
        input.hint = "Paste domains, one per line"
        AlertDialog.Builder(this)
            .setTitle("Import Whitelist")
            .setView(input)
            .setPositiveButton("Import") { _, _ ->
                val text = input.text.toString()
                text.lines().filter { it.isNotBlank() }.forEach {
                    whitelistManager.addToWhitelist(it.trim())
                }
                Toast.makeText(this, "Import complete", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
