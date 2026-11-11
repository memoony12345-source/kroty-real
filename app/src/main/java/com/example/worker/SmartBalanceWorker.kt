package com.example.worker

import android.content.Context
import androidx.work.*
import com.example.data.local.KrotyDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.KrotyRepository
import com.example.domain.network.CaptivePortalProber
import com.example.domain.network.WifiRadarEngine
import com.example.widget.KrotySmartWidgetProvider
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class SmartBalanceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = KrotyDatabase.getInstance(applicationContext)
            val repo = KrotyRepository(db.krotyDao())
            val prefs = UserPreferencesRepository(applicationContext)
            val radar = WifiRadarEngine(applicationContext)
            val prober = CaptivePortalProber(radar)

            val wifi = radar.refreshNow()
            if (!wifi.isConnectedToWifi || wifi.gatewayIp.isBlank()) {
                return Result.success()
            }

            val probe = prober.probeCurrentNetwork()
            val live = probe.liveStatus
            if (live != null && (live.remainingMegabytes != null || live.activeUsername != null)) {
                repo.syncLivePortalSession(
                    detectedUsername = live.activeUsername,
                    remainingMb = live.remainingMegabytes,
                    remainingSec = live.remainingTimeSeconds,
                    networkSsid = wifi.ssid
                )
                val alertLimit = prefs.lowBalanceAlertMb.first()
                val remMb = live.remainingMegabytes
                if (remMb != null && remMb <= alertLimit.toDouble()) {
                    KrotyNotificationHelper.showNotification(
                        applicationContext,
                        2001,
                        "⚠️ تنبيه انخفاض رصيد الكرت",
                        "الرصيد المتبقي في شبكتك (${wifi.ssid}) هو ${String.format("%.1f", remMb)} ميجابايت فقط."
                    )
                }
            }
            KrotySmartWidgetProvider.updateAllWidgets(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<SmartBalanceWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "kroty_periodic_balance_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }

        fun runOnceNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<SmartBalanceWorker>().build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }
}
