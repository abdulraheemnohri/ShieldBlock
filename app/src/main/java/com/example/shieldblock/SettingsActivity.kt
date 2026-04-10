package com.example.shieldblock

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.example.shieldblock.data.StatsManager
import com.example.shieldblock.data.WhitelistManager
import com.example.shieldblock.data.BlacklistManager
import com.example.shieldblock.data.FilterManager
import com.example.shieldblock.databinding.SettingsActivityBinding
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

        // Automation
        binding.autoStartSwitch.isChecked = prefs.getBoolean("auto_start", false)
        binding.notificationSwitch.isChecked = prefs.getBoolean("notifications", true)

        binding.autoStartSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_start", isChecked).apply()
        }
        binding.notificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications", isChecked).apply()
        }

        // Filtering
        updateDnsText()
        binding.dnsSettingsLayout.setOnClickListener { showDnsDialog() }
        binding.manageWhitelistLayout.setOnClickListener { showWhitelistDialog() }
        binding.manageBlacklistLayout.setOnClickListener { showBlacklistDialog() }

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
    }

    private fun updateDnsText() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        binding.currentDnsText.text = prefs.getString("custom_dns", "8.8.8.8")
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
        val filters = filterManager.defaultFilters
        val filterNames = filters.map { it.name }.toTypedArray()
        val enabledIds = filterManager.getEnabledFilterIds()
        val checkedItems = filters.map { enabledIds.contains(it.id) }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle("Blocklist Sources")
            .setMultiChoiceItems(filterNames, checkedItems) { _, which, isChecked ->
                filterManager.setFilterEnabled(filters[which].id, isChecked)
            }
            .setPositiveButton("Done", null)
            .show()
    }
}
