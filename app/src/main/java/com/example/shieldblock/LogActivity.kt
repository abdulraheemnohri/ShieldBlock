package com.example.shieldblock

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.shieldblock.databinding.ActivityLogBinding
import com.example.shieldblock.databinding.ItemLogBinding
import com.example.shieldblock.analytics.EventLogger
import com.example.shieldblock.data.WhitelistManager

class LogActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLogBinding
    private lateinit var eventLogger: EventLogger
    private val whitelistManager by lazy { WhitelistManager(this) }
    private var allLogs: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        setupBottomNavigation()
        eventLogger = EventLogger(this)

        binding.logRecyclerView.layoutManager = LinearLayoutManager(this)
        loadLogs()

        binding.refreshButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            loadLogs()
        }
        binding.exportLogsBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val logs = eventLogger.getLogs()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, logs)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.export_logs)))
        }
        binding.clearLogsButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            eventLogger.clearLogs()
            loadLogs()
        }

        binding.logSearchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filterLogs(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_analytics
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

    private fun showLogDetail(line: String) {
        val domain = if (line.contains("Blocked: ")) {
            line.substringAfter("Blocked: ").substringBefore(" (").trim()
        } else if (line.contains("Query: ")) {
            line.substringAfter("Query: ").substringBefore(" (").trim()
        } else {
            "Unknown"
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.log_detail_title)
            .setMessage(line)
            .setPositiveButton("Whitelist") { _, _ ->
                if (domain != "Unknown") {
                    whitelistManager.addToWhitelist(domain)
                    Toast.makeText(this, getString(R.string.whitelisted_toast, domain), Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Share") { _, _ ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, line)
                }
                startActivity(Intent.createChooser(intent, "Share Log"))
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun loadLogs() {
        val logsText = eventLogger.getLogs()
        allLogs = logsText.lines().filter { it.isNotBlank() }.reversed()
        filterLogs(binding.logSearchEditText.text.toString())
    }

    private fun filterLogs(query: String) {
        val filtered = if (query.isBlank()) allLogs else allLogs.filter { it.contains(query, ignoreCase = true) }
        binding.logRecyclerView.adapter = LogAdapter(filtered) { line -> showLogDetail(line) }
    }

    class LogAdapter(private val items: List<String>, val onClick: (String) -> Unit) :
        RecyclerView.Adapter<LogAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val timestamp = item.substringBefore("] ").replace("[", "")
            val content = item.substringAfter("] ")

            holder.binding.logTimestamp.text = timestamp
            holder.binding.logContent.text = content

            if (item.contains("[") && item.contains("ms]")) {
                val latency = item.substringAfter("[").substringBefore("]")
                holder.binding.logLatency.text = "Latency: $latency"
                holder.binding.logLatency.visibility = View.VISIBLE
            } else {
                holder.binding.logLatency.visibility = View.GONE
            }

            val color = when {
                content.startsWith("Blocked") -> holder.itemView.context.getColor(R.color.tertiary)
                content.startsWith("Allowed") || content.contains("ALLOWED") -> holder.itemView.context.getColor(R.color.primary)
                else -> holder.itemView.context.getColor(R.color.on_surface)
            }
            holder.binding.logContent.setTextColor(color)

            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
