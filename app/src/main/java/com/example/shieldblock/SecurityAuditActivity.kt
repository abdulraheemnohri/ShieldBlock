package com.example.shieldblock

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
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
        binding.toolbar.setNavigationOnClickListener { finish() }

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
                R.id.nav_home -> { startActivity(Intent(this, MainActivity::class.java)); finish(); true }
                R.id.nav_analytics -> { startActivity(Intent(this, AnalyticsActivity::class.java)); finish(); true }
                R.id.nav_apps -> { startActivity(Intent(this, AppExclusionActivity::class.java)); finish(); true }
                R.id.nav_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); finish(); true }
                else -> false
            }
        }
    }

    private fun runAudit() {
        binding.auditDetailsText.text = "Scanning applications..."
        lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) {
                auditor.runAudit()
            }
            binding.securityScoreText.text = report.securityScore.toString()
            binding.securityScoreProgress.progress = report.securityScore
            binding.auditDetailsText.text = if (report.risks.isEmpty()) {
                "No major security risks found in installed apps."
            } else {
                report.risks.joinToString("\n\n")
            }
        }
    }
}
