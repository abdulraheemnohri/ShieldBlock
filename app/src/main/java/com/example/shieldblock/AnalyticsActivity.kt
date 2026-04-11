package com.example.shieldblock

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.shieldblock.data.StatsManager
import com.example.shieldblock.databinding.ActivityAnalyticsBinding
import java.util.Calendar

class AnalyticsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAnalyticsBinding
    private val statsManager by lazy { StatsManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        loadStats()
    }

    private fun loadStats() {
        val total = statsManager.getTotalQueries()
        val blocked = statsManager.getBlockedAdsCount()
        val ratio = if (total > 0) (blocked.toFloat() / total.toFloat() * 100).toInt() else 0

        binding.totalQueriesText.text = "Total Queries: $total"
        binding.blockPercentageText.text = "Blocked Ratio: $ratio%"

        val hourly = statsManager.getHourlyStats()
        val maxBlocks = hourly.values.maxOrNull() ?: 0
        val peakHour = hourly.maxByOrNull { it.value }?.key ?: 0

        binding.peakHourText.text = "Peak Activity: ${String.format("%02d:00", peakHour)}"

        drawChart(hourly, maxBlocks)
    }

    private fun drawChart(stats: Map<Int, Int>, max: Int) {
        binding.chartContainer.removeAllViews()
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        for (i in 0..23) {
            val count = stats[i] ?: 0
            val bar = View(this)
            val maxHeightPx = 200 // Base height units
            val height = if (max > 0) (count.toFloat() / max.toFloat() * maxHeightPx).toInt() else 2

            val params = LinearLayout.LayoutParams(0, maxOf(10, height * 4))
            params.weight = 1f
            params.setMargins(4, 0, 4, 0)
            bar.layoutParams = params

            val color = if (i == currentHour) {
                getColor(R.color.primary)
            } else {
                getColor(R.color.primary_container)
            }
            bar.setBackgroundColor(color)
            bar.alpha = if (count > 0) 1.0f else 0.3f

            binding.chartContainer.addView(bar)
        }
    }
}
