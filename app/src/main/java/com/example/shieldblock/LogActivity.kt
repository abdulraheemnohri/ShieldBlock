package com.example.shieldblock

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.example.shieldblock.databinding.ActivityLogBinding
import com.example.shieldblock.analytics.EventLogger

class LogActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLogBinding
    private lateinit var eventLogger: EventLogger

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
    }

    private fun loadLogs() {
        val logsText = eventLogger.getLogs()
        val logsList = logsText.lines().filter { it.isNotBlank() }.reversed()
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, logsList)
        binding.logListView.adapter = adapter
    }
}
