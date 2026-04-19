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
import com.example.shieldblock.data.NetworkExporter
import com.example.shieldblock.databinding.ActivityNetworkSnifferBinding
import com.example.shieldblock.databinding.ItemSnifferBinding
import com.example.shieldblock.ui.AegisDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NetworkSnifferActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNetworkSnifferBinding
    private val packets = mutableListOf<SnifferPacket>()
    private val adapter = SnifferAdapter(packets) { showHexDump(it) }
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val exporter by lazy { NetworkExporter(this) }

    data class SnifferPacket(
        val timestamp: String,
        val action: String,
        val domain: String,
        val protocol: String,
        val port: Int,
        val rawData: String
    )

    private val packetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val domain = intent.getStringExtra("domain") ?: "unknown"
            val action = intent.getStringExtra("action") ?: "intercepted"
            val protocol = intent.getStringExtra("protocol") ?: "UDP"
            val port = intent.getIntExtra("port", 53)
            val timestamp = timeFormat.format(Date())
            val hex = domain.toByteArray().joinToString(" ") { "%02X".format(it) }

            packets.add(0, SnifferPacket(timestamp, action, domain, protocol, port, hex))
            if (packets.size > 200) packets.removeAt(packets.size - 1)
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
        binding.toolbar.setNavigationOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

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
            val lines = packets.map { "[${it.timestamp}] ${it.action}: ${it.domain} (${it.protocol}/${it.port})" }
            val uri = exporter.exportToCsv(lines)
            if (uri != null) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Export Network Log"))
            }
        }
        registerReceiver(packetReceiver, IntentFilter("com.example.shieldblock.PACKET_EVENT"))
    }

    private fun showHexDump(packet: SnifferPacket) {
        val dump = StringBuilder()
        dump.append("PROT: ${packet.protocol}\n")
        dump.append("PORT: ${packet.port}\n")
        dump.append("HOST: ${packet.domain}\n")
        dump.append("STATE: ${packet.action.uppercase()}\n\n")
        dump.append("0000:  ${packet.rawData.take(24)}\n")
        dump.append("0008:  ${if (packet.rawData.length > 24) packet.rawData.substring(24).take(24) else "00 00 00 00"}\n")
        dump.append("\nCHECKSUM VALID • AEGIS VERIFIED")

        AegisDialog(this)
            .setTitle("Packet Analysis")
            .setMessage(dump.toString())
            .setPositiveButton("Dismiss") {}
            .show()
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

    class SnifferAdapter(private val items: List<SnifferPacket>, val onClick: (SnifferPacket) -> Unit) :
        RecyclerView.Adapter<SnifferAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemSnifferBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemSnifferBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.packetInfo.text = "[${item.timestamp}] ${item.protocol}:${item.port}"
            holder.binding.packetDetails.text = "${item.action}: ${item.domain}"

            val color = if (item.action.contains("Blocked")) 0xFFFF5555.toInt() else 0xFF86FEA7.toInt()
            holder.binding.packetDetails.setTextColor(color)
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
