package com.example.shieldblock

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.example.shieldblock.databinding.ActivitySecurityRecommendationsBinding

class SecurityRecommendationsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySecurityRecommendationsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecurityRecommendationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        setupBottomNavigation()
        loadRecommendations()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_home
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            binding.bottomNavigation.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            when (item.itemId) {
                R.id.nav_home -> { finish(); true }
                R.id.nav_analytics -> { startActivity(Intent(this, AnalyticsActivity::class.java)); finish(); true }
                R.id.nav_apps -> { startActivity(Intent(this, AppExclusionActivity::class.java)); finish(); true }
                R.id.nav_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); finish(); true }
                else -> false
            }
        }
    }

    private fun loadRecommendations() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        binding.recommendationsContainer.removeAllViews()

        val recommendations = mutableListOf<Pair<String, String>>()

        if (!prefs.getBoolean("strict_mode", false)) {
            recommendations.add("Enable Strict Mode" to "Blocks all traffic except whitelisted domains for maximum security.")
        }
        if (!prefs.getBoolean("smart_filtering", false)) {
            recommendations.add("Activate Heuristic Filter" to "Uses intelligence to detect and block new tracker patterns automatically.")
        }
        if (!prefs.getBoolean("dns_over_https", false)) {
            recommendations.add("Enable DNS-over-HTTPS" to "Encrypts your DNS queries to prevent ISP tracking and DNS spoofing.")
        }
        if (prefs.getStringSet("excluded_apps", emptySet())?.isNotEmpty() == true) {
            recommendations.add("Review App Bypasses" to "Some apps are bypassing the shield. Review exclusions to maximize protection.")
        }

        if (recommendations.isEmpty()) {
            val tv = TextView(this).apply {
                text = "🛡️ Your device is fully hardened."
                setTextColor(0xFF86FEA7.toInt())
                gravity = android.view.Gravity.CENTER
                setPadding(0, 50, 0, 0)
            }
            binding.recommendationsContainer.addView(tv)
        } else {
            recommendations.forEach { (title, desc) ->
                val card = View.inflate(this, R.layout.item_recommendation, null)
                card.findViewById<TextView>(R.id.recTitle).text = title
                card.findViewById<TextView>(R.id.recDesc).text = desc
                card.setOnClickListener {
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                binding.recommendationsContainer.addView(card)
            }
        }
    }
}
