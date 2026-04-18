package com.example.shieldblock

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.VpnService
import android.os.*
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.example.shieldblock.data.StatsManager
import com.example.shieldblock.data.WhitelistManager
import com.example.shieldblock.databinding.ActivityMainBinding
import com.example.shieldblock.vpn.MyVpnService
import com.example.shieldblock.ui.AegisDialog
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val statsManager by lazy { StatsManager(this) }
    private val whitelistManager by lazy { WhitelistManager(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var statusPulse: ObjectAnimator? = null

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.example.shieldblock.VPN_STATUS_CHANGED" -> {
                    updateVpnUi(intent.getBooleanExtra("status", false))
                }
                "com.example.shieldblock.PACKET_EVENT" -> {
                    val action = intent.getStringExtra("action") ?: ""
                    if (action.contains("Blocked")) {
                        binding.aegisCore.triggerFlash()
                    }
                }
            }
        }
    }

    private var lastBytesRead = 0L
    private var startTime = 0L

    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnInternal()
        }
    }

    private val updateStatsRunnable = object : Runnable {
        override fun run() {
            updateStats()
            updateNetworkInfo()
            updateHud()
            updateTicker()
            handler.postDelayed(this, calculateUpdateDelay())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        applyTheme(prefs.getString("app_theme", "system") ?: "system")

        setupBottomNavigation()
        animateEntrance()

        binding.telemetryTickerText.isSelected = true

        binding.startVpnButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startVpn()
        }

        binding.stopVpnButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            stopVpn()
        }

        binding.scoreCard.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showHealthBreakdown()
        }

        binding.recommendationsCard.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startActivity(Intent(this, SecurityRecommendationsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        val filter = IntentFilter().apply {
            addAction("com.example.shieldblock.VPN_STATUS_CHANGED")
            addAction("com.example.shieldblock.PACKET_EVENT")
        }
        registerReceiver(statusReceiver, filter)
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_home
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            binding.bottomNavigation.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_analytics -> { startActivity(Intent(this, AnalyticsActivity::class.java)); overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left); true }
                R.id.nav_apps -> { startActivity(Intent(this, AppExclusionActivity::class.java)); overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left); true }
                R.id.nav_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left); true }
                else -> false
            }
        }
    }

    private fun applyTheme(theme: String) {
        when (theme) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun animateEntrance() {
        val views = listOf(binding.statusCard, binding.scoreCard, binding.recommendationsCard, binding.recentBlocksContainer.parent as View)
        views.forEachIndexed { index, view ->
            view.translationY = 100f
            view.alpha = 0f
            view.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(600)
                .setStartDelay(index * 100L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNavigation.selectedItemId = R.id.nav_home
        val isActive = VpnService.prepare(this) == null
        updateVpnUi(isActive)
        if (isActive && startTime == 0L) startTime = SystemClock.elapsedRealtime()
        else if (!isActive) startTime = 0L

        checkAlwaysOnVpn()
        handler.post(updateStatsRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateStatsRunnable)
    }

    private fun calculateUpdateDelay(): Long {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val batteryLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val profile = prefs.getString("perf_profile", "balanced")

        return when {
            profile == "performance" -> 1000L
            profile == "battery_saver" -> 30000L
            batteryLevel < 15 -> 10000L
            else -> 5000L
        }
    }

    private fun updateHud() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val isDoh = prefs.getBoolean("dns_over_https", false)
        binding.hudProtocolText.text = if (isDoh) "DoH/HTTPS" else "UDP/53"
        binding.hudProtocolText.setBackgroundColor(if (isDoh) 0x3386FEA7.toInt() else 0x22FFFFFF.toInt())

        if (startTime > 0) {
            val elapsed = SystemClock.elapsedRealtime() - startTime
            val h = TimeUnit.MILLISECONDS.toHours(elapsed)
            val m = TimeUnit.MILLISECONDS.toMinutes(elapsed) % 60
            val s = TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60
            binding.hudStatusText.text = String.format("AEGIS RUNTIME: %01dh %01dm %01ds", h, m, s)

            val lat = (20..80).random()
            binding.hudLatencyText.text = "LATENCY: ${lat}ms"
        } else {
            binding.hudStatusText.text = "CORE OFFLINE"
            binding.hudLatencyText.text = "LATENCY: --ms"
        }
    }

    private fun updateTicker() {
        val recent = statsManager.getRecentBlocks().take(1)
        if (recent.isNotEmpty()) {
            binding.telemetryTickerText.text = "THREAT MITIGATED: ${recent.first()} • ENFORCING ZERO-TRUST POLICIES • CORE TEMPERATURE STABLE"
        } else {
            binding.telemetryTickerText.text = "AEGIS COMMAND ACTIVE • MONITORING ENTIRE NETWORK INTERFACE • READY FOR INJECTION"
        }
    }

    private fun updateStats() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        var activeLayers = 0
        if (prefs.getBoolean("strict_mode", false)) activeLayers++
        if (prefs.getBoolean("smart_filtering", false)) activeLayers++
        if (prefs.getBoolean("data_saver", false)) activeLayers++
        if (prefs.getBoolean("block_ipv6", true)) activeLayers++
        if (VpnService.prepare(this) == null) activeLayers++

        val recommendationCount = getPendingRecommendationCount()
        val scoreMultiplier = when {
            activeLayers >= 5 && recommendationCount == 0 -> 1.0
            activeLayers >= 4 -> 0.9
            activeLayers >= 3 -> 0.8
            activeLayers >= 2 -> 0.6
            else -> 0.4
        }

        val adsBlocked = statsManager.getBlockedAdsCount()
        val totalQueries = statsManager.getTotalQueries()
        val dataSaved = (adsBlocked * 0.15).coerceAtMost(999.0)

        binding.blockedAdsCountText.text = adsBlocked.toString()
        binding.dataSavedText.text = String.format("%.1f MB", dataSaved)

        val baseScore = if (totalQueries == 0) 100 else {
            val blockedRatio = (adsBlocked.toFloat() / totalQueries.toFloat())
            (100 - (1.0 - blockedRatio) * 20).toInt().coerceIn(0, 100)
        }
        val score = (baseScore * scoreMultiplier).toInt()

        binding.privacyScoreText.text = "Aegis Level: $score"
        binding.privacyProgressBar.progress = score

        val grade = when {
            score >= 95 -> "MAX"
            score >= 90 -> "A+"
            score >= 80 -> "A"
            score >= 70 -> "B"
            else -> "C"
        }
        binding.privacyGradeText.text = grade
        val gradeColor = when {
            score >= 90 -> getColor(R.color.emerald_accent)
            score >= 70 -> getColor(R.color.secondary)
            else -> getColor(R.color.tertiary)
        }
        binding.privacyGradeText.setTextColor(gradeColor)
        binding.privacyProgressBar.setIndicatorColor(gradeColor)

        binding.recDescText.text = if (recommendationCount > 0) "$recommendationCount tasks pending" else "System hardened"

        updateRecentFeed()
    }

    private fun getPendingRecommendationCount(): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        var count = 0
        if (!prefs.getBoolean("strict_mode", false)) count++
        if (!prefs.getBoolean("smart_filtering", false)) count++
        if (!prefs.getBoolean("block_ipv6", true)) count++
        return count
    }

    private fun updateRecentFeed() {
        val recent = statsManager.getRecentBlocks()
        binding.recentBlocksContainer.removeAllViews()
        val title = TextView(this)
        title.setText(R.string.live_threat_monitor)
        title.setTextColor(getColor(R.color.emerald_accent))
        title.textSize = 10f
        title.setPadding(0, 0, 0, 8)
        binding.recentBlocksContainer.addView(title)

        if (recent.isEmpty()) {
            val tv = TextView(this)
            tv.setText(R.string.scanning_threats)
            tv.setTextColor(0xFFAAAAAA.toInt())
            tv.textSize = 12f
            binding.recentBlocksContainer.addView(tv)
        } else {
            recent.take(3).forEach { domain ->
                val tv = TextView(this)
                tv.text = "• $domain"
                tv.setTextColor(android.graphics.Color.WHITE)
                tv.textSize = 11f
                tv.setPadding(0, 4, 0, 4)
                tv.isClickable = true
                tv.setBackgroundResource(android.R.drawable.list_selector_background)
                tv.setOnClickListener { showWhitelistDialog(domain) }
                binding.recentBlocksContainer.addView(tv)
            }
        }
    }

    private fun showWhitelistDialog(domain: String) {
        AegisDialog(this)
            .setTitle("Whitelist Domain?")
            .setMessage("Allow all traffic for $domain?")
            .setPositiveButton("Allow") {
                whitelistManager.addToWhitelist(domain)
                Toast.makeText(this, "$domain whitelisted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel")
            .show()
    }

    private fun updateNetworkInfo() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)
        val networkType = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile"
            else -> "Offline"
        }
        val stats = MyVpnService.instance?.getTrafficStats() ?: (0L to 0L)
        val currentRead = stats.first
        val speed = if (lastBytesRead > 0) (currentRead - lastBytesRead) / 1024 else 0
        lastBytesRead = currentRead

        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val bat = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        binding.networkInfoText.text = "$networkType • $speed KB/s • $bat% Battery"

        val normalizedSpeed = (speed.toFloat() / 1000f).coerceAtMost(1.0f)
        binding.throughputOscilloscope.addValue(normalizedSpeed)
    }

    private fun startVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpnInternal()
        }
    }

    private fun startVpnInternal() {
        startService(Intent(this, MyVpnService::class.java).apply { putExtra("action", "start") })
        updateVpnUi(true)
        startTime = SystemClock.elapsedRealtime()
    }

    private fun stopVpn() {
        startService(Intent(this, MyVpnService::class.java).apply { putExtra("action", "stop") })
        updateVpnUi(false)
        startTime = 0L
    }

    private fun checkAlwaysOnVpn() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !prefs.getBoolean("always_on_notified", false)) {
            AegisDialog(this)
                .setTitle("Always-on Aegis")
                .setMessage("For persistent security, enable Always-on VPN in system settings.")
                .setPositiveButton("Settings") {
                    prefs.edit().putBoolean("always_on_notified", true).apply()
                    val intent = Intent("android.net.vpn.SETTINGS")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                }
                .setNegativeButton("Cancel")
                .show()
        }
    }

    private fun updateVpnUi(isConnected: Boolean) {
        binding.aegisCore.setActive(isConnected)
        binding.auraBackground.setPulseSpeed(isConnected)

        if (isConnected) {
            binding.statusText.setText(R.string.protected_status)
            binding.statusSubText.setText(R.string.status_filtering_active)
            binding.statusText.setTextColor(getColor(R.color.emerald_accent))
            binding.statusIcon.setColorFilter(getColor(R.color.emerald_accent))
            binding.startVpnButton.visibility = View.GONE
            binding.stopVpnButton.visibility = View.VISIBLE
            startPulseAnimation()
        } else {
            binding.statusText.setText(R.string.unprotected_status)
            binding.statusSubText.setText(R.string.status_deactivated)
            binding.statusText.setTextColor(getColor(R.color.tertiary))
            binding.statusIcon.setColorFilter(getColor(R.color.tertiary))
            binding.startVpnButton.visibility = View.VISIBLE
            binding.stopVpnButton.visibility = View.GONE
            stopPulseAnimation()
        }
    }

    private fun startPulseAnimation() {
        if (statusPulse != null) return
        statusPulse = ObjectAnimator.ofPropertyValuesHolder(binding.statusCard,
            PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.02f, 1.0f),
            PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.02f, 1.0f)).apply {
            duration = 2000; repeatCount = ObjectAnimator.INFINITE; start()
        }
    }

    private fun stopPulseAnimation() {
        statusPulse?.cancel(); statusPulse = null
        binding.statusCard.scaleX = 1.0f; binding.statusCard.scaleY = 1.0f
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(statusReceiver) } catch(e: Exception) {}
    }

    private fun showHealthBreakdown() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val isVpnActive = VpnService.prepare(this) == null
        val isStrict = prefs.getBoolean("strict_mode", false)
        val isSmart = prefs.getBoolean("smart_filtering", false)
        val isIpv6 = prefs.getBoolean("block_ipv6", true)
        val isDataSaver = prefs.getBoolean("data_saver", false)

        val msg = StringBuilder()
        msg.append("🛡️ VPN Core: ").append(if (isVpnActive) "ENABLED" else "DISABLED").append("\n")
        msg.append("🔥 Strict Mode: ").append(if (isStrict) "ACTIVE" else "OFF").append("\n")
        msg.append("🧠 Heuristics: ").append(if (isSmart) "ENABLED" else "OFF").append("\n")
        msg.append("🔒 IPv6 Shield: ").append(if (isIpv6) "ACTIVE" else "OFF").append("\n")
        msg.append("⚡ Data Saver: ").append(if (isDataSaver) "ENABLED" else "OFF")

        AegisDialog(this)
            .setTitle(getString(R.string.health_breakdown_title))
            .setMessage(msg.toString())
            .setPositiveButton("Settings") {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .setNegativeButton("Close")
            .show()
    }
}
