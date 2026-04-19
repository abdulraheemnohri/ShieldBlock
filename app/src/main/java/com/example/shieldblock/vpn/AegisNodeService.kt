package com.example.shieldblock.vpn

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.TextView
import com.example.shieldblock.R
import com.example.shieldblock.data.StatsManager

class AegisNodeService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var nodeView: View
    private lateinit var countText: TextView
    private val statsManager by lazy { StatsManager(this) }

    private val packetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.getStringExtra("action") ?: ""
            if (action.contains("Blocked")) {
                updateCount()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        nodeView = LayoutInflater.from(this).inflate(R.layout.layout_aegis_node, null)
        countText = nodeView.findViewById(R.id.nodeCountText)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        nodeView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(nodeView, params)
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(nodeView, params)
        registerReceiver(packetReceiver, IntentFilter("com.example.shieldblock.PACKET_EVENT"))
        updateCount()
    }

    private fun updateCount() {
        countText.text = statsManager.getBlockedAdsCount().toString()
    }

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(nodeView)
        unregisterReceiver(packetReceiver)
    }
}
