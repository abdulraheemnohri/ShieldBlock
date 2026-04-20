package com.example.shieldblock

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.shieldblock.data.ScheduleManager
import com.example.shieldblock.databinding.ActivitySentinelScheduleBinding

class SentinelScheduleActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySentinelScheduleBinding
    private val scheduleManager by lazy { ScheduleManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySentinelScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.saveScheduleBtn.setOnClickListener {
            scheduleManager.setSchedule(
                enabled = binding.scheduleSwitch.isChecked,
                startHour = binding.startTimePicker.hour,
                startMinute = binding.startTimePicker.minute,
                endHour = binding.endTimePicker.hour,
                endMinute = binding.endTimePicker.minute
            )
            Toast.makeText(this, "Sentinel Schedule Synchronized", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
