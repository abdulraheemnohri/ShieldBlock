package com.example.shieldblock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.shieldblock.databinding.ActivityNetworkSnifferBinding
import com.example.shieldblock.databinding.ItemSnifferBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NetworkSnifferActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNetworkSnifferBinding
    private val packets = mutableListOf<String>()
    private val adapter = SnifferAdapter(packets)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val packetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val domain = intent.getStringExtra("domain") ?: "unknown"
            val action = intent.getStringExtra("action") ?: "intercepted"
            val timestamp = timeFormat.format(Date())
            packets.add(0, "[$timestamp] $action: $domain")
            if (packets.size > 100) packets.removeAt(packets.size - 1)
            adapter.notifyItemInserted(0)
            binding.snifferRecyclerView.scrollToPosition(0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNetworkSnifferBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupBottomNavigation()
        binding.snifferRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.snifferRecyclerView.adapter = adapter

        binding.clearSnifferBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            packets.clear()
            adapter.notifyDataSetChanged()
        }
        binding.exportSnifferBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val text = packets.joinToString("\n")
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(intent, "Export Sniffer Data"))
        }
        registerReceiver(packetReceiver, IntentFilter("com.example.shieldblock.PACKET_EVENT"))
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_analytics
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            binding.bottomNavigation.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            when (item.itemId) {
                R.id.nav_home -> { startActivity(Intent(this, MainActivity::class.java)); overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right); finish(); true }
                R.id.nav_analytics -> { startActivity(Intent(this, AnalyticsActivity::class.java)); overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right); finish(); true }
                R.id.nav_apps -> { startActivity(Intent(this, AppExclusionActivity::class.java)); overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left); finish(); true }
                R.id.nav_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left); finish(); true }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(packetReceiver) } catch(e: Exception) {}
    }

    class SnifferAdapter(private val items: List<String>) :
        RecyclerView.Adapter<SnifferAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemSnifferBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemSnifferBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.packetInfo.text = item.substringBefore(": ")
            holder.binding.packetDetails.text = item.substringAfter(": ")

            val action = item.substringAfter("] ").substringBefore(":")
            val color = when {
                action.contains("Blocked") -> holder.itemView.context.getColor(R.color.tertiary)
                action.contains("White") -> holder.itemView.context.getColor(R.color.secondary)
                else -> holder.itemView.context.getColor(R.color.primary)
            }
            holder.binding.packetInfo.setTextColor(color)
        }

        override fun getItemCount() = items.size
    }
}
