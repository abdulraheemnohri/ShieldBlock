package com.example.shieldblock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.widget.RemoteViews
import com.example.shieldblock.data.StatsManager
import com.example.shieldblock.vpn.MyVpnService

class ShieldBlockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "TOGGLE_VPN") {
            val vpnIntent = VpnService.prepare(context)
            if (vpnIntent == null) {
                // If already prepared (or no permission needed), we toggle.
                // In a real app, if vpnIntent != null, we might need to open the app.
                val isRunning = isVpnServiceRunning(context)
                val action = if (isRunning) "stop" else "start"
                val serviceIntent = Intent(context, MyVpnService::class.java).apply {
                    putExtra("action", action)
                }
                if (!isRunning) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                // Need permission, open app
                val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            }

            // Trigger UI update
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, ShieldBlockWidget::class.java)
            onUpdate(context, appWidgetManager, appWidgetManager.getAppWidgetIds(componentName))
        }
    }

    private fun isVpnServiceRunning(context: Context): Boolean {
        // Simplified check. In production, use a more robust check if needed.
        return VpnService.prepare(context) == null && MyVpnService::class.java != null // This is dummy check
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val statsManager = StatsManager(context)
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            views.setTextViewText(R.id.widgetBlockedCount, statsManager.getBlockedAdsCount().toString())

            // Check if VPN is running (simplified)
            // Note: Determining if VPN is active from a widget can be tricky without a shared state or singleton.
            // For now, we'll assume "ON" if we can't prepare.
            val isRunning = VpnService.prepare(context) == null
            views.setTextViewText(R.id.widgetStatusText, if (isRunning) "ON" else "OFF")
            views.setTextColor(R.id.widgetStatusText, context.getColor(if (isRunning) R.color.primary else R.color.tertiary))

            val intent = Intent(context, ShieldBlockWidget::class.java).apply {
                action = "TOGGLE_VPN"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetToggleButton, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
