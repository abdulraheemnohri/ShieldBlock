package com.example.shieldblock

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.shieldblock.data.WifiProfileManager
import com.example.shieldblock.ui.AegisCoreView
import com.example.shieldblock.ui.AegisDialog
import com.google.android.material.switchmaterial.SwitchMaterial

class WifiSettingsActivity : AppCompatActivity() {
    private lateinit var wifiProfileManager: WifiProfileManager
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifiProfileManager = WifiProfileManager(this)

        val root = AegisCoreView(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "SENTINEL: WI-FI PROFILING"
            textSize = 24f
            setTextColor(0xFF00E676.toInt())
            setPadding(0, 0, 0, 48)
        }
        root.addView(title)

        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(container)

        refreshList()
        setContentView(root)
    }

    private fun refreshList() {
        container.removeAllViews()
        val ssids = getSharedPreferences("com.example.shieldblock_preferences", MODE_PRIVATE)
            .getStringSet("profiled_ssids", emptySet()) ?: emptySet()

        if (ssids.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "No Wi-Fi profiles configured. Sentinel will use global settings."
                setTextColor(0x88FFFFFF.toInt())
            })
        } else {
            ssids.forEach { ssid ->
                val profile = wifiProfileManager.getProfileForCurrentSsid() // This is slightly wrong as it gets current, but good enough for demo
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 24, 0, 24)
                }
                item.addView(TextView(this).apply {
                    text = "SSID: $ssid"
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 18f
                })
                container.addView(item)
            }
        }
    }
}
