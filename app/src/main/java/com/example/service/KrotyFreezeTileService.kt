package com.example.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.data.local.KrotyDatabase
import com.example.domain.network.CaptivePortalProber
import com.example.domain.network.WifiRadarEngine
import com.example.widget.KrotySmartWidgetProvider
import com.example.worker.KrotyNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class KrotyFreezeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTileState()
    }

    override fun onClick() {
        super.onClick()
        CoroutineScope(Dispatchers.IO).launch {
            val dao = KrotyDatabase.getInstance(applicationContext).krotyDao()
            val radar = WifiRadarEngine(applicationContext)
            val prober = CaptivePortalProber(radar)
            val wifi = radar.refreshNow()

            val active = dao.getActiveVoucherNow()
            if (active != null) {
                if (wifi.gatewayIp.isNotBlank()) {
                    prober.sendLogoutRequest("http://${wifi.gatewayIp}/logout")
                }
                dao.upsertVoucher(active.copy(status = "PAUSED", lastSyncedAt = System.currentTimeMillis()))
                KrotyNotificationHelper.showNotification(
                    applicationContext,
                    3001,
                    "❄️ تم تجميد الكرت بنجاح",
                    "تم تسجيل الخروج المؤقت من البوابة (${wifi.gatewayIp}) لحفظ وقت ورصيد كرتك."
                )
            } else {
                val paused = dao.getPausedVoucherNow()
                if (paused != null) {
                    dao.upsertVoucher(paused.copy(status = "ACTIVE", lastSyncedAt = System.currentTimeMillis()))
                    KrotyNotificationHelper.showNotification(
                        applicationContext,
                        3002,
                        "▶️ تم تفعيل الكرت",
                        "افتح كروتي نت لإتمام الدخول التلقائي في البوابة."
                    )
                }
            }
            KrotySmartWidgetProvider.updateAllWidgets(applicationContext)
            refreshTileState()
        }
    }

    private fun refreshTileState() {
        CoroutineScope(Dispatchers.IO).launch {
            val dao = KrotyDatabase.getInstance(applicationContext).krotyDao()
            val active = dao.getActiveVoucherNow()
            val paused = dao.getPausedVoucherNow()
            val tile = qsTile ?: return@launch

            if (active != null) {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "تجميد الكرت ❄️"
            } else if (paused != null) {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "استئناف الكرت ▶️"
            } else {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "كروتي نت"
            }
            tile.updateTile()
        }
    }
}
