package com.example.shieldblock

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.shieldblock.databinding.ActivityLogBinding
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
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupBottomNavigation()
        eventLogger = EventLogger(this)
        loadLogs()

        binding.refreshButton.setOnClickListener { loadLogs() }
        binding.clearLogsButton.setOnClickListener {
            eventLogger.clearLogs()
            loadLogs()
        }

        binding.logSearchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filterLogs(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.logListView.setOnItemClickListener { _, _, position, _ ->
            val logLine = binding.logListView.adapter.getItem(position) as String
            showLogDetail(logLine)
        }

        binding.logListView.setOnItemLongClickListener { _, _, position, _ ->
            val logLine = binding.logListView.adapter.getItem(position) as String
            shareLog(logLine)
            true
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_analytics
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

    private fun shareLog(line: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, line)
        }
        startActivity(Intent.createChooser(intent, "Share Log Entry"))
    }

    private fun showLogDetail(line: String) {
        val domain = if (line.contains("Blocked: ")) {
            line.substringAfter("Blocked: ").trim()
        } else if (line.contains("Query: ")) {
            line.substringAfter("Query: ").trim()
        } else {
            "Unknown"
        }

        AlertDialog.Builder(this)
            .setTitle("Service Log Detail")
            .setMessage(line)
            .setPositiveButton("Whitelist") { _, _ ->
                if (domain != "Unknown") {
                    whitelistManager.addToWhitelist(domain)
                    Toast.makeText(this, "$domain whitelisted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Share") { _, _ -> shareLog(line) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun loadLogs() {
        val logsText = eventLogger.getLogs()
        allLogs = logsText.lines().filter { it.isNotBlank() }.reversed()
        filterLogs(binding.logSearchEditText.text.toString())
    }

    private fun filterLogs(query: String) {
        val filtered = if (query.isBlank()) allLogs else allLogs.filter { it.contains(query, ignoreCase = true) }
        binding.logListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, filtered)
    }
}
