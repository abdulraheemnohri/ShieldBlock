package com.example.shieldblock

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.shieldblock.data.FirewallManager
import com.example.shieldblock.databinding.ActivityFirewallBinding
import com.example.shieldblock.databinding.ItemFirewallIpBinding

class FirewallActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFirewallBinding
    private val firewallManager by lazy { FirewallManager(this) }
    private lateinit var adapter: FirewallAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFirewallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupBottomNavigation()
        setupList()

        binding.blockIpBtn.setOnClickListener {
            val ip = binding.ipInputEditText.text.toString().trim()
            if (ip.isNotEmpty()) {
                firewallManager.addBlockedIp(ip)
                binding.ipInputEditText.setText("")
                refreshList()
                Toast.makeText(this, "IP Address Mitigated", Toast.LENGTH_SHORT).show()
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_settings
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { startActivity(Intent(this, MainActivity::class.java)); true }
                R.id.nav_analytics -> { startActivity(Intent(this, AnalyticsActivity::class.java)); true }
                R.id.nav_apps -> { startActivity(Intent(this, AppExclusionActivity::class.java)); true }
                R.id.nav_settings -> { finish(); true }
                else -> false
            }
        }
    }

    private fun setupList() {
        adapter = FirewallAdapter(firewallManager.getBlockedIps().toList()) { ip ->
            firewallManager.removeBlockedIp(ip)
            refreshList()
        }
        binding.firewallRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.firewallRecyclerView.adapter = adapter
    }

    private fun refreshList() {
        adapter.update(firewallManager.getBlockedIps().toList())
    }

    class FirewallAdapter(private var ips: List<String>, val onDelete: (String) -> Unit) :
        RecyclerView.Adapter<FirewallAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemFirewallIpBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemFirewallIpBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val ip = ips[position]
            holder.binding.ipText.text = ip
            holder.binding.deleteIpBtn.setOnClickListener { onDelete(ip) }
        }

        override fun getItemCount() = ips.size

        fun update(newIps: List<String>) {
            ips = newIps
            notifyDataSetChanged()
        }
    }
}
