package com.example.shieldblock.data

import android.content.Context
import androidx.preference.PreferenceManager
import java.util.Calendar

class ScheduleManager(private val context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    fun setSchedule(enabled: Boolean, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        prefs.edit().apply {
            putBoolean("schedule_enabled", enabled)
            putInt("schedule_start_hour", startHour)
            putInt("schedule_start_min", startMinute)
            putInt("schedule_end_hour", endHour)
            putInt("schedule_end_min", endMinute)
        }.apply()
    }

    fun isCurrentlyActive(): Boolean {
        if (!prefs.getBoolean("schedule_enabled", false)) return true

        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMin = now.get(Calendar.MINUTE)

        val startHour = prefs.getInt("schedule_start_hour", 0)
        val startMin = prefs.getInt("schedule_start_min", 0)
        val endHour = prefs.getInt("schedule_end_hour", 23)
        val endMin = prefs.getInt("schedule_end_min", 59)

        val currentTime = currentHour * 60 + currentMin
        val startTime = startHour * 60 + startMin
        val endTime = endHour * 60 + endMin

        return if (startTime <= endTime) {
            currentTime in startTime..endTime
        } else {
            // Overlaps midnight
            currentTime >= startTime || currentTime <= endTime
        }
    }
}
