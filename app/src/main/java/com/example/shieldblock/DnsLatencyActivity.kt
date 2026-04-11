package com.example.shieldblock

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.shieldblock.databinding.ActivityDnsLatencyBinding
import com.example.shieldblock.databinding.ItemDnsLatencyBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import kotlin.system.measureTimeMillis

data class DnsProvider(val name: String, val ip: String, var latency: Long = -1)

class DnsLatencyActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDnsLatencyBinding
    private val providers = mutableListOf(
        DnsProvider("Google DNS", "8.8.8.8"),
        DnsProvider("Cloudflare", "1.1.1.1"),
        DnsProvider("AdGuard", "94.140.14.14"),
        DnsProvider("OpenDNS", "208.67.222.222"),
        DnsProvider("Quad9", "9.9.9.9")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDnsLatencyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupBottomNavigation()
        binding.latencyRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.latencyRecyclerView.adapter = LatencyAdapter(providers)

        binding.startTestButton.setOnClickListener { runBenchmark() }

        binding.pingBtn.setOnClickListener {
            val host = binding.manualLookupInput.text.toString().ifBlank { "google.com" }
            runManualTest("PING", host)
        }

        binding.lookupBtn.setOnClickListener {
            val host = binding.manualLookupInput.text.toString().ifBlank { "google.com" }
            runManualTest("LOOKUP", host)
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_settings
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

    private fun runManualTest(type: String, host: String) {
        binding.manualResultText.text = "Testing $host..."
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    if (type == "PING") {
                        val start = System.currentTimeMillis()
                        val reachable = InetAddress.getByName(host).isReachable(3000)
                        if (reachable) "Reply from $host: ${System.currentTimeMillis() - start}ms" else "Request timed out"
                    } else {
                        val addr = InetAddress.getByName(host)
                        "Resolved $host to ${addr.hostAddress}"
                    }
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
            }
            binding.manualResultText.text = result
        }
    }

    private fun runBenchmark() {
        binding.startTestButton.isEnabled = false
        lifecycleScope.launch {
            providers.forEachIndexed { index, provider ->
                val time = withContext(Dispatchers.IO) {
                    try {
                        val start = System.currentTimeMillis()
                        val address = InetAddress.getByName(provider.ip)
                        if (address.isReachable(2000)) {
                            System.currentTimeMillis() - start
                        } else {
                            -1L
                        }
                    } catch (e: Exception) {
                        -1L
                    }
                }
                provider.latency = time
                binding.latencyRecyclerView.adapter?.notifyItemChanged(index)
            }
            binding.startTestButton.isEnabled = true
        }
    }

    class LatencyAdapter(private val items: List<DnsProvider>) :
        RecyclerView.Adapter<LatencyAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemDnsLatencyBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemDnsLatencyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.providerName.text = item.name
            holder.binding.providerIp.text = item.ip
            holder.binding.latencyValue.text = if (item.latency >= 0) "${item.latency} ms" else "Timeout"
            holder.binding.latencyValue.setTextColor(
                if (item.latency in 0..100) holder.itemView.context.getColor(R.color.primary)
                else holder.itemView.context.getColor(R.color.tertiary)
            )
        }

        override fun getItemCount() = items.size
    }
}
