package com.example.shieldblock

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
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

        eventLogger = EventLogger(this)

        loadLogs()

        binding.refreshButton.setOnClickListener {
            loadLogs()
        }

        binding.clearLogsButton.setOnClickListener {
            eventLogger.clearLogs()
            loadLogs()
        }

        binding.logSearchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterLogs(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.logListView.setOnItemClickListener { _, _, position, _ ->
            val logLine = binding.logListView.adapter.getItem(position) as String
            showLogDetail(logLine)
        }
    }

    private fun showLogDetail(line: String) {
        // Extract domain from log line [timestamp] Blocked: domain.com
        val domain = if (line.contains("Blocked: ")) {
            line.substringAfter("Blocked: ").trim()
        } else if (line.contains("Query: ")) {
            line.substringAfter("Query: ").trim()
        } else {
            "Unknown"
        }

        AlertDialog.Builder(this)
            .setTitle("Log Detail")
            .setMessage("Full Log:\n$line")
            .setPositiveButton("Whitelist") { _, _ ->
                if (domain != "Unknown") {
                    whitelistManager.addToWhitelist(domain)
                    Toast.makeText(this, "$domain whitelisted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun loadLogs() {
        val logsText = eventLogger.getLogs()
        allLogs = logsText.lines().filter { it.isNotBlank() }.reversed()
        filterLogs(binding.logSearchEditText.text.toString())
    }

    private fun filterLogs(query: String) {
        val filtered = if (query.isBlank()) {
            allLogs
        } else {
            allLogs.filter { it.contains(query, ignoreCase = true) }
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, filtered)
        binding.logListView.adapter = adapter
    }
}
