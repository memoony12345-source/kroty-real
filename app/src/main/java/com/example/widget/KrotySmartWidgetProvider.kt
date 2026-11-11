package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.KrotyDatabase
import com.example.domain.network.CaptivePortalProber
import com.example.domain.network.WifiRadarEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class KrotySmartWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAllWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH_BALANCE -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val radar = WifiRadarEngine(context)
                    val prober = CaptivePortalProber(radar)
                    val probe = prober.probeCurrentNetwork()
                    val db = KrotyDatabase.getInstance(context).krotyDao()
                    val active = db.getActiveVoucherNow()
                    if (probe.liveStatus != null && active != null) {
                        db.upsertVoucher(
                            active.copy(
                                remainingMegabytes = probe.liveStatus.remainingMegabytes ?: active.remainingMegabytes,
                                remainingTimeSeconds = probe.liveStatus.remainingTimeSeconds ?: active.remainingTimeSeconds,
                                lastSyncedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    updateAllWidgets(context)
                }
            }
            ACTION_TOGGLE_FREEZE -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val db = KrotyDatabase.getInstance(context).krotyDao()
                    val radar = WifiRadarEngine(context)
                    val prober = CaptivePortalProber(radar)
                    val wifi = radar.refreshNow()
                    val active = db.getActiveVoucherNow()
                    if (active != null && wifi.gatewayIp.isNotBlank()) {
                        prober.sendLogoutRequest("http://${wifi.gatewayIp}/logout")
                        db.upsertVoucher(active.copy(status = "PAUSED"))
                    } else {
                        val paused = db.getPausedVoucherNow()
                        if (paused != null) {
                            db.upsertVoucher(paused.copy(status = "ACTIVE"))
                        }
                    }
                    updateAllWidgets(context)
                }
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_BALANCE = "com.example.widget.ACTION_REFRESH_BALANCE"
        const val ACTION_TOGGLE_FREEZE = "com.example.widget.ACTION_TOGGLE_FREEZE"

        fun updateAllWidgets(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val manager = AppWidgetManager.getInstance(context)
                    val ids = manager.getAppWidgetIds(ComponentName(context, KrotySmartWidgetProvider::class.java))
                    if (ids.isEmpty()) return@launch

                    val db = KrotyDatabase.getInstance(context).krotyDao()
                    val radar = WifiRadarEngine(context)
                    val wifi = radar.refreshNow()
                    val active = db.getActiveVoucherNow() ?: db.getPausedVoucherNow()

                    val views = RemoteViews(context.packageName, R.layout.widget_kroty_smart)
                    views.setTextViewText(R.id.widget_network_name, wifi.ssid)
                    views.setTextViewText(
                        R.id.widget_status_badge,
                        if (wifi.isConnectedToWifi) "متصل (${wifi.gatewayIp})" else "غير متصل"
                    )

                    if (active != null) {
                        val masked = if (active.code.length > 4) "${active.code.take(2)}****${active.code.takeLast(2)}" else active.code
                        views.setTextViewText(R.id.widget_card_info, "الكرت: $masked (${if (active.status == "PAUSED") "مجمد ❄️" else "نشط ✅"})")
                        val mbText = active.remainingMegabytes?.let { String.format("%.1f MB", it) } ?: "اضغط تحديث"
                        views.setTextViewText(R.id.widget_balance_text, "الرصيد المتبقي: $mbText")
                    } else {
                        views.setTextViewText(R.id.widget_card_info, "الكرت النشط: لا يوجد كرت مسجل")
                        views.setTextViewText(R.id.widget_balance_text, "افتح التطبيق أو امسح كرتاً جديداً")
                    }

                    val refreshIntent = Intent(context, KrotySmartWidgetProvider::class.java).apply { action = ACTION_REFRESH_BALANCE }
                    views.setOnClickPendingIntent(
                        R.id.widget_btn_refresh,
                        PendingIntent.getBroadcast(context, 101, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    )

                    val freezeIntent = Intent(context, KrotySmartWidgetProvider::class.java).apply { action = ACTION_TOGGLE_FREEZE }
                    views.setOnClickPendingIntent(
                        R.id.widget_btn_freeze,
                        PendingIntent.getBroadcast(context, 102, freezeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    )

                    val appIntent = Intent(context, MainActivity::class.java)
                    views.setOnClickPendingIntent(
                        R.id.widget_btn_scan,
                        PendingIntent.getActivity(context, 103, appIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    )

                    for (id in ids) {
                        manager.updateAppWidget(id, views)
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
