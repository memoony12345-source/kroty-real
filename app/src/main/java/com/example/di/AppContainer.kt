package com.example.di

import android.content.Context
import com.example.data.local.KrotyDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.KrotyRepository
import com.example.domain.network.CaptivePortalProber
import com.example.domain.network.WifiRadarEngine

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: KrotyDatabase by lazy { KrotyDatabase.getInstance(appContext) }
    val repository: KrotyRepository by lazy { KrotyRepository(database.krotyDao()) }
    val preferences: UserPreferencesRepository by lazy { UserPreferencesRepository(appContext) }
    val wifiRadarEngine: WifiRadarEngine by lazy { WifiRadarEngine(appContext) }
    val portalProber: CaptivePortalProber by lazy { CaptivePortalProber(wifiRadarEngine) }
}
