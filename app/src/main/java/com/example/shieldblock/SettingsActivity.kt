package com.example.shieldblock

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.example.shieldblock.databinding.SettingsActivityBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: SettingsActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load saved preferences
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        binding.autoStartSwitch.isChecked = prefs.getBoolean("auto_start", false)
        binding.notificationSwitch.isChecked = prefs.getBoolean("notifications", true)

        // Save preferences on change
        binding.autoStartSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_start", isChecked).apply()
        }

        binding.notificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications", isChecked).apply()
        }
    }
}
