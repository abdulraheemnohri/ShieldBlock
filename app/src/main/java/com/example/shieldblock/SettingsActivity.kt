package com.example.shieldblock

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
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

        // Load saved preferences
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        binding.autoStartSwitch.isChecked = prefs.getBoolean("auto_start", false)
        binding.notificationSwitch.isChecked = prefs.getBoolean("notifications", true)

        val currentDns = prefs.getString("custom_dns", "8.8.8.8")
        binding.currentDnsText.text = currentDns

        // Save preferences on change
        binding.autoStartSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_start", isChecked).apply()
        }

        binding.notificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications", isChecked).apply()
        }

        binding.dnsSettingsLayout.setOnClickListener {
            // Placeholder for DNS selection dialog
            Toast.makeText(this, "DNS settings coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.viewLogsButton.setOnClickListener {
            Toast.makeText(this, "Opening logs...", Toast.LENGTH_SHORT).show()
        }

        binding.clearLogsButton.setOnClickListener {
            Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show()
        }
    }
}
