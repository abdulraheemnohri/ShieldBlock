package com.example.shieldblock

import android.content.Intent
import android.net.VpnService
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.shieldblock.vpn.MyVpnService

class ShieldBlockTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val isRunning = VpnService.prepare(this) == null
        val action = if (isRunning) "stop" else "start"

        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent == null) {
            val serviceIntent = Intent(this, MyVpnService::class.java).apply {
                putExtra("action", action)
            }
            if (action == "start") {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            updateTile()
        } else {
            // Need permission, open app
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val isRunning = VpnService.prepare(this) == null
        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        tile.updateTile()
    }
}
