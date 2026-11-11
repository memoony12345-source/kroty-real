package com.example.di

import android.content.Context
import com.example.data.local.KrotyDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.KrotyRepository
import com.example.domain.radar.WifiRadarEngine
import com.example.domain.scanner.MultiFrameConsensusTracker
import com.example.domain.scanner.SmartVoucherOcrParser
import com.example.domain.system.KrotyNotificationHelper
import com.example.domain.wallet.BackupRestoreManager
import com.example.domain.wallet.VoucherQrGenerator
import com.example.domain.web.BalanceAndErrorParser
import com.example.domain.web.CaptivePortalProber

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: KrotyDatabase by lazy { KrotyDatabase.getInstance(appContext) }
    val repository: KrotyRepository by lazy { KrotyRepository(database.krotyDao()) }
    val preferencesRepository: UserPreferencesRepository by lazy { UserPreferencesRepository(appContext) }

    val wifiRadarEngine: WifiRadarEngine by lazy { WifiRadarEngine(appContext) }
    val balanceParser: BalanceAndErrorParser by lazy { BalanceAndErrorParser() }
    val captivePortalProber: CaptivePortalProber by lazy {
        CaptivePortalProber(wifiRadarEngine, balanceParser)
    }

    val ocrParser: SmartVoucherOcrParser by lazy { SmartVoucherOcrParser() }
    val consensusTracker: MultiFrameConsensusTracker by lazy { MultiFrameConsensusTracker(requiredMatches = 2) }

    val qrGenerator: VoucherQrGenerator by lazy { VoucherQrGenerator() }
    val backupRestoreManager: BackupRestoreManager by lazy {
        BackupRestoreManager(appContext, database.krotyDao())
    }
    val notificationHelper: KrotyNotificationHelper by lazy {
        KrotyNotificationHelper(appContext)
    }
}
