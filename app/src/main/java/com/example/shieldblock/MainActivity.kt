package com.example.shieldblock

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.VpnService
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.work.*
import com.example.shieldblock.data.StatsManager
import com.example.shieldblock.data.WhitelistManager
import com.example.shieldblock.databinding.ActivityMainBinding
import com.example.shieldblock.vpn.MyVpnService
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val whitelistManager by lazy { WhitelistManager(this) }
    private val statsManager by lazy { StatsManager(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var statusPulse: ObjectAnimator? = null
    private var shieldRotate: ObjectAnimator? = null
    private var lastBytesRead = 0L

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.all { it.value }) {
            checkBackgroundLocation()
        }
    }

    private val updateStatsRunnable = object : Runnable {
        override fun run() {
            updateStats()
            updateNetworkInfo()
            val delay = calculateUpdateDelay()
            handler.postDelayed(this, delay)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        applyTheme(prefs.getString("app_theme", "system") ?: "system")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation()
        setupQuickActions()
        requestNecessaryPermissions()
        checkBatteryOptimizations()

        val isRunning = VpnService.prepare(this) == null
        updateVpnUi(isRunning)
        updateStats()
        updateNetworkInfo()

        binding.startVpnButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startVpn()
        }
        binding.stopVpnButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            stopVpn()
        }

        animateEntrance()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_home
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            binding.bottomNavigation.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_analytics -> {
                    startActivity(Intent(this, AnalyticsActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    false
                }
                R.id.nav_apps -> {
                    startActivity(Intent(this, AppExclusionActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    false
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    false
                }
                else -> false
            }
        }
    }

    private fun setupQuickActions() {
        binding.quickScanBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startActivity(Intent(this, SecurityAuditActivity::class.java))
        }
        binding.quickDnsBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startActivity(Intent(this, DnsLatencyActivity::class.java))
        }
        binding.quickLogBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startActivity(Intent(this, LogActivity::class.java))
        }
    }

    private fun requestNecessaryPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)

        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) {
            permissionLauncher.launch(toRequest.toTypedArray())
        } else {
            checkBackgroundLocation()
        }
    }

    private fun checkBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.background_location_title)
                    .setMessage(R.string.background_location_msg)
                    .setPositiveButton(R.string.nav_settings) { _, _ ->
                        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 101)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun checkBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = packageName
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                binding.statusSubText.setText(R.string.battery_optimization_warning)
                binding.statusSubText.setOnClickListener {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                }
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
        binding.statusCard.translationY = 100f
        binding.statusCard.alpha = 0f
        binding.statusCard.animate().translationY(0f).alpha(1f).setDuration(800).setInterpolator(DecelerateInterpolator()).start()
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNavigation.selectedItemId = R.id.nav_home
        updateVpnUi(VpnService.prepare(this) == null)
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

    private fun updateStats() {
        val adsBlocked = statsManager.getBlockedAdsCount()
        val totalQueries = statsManager.getTotalQueries()
        val dataSaved = (adsBlocked * 0.15).coerceAtMost(999.0)

        binding.blockedAdsCountText.text = adsBlocked.toString()
        binding.dataSavedText.text = String.format("%.1f MB", dataSaved)

        val score = if (totalQueries == 0) 100 else {
            val blockedRatio = (adsBlocked.toFloat() / totalQueries.toFloat())
            (100 - (1.0 - blockedRatio) * 20).toInt().coerceIn(0, 100)
        }

        binding.privacyScoreText.text = getString(R.string.rating_label, score)
        binding.privacyProgressBar.progress = score

        val grade = when {
            score >= 95 -> "A+"
            score >= 90 -> "A"
            score >= 80 -> "B"
            score >= 70 -> "C"
            else -> "D"
        }
        binding.privacyGradeText.text = grade
        val gradeColor = when {
            score >= 90 -> getColor(R.color.primary)
            score >= 70 -> getColor(R.color.secondary)
            else -> getColor(R.color.tertiary)
        }
        binding.privacyGradeText.setTextColor(gradeColor)
        binding.privacyProgressBar.setIndicatorColor(gradeColor)

        updateRecentFeed()
    }

    private fun updateRecentFeed() {
        val recent = statsManager.getRecentBlocks()
        binding.recentBlocksContainer.removeAllViews()
        val title = TextView(this)
        title.setText(R.string.live_threat_monitor)
        title.setTextColor(getColor(R.color.primary))
        title.textSize = 10f
        title.setPadding(0, 0, 0, 8)
        binding.recentBlocksContainer.addView(title)

        if (recent.isEmpty()) {
            val tv = TextView(this)
            tv.setText(R.string.scanning_threats)
            tv.setTextColor(getColor(R.color.on_surface_variant))
            tv.textSize = 12f
            binding.recentBlocksContainer.addView(tv)
        } else {
            recent.take(3).forEach { domain ->
                val tv = TextView(this)
                tv.text = "• $domain"
                tv.setTextColor(getColor(R.color.on_surface))
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
        AlertDialog.Builder(this)
            .setTitle(R.string.whitelist_dialog_title)
            .setMessage(getString(R.string.whitelist_dialog_msg, domain))
            .setPositiveButton(R.string.allow) { _, _ ->
                whitelistManager.addToWhitelist(domain)
                Toast.makeText(this, getString(R.string.whitelisted_toast, domain), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
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
    }

    private fun startVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, 0)
        } else {
            startService(Intent(this, MyVpnService::class.java).apply { putExtra("action", "start") })
            updateVpnUi(true)
        }
    }

    private fun stopVpn() {
        startService(Intent(this, MyVpnService::class.java).apply { putExtra("action", "stop") })
        updateVpnUi(false)
    }

    private fun updateVpnUi(isConnected: Boolean) {
        if (isConnected) {
            binding.statusText.setText(R.string.protected_status)
            binding.statusSubText.setText(R.string.status_filtering_active)
            binding.statusText.setTextColor(getColor(R.color.primary))
            binding.statusIcon.setColorFilter(getColor(R.color.primary))
            binding.startVpnButton.visibility = View.GONE
            binding.stopVpnButton.visibility = View.VISIBLE
            startPulseAnimation()
            startRotateAnimation()
        } else {
            binding.statusText.setText(R.string.unprotected_status)
            binding.statusSubText.setText(R.string.status_deactivated)
            binding.statusText.setTextColor(getColor(R.color.tertiary))
            binding.statusIcon.setColorFilter(getColor(R.color.tertiary))
            binding.startVpnButton.visibility = View.VISIBLE
            binding.stopVpnButton.visibility = View.GONE
            stopPulseAnimation()
            stopRotateAnimation()
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

    private fun startRotateAnimation() {
        if (shieldRotate != null) return
        shieldRotate = ObjectAnimator.ofFloat(binding.statusIcon, "rotation", 0f, 360f).apply {
            duration = 4000; repeatCount = ObjectAnimator.INFINITE; interpolator = LinearInterpolator(); start()
        }
    }

    private fun stopRotateAnimation() {
        shieldRotate?.cancel(); shieldRotate = null; binding.statusIcon.rotation = 0f
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) startVpn()
    }
}
