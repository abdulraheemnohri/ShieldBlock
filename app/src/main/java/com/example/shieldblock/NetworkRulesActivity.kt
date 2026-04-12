package com.example.shieldblock

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.shieldblock.databinding.ActivityNetworkRulesBinding
import com.example.shieldblock.databinding.ItemWifiBinding

class NetworkRulesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNetworkRulesBinding
    private val excludedWifiKey = "excluded_wifi_ssids"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNetworkRulesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupBottomNavigation()
        binding.wifiRecyclerView.layoutManager = LinearLayoutManager(this)
        refreshList()

        binding.addCurrentWifiBtn.setOnClickListener {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wm.connectionInfo
            val ssid = info.ssid.replace("\"", "")
            if (ssid != "<unknown ssid>" && ssid.isNotEmpty()) {
                addSsid(ssid)
            } else {
                Toast.makeText(this, "Connect to Wi-Fi first", Toast.LENGTH_SHORT).show()
            }
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

    private fun refreshList() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val ssids = prefs.getStringSet(excludedWifiKey, emptySet())?.toList()?.sorted() ?: emptyList()
        binding.wifiRecyclerView.adapter = WifiAdapter(ssids) { ssid ->
            removeSsid(ssid)
        }
    }

    private fun addSsid(ssid: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getStringSet(excludedWifiKey, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(ssid)
        prefs.edit().putStringSet(excludedWifiKey, current).apply()
        refreshList()
        Toast.makeText(this, "$ssid excluded", Toast.LENGTH_SHORT).show()
    }

    private fun removeSsid(ssid: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getStringSet(excludedWifiKey, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.remove(ssid)
        prefs.edit().putStringSet(excludedWifiKey, current).apply()
        refreshList()
    }

    class WifiAdapter(private val items: List<String>, val onRemove: (String) -> Unit) :
        RecyclerView.Adapter<WifiAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemWifiBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemWifiBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val ssid = items[position]
            holder.binding.ssidText.text = ssid
            holder.binding.removeBtn.setOnClickListener { onRemove(ssid) }
        }

        override fun getItemCount() = items.size
    }
}
