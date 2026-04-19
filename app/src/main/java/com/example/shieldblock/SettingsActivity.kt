package com.example.shieldblock

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.example.shieldblock.data.BackupManager
import com.example.shieldblock.data.FilterManager
import com.example.shieldblock.data.StatsManager
import com.example.shieldblock.databinding.SettingsActivityBinding
import com.example.shieldblock.vpn.MyVpnService
import com.example.shieldblock.vpn.AegisNodeService
import com.example.shieldblock.ui.AegisDialog
import java.util.concurrent.Executor

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: SettingsActivityBinding
    private val filterManager by lazy { FilterManager(this) }
    private val statsManager by lazy { StatsManager(this) }
    private val backupManager by lazy { BackupManager(this) }
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        executor = ContextCompat.getMainExecutor(this)
        setupBiometric()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        setupBottomNavigation()
        refreshSwitches()

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        binding.strictModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("strict_mode", isChecked).apply()
            notifyServiceReload()
        }
        binding.smartFilterSwitch.setOnCheckedChangeListener { _, isChecked ->
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
        binding.dnsOverHttpsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dns_over_https", isChecked).apply()
            notifyServiceReload()
        }

        binding.stealthModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("stealth_mode", isChecked).apply()
            notifyServiceReload()
        }
        binding.neuralHeuristicsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("neural_heuristics", isChecked).apply()
            notifyServiceReload()
        }
        binding.quantumProtocolSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("quantum_protocol", isChecked).apply()
            notifyServiceReload()
        }

        binding.aegisNodeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    startActivity(intent)
                    binding.aegisNodeSwitch.isChecked = false
                } else {
                    startService(Intent(this, AegisNodeService::class.java))
                    prefs.edit().putBoolean("aegis_node_enabled", true).apply()
                }
            } else {
                stopService(Intent(this, AegisNodeService::class.java))
                prefs.edit().putBoolean("aegis_node_enabled", false).apply()
            }
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
            Toast.makeText(this, "Updating Core Rulesets...", Toast.LENGTH_SHORT).show()
        }

        binding.backupConfigBtn.setOnClickListener {
            authenticate { showBackupDialog() }
        }
        binding.viewLogsButton.setOnClickListener { startActivity(Intent(this, LogActivity::class.java)) }
        binding.aboutButton.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }
        binding.resetStatsBtn.setOnClickListener { showResetStatsDialog() }
    }

    private fun setupBiometric() {
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onAuthSuccess?.invoke()
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Aegis Authorization")
            .setSubtitle("Authenticate to access core configuration")
            .setNegativeButtonText("Abort")
            .build()
    }

    private var onAuthSuccess: (() -> Unit)? = null
    private fun authenticate(action: () -> Unit) {
        onAuthSuccess = action
        biometricPrompt.authenticate(promptInfo)
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

        binding.stealthModeSwitch.isChecked = prefs.getBoolean("stealth_mode", false)
        binding.neuralHeuristicsSwitch.isChecked = prefs.getBoolean("neural_heuristics", false)
        binding.quantumProtocolSwitch.isChecked = prefs.getBoolean("quantum_protocol", false)
        binding.aegisNodeSwitch.isChecked = prefs.getBoolean("aegis_node_enabled", false)
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
            .setTitle("Core DNS Matrix")
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
            .setTitle("Manual Probe Setup")
            .setView(input)
            .setPositiveButton("Commit") { _, _ ->
                val dns = input.text.toString().trim()
                if (dns.isNotEmpty()) {
                    PreferenceManager.getDefaultSharedPreferences(this)
                        .edit().putString("custom_dns", dns).apply()
                    notifyServiceReload()
                }
            }
            .setNegativeButton("Abort", null)
            .show()
    }

    private fun showBackupDialog() {
        val options = arrayOf("Export Configuration", "Import Configuration")
        AlertDialog.Builder(this)
            .setTitle("Config Vault")
            .setItems(options) { _, which ->
                if (which == 0) {
                    val json = backupManager.createBackup()
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_TEXT, json)
                    }
                    startActivity(Intent.createChooser(intent, "Secure Export"))
                } else {
                    showImportDialog()
                }
            }.show()
    }

    private fun showImportDialog() {
        val input = EditText(this)
        input.hint = "PASTE ENCRYPTED PAYLOAD"
        AlertDialog.Builder(this)
            .setTitle("Core Injection")
            .setView(input)
            .setPositiveButton("Inject") { _, _ ->
                val json = input.text.toString()
                if (backupManager.restoreBackup(json)) {
                    Toast.makeText(this, "Aegis Core Restored!", Toast.LENGTH_LONG).show()
                    finishAffinity()
                    startActivity(Intent(this, MainActivity::class.java))
                } else {
                    Toast.makeText(this, "MALFORMED PAYLOAD", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Abort", null).show()
    }

    private fun showResetStatsDialog() {
        AegisDialog(this)
            .setTitle("Purge Data?")
            .setMessage("Clear all telemetry counters and system logs?")
            .setPositiveButton("Purge") {
                statsManager.resetStats()
                Toast.makeText(this, "Data Purged", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Abort")
            .show()
    }
}
