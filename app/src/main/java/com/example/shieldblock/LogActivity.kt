package com.example.shieldblock

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.example.shieldblock.databinding.ActivityLogBinding
import com.example.shieldblock.analytics.EventLogger

class LogActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLogBinding
    private lateinit var eventLogger: EventLogger
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
