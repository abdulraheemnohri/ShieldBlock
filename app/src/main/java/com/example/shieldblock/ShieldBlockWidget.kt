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
        if (intent.action == "com.example.shieldblock.TOGGLE_VPN") {
            val isRunning = VpnService.prepare(context) == null
            val action = if (isRunning) "stop" else "start"

            val vpnIntent = VpnService.prepare(context)
            if (vpnIntent == null) {
                val serviceIntent = Intent(context, MyVpnService::class.java).apply {
                    putExtra("action", action)
                }
                if (action == "start") {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            }
        }

        // Always refresh UI on any relevant broadcast
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, ShieldBlockWidget::class.java)
        val ids = appWidgetManager.getAppWidgetIds(componentName)
        onUpdate(context, appWidgetManager, ids)
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val statsManager = StatsManager(context)
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            views.setTextViewText(R.id.widgetBlockedCount, statsManager.getBlockedAdsCount().toString())

            val isRunning = VpnService.prepare(context) == null
            views.setTextViewText(R.id.widgetStatusText, if (isRunning) "ON" else "OFF")
            views.setTextColor(R.id.widgetStatusText, context.getColor(if (isRunning) R.color.primary else R.color.tertiary))

            val intent = Intent(context, ShieldBlockWidget::class.java).apply {
                action = "com.example.shieldblock.TOGGLE_VPN"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetToggleButton, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
