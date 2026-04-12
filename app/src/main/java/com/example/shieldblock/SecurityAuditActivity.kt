package com.example.shieldblock

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.shieldblock.data.SecurityAuditor
import com.example.shieldblock.databinding.ActivitySecurityAuditBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SecurityAuditActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySecurityAuditBinding
    private val auditor by lazy { SecurityAuditor(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecurityAuditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        setupBottomNavigation()
        runAudit()

        binding.reScanBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            runAudit()
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_analytics
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            binding.bottomNavigation.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            when (item.itemId) {
                R.id.nav_home -> { startActivity(Intent(this, MainActivity::class.java)); overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right); finish(); true }
                R.id.nav_analytics -> { startActivity(Intent(this, AnalyticsActivity::class.java)); overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right); finish(); true }
                R.id.nav_apps -> { startActivity(Intent(this, AppExclusionActivity::class.java)); overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right); finish(); true }
                R.id.nav_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left); finish(); true }
                else -> false
            }
        }
    }

    private fun runAudit() {
        binding.scoreText.text = ".."
        binding.riskContainer.removeAllViews()

        lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) {
                auditor.runAudit()
            }

            binding.scoreText.text = report.securityScore.toString()

            if (report.appRisks.isEmpty()) {
                val emptyTv = TextView(this@SecurityAuditActivity).apply {
                    text = "No major risks detected."
                    setTextColor(0xFFAAAAAA.toInt())
                    setPadding(0, 32, 0, 0)
                    gravity = android.view.Gravity.CENTER
                }
                binding.riskContainer.addView(emptyTv)
            } else {
                report.appRisks.forEach { (name, perms) ->
                    val view = View.inflate(this@SecurityAuditActivity, R.layout.item_risk, null)
                    view.findViewById<TextView>(R.id.appName).text = name
                    view.findViewById<TextView>(R.id.riskDescription).text = "Permissions: $perms"
                    binding.riskContainer.addView(view)
                }
            }
        }
    }
}
