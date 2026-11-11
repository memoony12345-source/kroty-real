package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "kroty_prefs")

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        val GHOST_MODE = booleanPreferencesKey("ghost_mode")
        val DATA_SAVER = booleanPreferencesKey("data_saver")
        val LOW_BALANCE_MB = intPreferencesKey("low_balance_mb")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val ghostModeEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.GHOST_MODE] ?: true }
    val dataSaverEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.DATA_SAVER] ?: true }
    val lowBalanceAlertMb: Flow<Int> = context.dataStore.data.map { it[Keys.LOW_BALANCE_MB] ?: 100 }
    val biometricLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.BIOMETRIC_LOCK] ?: false }
    val themeMode: Flow<String> = context.dataStore.data.map { it[Keys.THEME_MODE] ?: "DARK" }

    suspend fun setGhostMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.GHOST_MODE] = enabled }
    }
    suspend fun setDataSaver(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DATA_SAVER] = enabled }
    }
    suspend fun setLowBalanceAlertMb(mb: Int) {
        context.dataStore.edit { it[Keys.LOW_BALANCE_MB] = mb }
    }
    suspend fun setBiometricLock(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC_LOCK] = enabled }
    }
    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }
}
