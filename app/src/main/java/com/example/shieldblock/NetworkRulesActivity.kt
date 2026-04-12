package com.example.shieldblock

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.shieldblock.databinding.ActivityNetworkRulesBinding
import com.example.shieldblock.databinding.ItemWifiBinding

class NetworkRulesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNetworkRulesBinding
    private val wifiKey = "excluded_wifi_ssids"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNetworkRulesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        setupBottomNavigation()
        binding.wifiRecyclerView.layoutManager = LinearLayoutManager(this)
        refreshList()

        binding.addCurrentWifiBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            addCurrentWifi()
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_settings
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

    private fun addCurrentWifi() {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = try { wm.connectionInfo } catch(e: Exception) { null }
        val ssid = info?.ssid?.replace("\"", "") ?: ""

        if (ssid.isEmpty() || ssid == "<unknown ssid>") {
            Toast.makeText(this, "No active WiFi detected", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getStringSet(wifiKey, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (current.add(ssid)) {
            prefs.edit().putStringSet(wifiKey, current).apply()
            refreshList()
            Toast.makeText(this, "Added $ssid to trusted networks", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshList() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val ssids = prefs.getStringSet(wifiKey, emptySet())?.toList()?.sorted() ?: emptyList()
        binding.wifiRecyclerView.adapter = WifiAdapter(ssids) { ssid ->
            AlertDialog.Builder(this)
                .setTitle("Untrust network?")
                .setMessage("Remove $ssid from trusted networks?")
                .setPositiveButton("Remove") { _, _ ->
                    val current = prefs.getStringSet(wifiKey, emptySet())?.toMutableSet() ?: mutableSetOf()
                    current.remove(ssid)
                    prefs.edit().putStringSet(wifiKey, current).apply()
                    refreshList()
                }.setNegativeButton("Cancel", null).show()
        }
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
            holder.binding.removeWifiBtn.setOnClickListener { onRemove(ssid) }
        }

        override fun getItemCount() = items.size
    }
}
