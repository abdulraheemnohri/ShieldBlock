package com.example.shieldblock

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
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

    private val updateStatsRunnable = object : Runnable {
        override fun run() {
            updateStats()
            updateNetworkInfo()
            handler.postDelayed(this, 5000)
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

        binding.startVpnButton.setOnClickListener { startVpn() }
        binding.stopVpnButton.setOnClickListener { stopVpn() }
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.shareButton.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Secure your connection with ShieldBlock! Blocking ads and trackers system-wide.")
            }
            startActivity(Intent.createChooser(shareIntent, "Share ShieldBlock"))
        }

        val blacklistWork = PeriodicWorkRequestBuilder<BlacklistWorker>(
            24, TimeUnit.HOURS
        ).build()
        WorkManager.getInstance(this).enqueue(blacklistWork)

        runEntranceAnimations()
    }

    private fun runEntranceAnimations() {
        val views = listOf(
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

        updateTopBlocked()
    }

    private fun updateNetworkInfo() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)
        val info = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Connected to WiFi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Connected to Mobile Data"
            else -> "Offline or Unknown Network"
        }
        binding.networkInfoText.text = info
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
