package com.example.shieldblock

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.shieldblock.data.StatsManager
import com.example.shieldblock.databinding.ActivityAnalyticsBinding
import com.example.shieldblock.vpn.MyVpnService
import java.util.Calendar

class AnalyticsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAnalyticsBinding
    private val statsManager by lazy { StatsManager(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var lastBytesRead = 0L

    private val monitorRunnable = object : Runnable {
        override fun run() {
            updateRealtimeStats()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation()
        loadStaticStats()

        binding.openSecurityAuditBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startActivity(Intent(this, SecurityAuditActivity::class.java))
        }

        binding.openSnifferBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startActivity(Intent(this, NetworkSnifferActivity::class.java))
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_analytics
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            binding.bottomNavigation.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_analytics -> true
                R.id.nav_apps -> {
                    startActivity(Intent(this, AppExclusionActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadStaticStats() {
        val total = statsManager.getTotalQueries()
        val blocked = statsManager.getBlockedAdsCount()
        val ratio = if (total > 0) (blocked.toFloat() / total.toFloat() * 100).toInt() else 0

        binding.blockPercentageText.text = "Protection Rate: $ratio%"
        binding.totalQueriesText.text = "Total Queries: $total"

        val hourly = statsManager.getHourlyStats()
        val maxBlocks = hourly.values.maxOrNull() ?: 1

        val peakHour = hourly.maxByOrNull { it.value }?.key ?: 0
        binding.peakHourText.text = "Peak Activity: ${String.format("%02d:00", peakHour)}"

        drawChart(hourly, maxBlocks)
        loadTopApps()
    }

    private fun updateRealtimeStats() {
        val stats = MyVpnService.instance?.getTrafficStats() ?: (0L to 0L)
        val currentRead = stats.first
        val speed = if (lastBytesRead > 0) (currentRead - lastBytesRead) / 1024 else 0
        lastBytesRead = currentRead

        binding.throughputText.text = "Live Throughput: $speed KB/s"

        val blockedCount = statsManager.getBlockedAdsCount()
        val impact = when {
            speed > 500 -> "Moderate Impact"
            blockedCount > 1000 -> "Efficient (Ads Blocked)"
            else -> "Minimal Power Impact"
        }
        binding.batteryUsageText.text = impact
    }

    private fun loadTopApps() {
        binding.topAppsContainer.removeAllViews()
        val pm = packageManager
        val apps = pm.getInstalledApplications(0)
        val stats = apps.map { it.packageName to statsManager.getAppBlockedCount(it.packageName) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(5)

        if (stats.isEmpty()) {
            val tv = TextView(this)
            tv.text = "No app tracking data detected yet."
            tv.setTextColor(getColor(R.color.on_surface_variant))
            binding.topAppsContainer.addView(tv)
        } else {
            stats.forEach { (pkg, count) ->
                val tv = TextView(this)
                val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)) } catch(e: Exception) { pkg }
                tv.text = "• $label: $count tracker requests blocked"
                tv.setTextColor(getColor(R.color.on_surface))
                tv.setPadding(0, 8, 0, 8)
                binding.topAppsContainer.addView(tv)
            }
        }
    }

    private fun drawChart(stats: Map<Int, Int>, max: Int) {
        binding.chartContainer.removeAllViews()
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        for (i in 0..23) {
            val count = stats[i] ?: 0
            val bar = View(this)
            val maxHeightPx = 200
            val height = (count.toFloat() / max.toFloat() * maxHeightPx).toInt()

            val params = LinearLayout.LayoutParams(0, maxOf(10, height * 2))
            params.weight = 1f
            params.setMargins(2, 0, 2, 0)
            bar.layoutParams = params
            bar.setBackgroundColor(if (i == currentHour) getColor(R.color.primary) else getColor(R.color.primary_container))
            binding.chartContainer.addView(bar)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(monitorRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(monitorRunnable)
    }
}
