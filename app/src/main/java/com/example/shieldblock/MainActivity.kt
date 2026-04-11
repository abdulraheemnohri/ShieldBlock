package com.example.shieldblock

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.work.*
import com.example.shieldblock.data.StatsManager
import com.example.shieldblock.data.WhitelistManager
import com.example.shieldblock.databinding.ActivityMainBinding
import com.example.shieldblock.vpn.MyVpnService
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val whitelistManager by lazy { WhitelistManager(this) }
    private val statsManager by lazy { StatsManager(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var statusPulse: ObjectAnimator? = null
    private var lastBytesRead = 0L

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

        updateVpnUi(VpnService.prepare(this) == null)
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
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.analyticsCard.setOnClickListener {
            startActivity(Intent(this, AnalyticsActivity::class.java))
        }

        binding.quickBenchmarkBtn.setOnClickListener { startActivity(Intent(this, DnsLatencyActivity::class.java)) }
        binding.quickAppsBtn.setOnClickListener { startActivity(Intent(this, AppExclusionActivity::class.java)) }
        binding.quickLogsBtn.setOnClickListener { startActivity(Intent(this, LogActivity::class.java)) }

        binding.shareButton.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Secure your connection with ShieldBlock Ultra! High-performance privacy.")
            }
            startActivity(Intent.createChooser(shareIntent, "Share ShieldBlock"))
        }

        val freq = prefs.getInt("update_frequency", 24)
        val work = PeriodicWorkRequestBuilder<BlacklistWorker>(freq.toLong(), TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "blacklist_update",
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )

        runEntranceAnimations()
    }

    private fun calculateUpdateDelay(): Long {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        var profile = prefs.getString("perf_profile", "balanced")

        // Auto Battery Saver check
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            baseContext.registerReceiver(null, filter)
        }
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = level * 100 / scale.toFloat()

        if (batteryPct < 15) profile = "battery_saver"

        return when(profile) {
            "performance" -> 1000L
            "battery_saver" -> 30000L
            else -> 5000L
        }
    }

    private fun runEntranceAnimations() {
        val views = listOf(
            binding.analyticsCard,
            binding.statusCard,
            binding.startVpnButton,
            binding.settingsButton
        )

        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 50f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setStartDelay(100L * index)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun applyTheme(theme: String) {
        when (theme) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateStatsRunnable)
        updateVpnUi(VpnService.prepare(this) == null)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateStatsRunnable)
    }

    private fun updateStats() {
        binding.blockedAdsCountText.text = statsManager.getBlockedAdsCount().toString()
        binding.dataSavedText.text = statsManager.getDataSavedEstimates()

        val score = statsManager.getPrivacyScore()
        binding.privacyScoreText.text = "Score: $score"
        binding.privacyProgressBar.progress = score

        val grade = when {
            score >= 90 -> "A+"
            score >= 80 -> "A"
            score >= 70 -> "B"
            score >= 60 -> "C"
            else -> "D"
        }
        binding.privacyGradeText.text = grade

        val gradeColor = when {
            score >= 80 -> getColor(R.color.primary)
            score >= 60 -> getColor(R.color.secondary)
            else -> getColor(R.color.tertiary)
        }
        binding.privacyGradeText.setTextColor(gradeColor)
        binding.privacyProgressBar.setIndicatorColor(gradeColor)

        updateTopBlocked()
        updateRecentFeed()
    }

    private fun updateRecentFeed() {
        val recent = statsManager.getRecentBlocks()
        binding.recentBlocksContainer.removeAllViews()

        val title = TextView(this)
        title.text = "LIVE FEED"
        title.setTextColor(getColor(R.color.primary))
        title.textSize = 10f
        title.setPadding(0, 0, 0, 8)
        binding.recentBlocksContainer.addView(title)

        if (recent.isEmpty()) {
            val tv = TextView(this)
            tv.text = "Waiting for activity..."
            tv.setTextColor(getColor(R.color.on_surface_variant))
            tv.textSize = 12f
            binding.recentBlocksContainer.addView(tv)
        } else {
            recent.forEach { domain ->
                val tv = TextView(this)
                tv.text = "• $domain"
                tv.setTextColor(getColor(R.color.on_surface))
                tv.textSize = 11f
                tv.setPadding(0, 4, 0, 4)
                tv.isClickable = true
                tv.setBackgroundResource(android.R.drawable.list_selector_background)
                tv.setOnClickListener { showDomainDetail(domain) }
                binding.recentBlocksContainer.addView(tv)
            }
        }
    }

    private fun showDomainDetail(domain: String) {
        AlertDialog.Builder(this)
            .setTitle("Domain Detail")
            .setMessage("Domain: $domain\nAction: Blocked\nReason: Blacklist Match")
            .setPositiveButton("Whitelist") { _, _ ->
                whitelistManager.addToWhitelist(domain)
                Toast.makeText(this, "$domain added to whitelist", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
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

        binding.networkInfoText.text = "$networkType • $speed KB/s processed"
    }

    private fun updateTopBlocked() {
        val topDomains = statsManager.getTopBlockedDomains()
        binding.topBlockedContainer.removeAllViews()

        if (topDomains.isEmpty()) {
            val tv = TextView(this)
            tv.text = "No domains blocked yet"
            tv.setTextColor(getColor(R.color.on_surface_variant))
            binding.topBlockedContainer.addView(tv)
        } else {
            topDomains.forEach { (domain, count) ->
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.setPadding(0, 8, 0, 8)
                row.isClickable = true
                row.setBackgroundResource(android.R.drawable.list_selector_background)
                row.setOnClickListener { showDomainDetail(domain) }

                val domainTv = TextView(this)
                domainTv.text = domain
                domainTv.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                domainTv.setTextColor(getColor(R.color.on_surface))

                val countTv = TextView(this)
                countTv.text = count.toString()
                countTv.setTextColor(getColor(R.color.primary))
                countTv.setPadding(16, 0, 0, 0)

                row.addView(domainTv)
                row.addView(countTv)
                binding.topBlockedContainer.addView(row)
            }
        }
    }

    private fun startVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, 0)
        } else {
            startService(Intent(this, MyVpnService::class.java).apply {
                putExtra("action", "start")
            })
            updateVpnUi(true)
        }
    }

    private fun stopVpn() {
        startService(Intent(this, MyVpnService::class.java).apply {
            putExtra("action", "stop")
        })
        updateVpnUi(false)
    }

    private fun updateVpnUi(isConnected: Boolean) {
        if (isConnected) {
            binding.statusText.text = "Protected"
            binding.statusSubText.text = "Your connection is encrypted and filtered"
            binding.statusText.setTextColor(getColor(R.color.primary))
            binding.statusIcon.setColorFilter(getColor(R.color.primary))
            binding.startVpnButton.visibility = View.GONE
            binding.stopVpnButton.visibility = View.VISIBLE
            startPulseAnimation()
        } else {
            binding.statusText.text = "Unprotected"
            binding.statusSubText.text = "Your connection is direct and unfiltered"
            binding.statusText.setTextColor(getColor(R.color.tertiary))
            binding.statusIcon.setColorFilter(getColor(R.color.tertiary))
            binding.startVpnButton.visibility = View.VISIBLE
            binding.stopVpnButton.visibility = View.GONE
            stopPulseAnimation()
        }
    }

    private fun startPulseAnimation() {
        if (statusPulse != null) return
        statusPulse = ObjectAnimator.ofPropertyValuesHolder(
            binding.statusCard,
            PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.02f, 1.0f),
            PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.02f, 1.0f)
        ).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun stopPulseAnimation() {
        statusPulse?.cancel()
        statusPulse = null
        binding.statusCard.scaleX = 1.0f
        binding.statusCard.scaleY = 1.0f
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            startVpn()
        }
    }
}
