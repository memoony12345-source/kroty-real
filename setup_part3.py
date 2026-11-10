#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Kroty Net v3.1.0-REAL - Part 3: Application, ViewModel, Real WebView Portal Dialog,
Real Live CameraX Scanner Screen, Biometric & QR Wallet Screen, Settings & MainActivity.
"""
import os

def write_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content.strip() + "\n")
    print(f"[PART 3] Created: {path}")

# 1. AppContainer.kt & KrotyApplication.kt
write_file("app/src/main/java/com/example/di/AppContainer.kt", """
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
""")

write_file("app/src/main/java/com/example/KrotyApplication.kt", """
package com.example

import android.app.Application
import com.example.di.AppContainer
import com.example.worker.SmartBalanceWorker

class KrotyApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SmartBalanceWorker.schedulePeriodic(this)
    }
}
""")

# 2. UI Theme (Color.kt & Theme.kt)
write_file("app/src/main/java/com/example/ui/theme/Color.kt", """
package com.example.ui.theme

import androidx.compose.ui.graphics.Color

val NavyDarkBg = Color(0xFF0B1120)
val NavyCardBg = Color(0xFF162032)
val CyanAccent = Color(0xFF38BDF8)
val EmeraldSuccess = Color(0xFF10B981)
val AmberWarning = Color(0xFFF59E0B)
val CrimsonError = Color(0xFFEF4444)
""")

write_file("app/src/main/java/com/example/ui/theme/Theme.kt", """
package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkPalette = darkColorScheme(
    primary = CyanAccent,
    secondary = EmeraldSuccess,
    tertiary = AmberWarning,
    background = NavyDarkBg,
    surface = NavyCardBg,
    onPrimary = Color.Black,
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9)
)

private val AmoledPalette = darkColorScheme(
    primary = CyanAccent,
    secondary = EmeraldSuccess,
    tertiary = AmberWarning,
    background = Color.Black,
    surface = Color(0xFF0A0F1A),
    onPrimary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightPalette = lightColorScheme(
    primary = Color(0xFF0284C7),
    secondary = Color(0xFF059669),
    background = Color(0xFFF8FAFC),
    surface = Color.White
)

@Composable
fun KrotyNetTheme(
    themeMode: String = "DARK",
    content: @Composable () -> Unit
) {
    val scheme = when (themeMode) {
        "AMOLED" -> AmoledPalette
        "LIGHT" -> LightPalette
        else -> DarkPalette
    }
    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}
""")

# 3. KrotyViewModel.kt (100% Real Dynamic State - Zero Fake Seed Data)
write_file("app/src/main/java/com/example/ui/KrotyViewModel.kt", """
package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ConnectionLogEntity
import com.example.data.local.entity.NetworkEntity
import com.example.data.local.entity.VoucherEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.KrotyRepository
import com.example.domain.network.CaptivePortalProber
import com.example.domain.network.LiveWifiState
import com.example.domain.network.WifiRadarEngine
import com.example.domain.wallet.BackupRestoreManager
import com.example.domain.web.BalanceAndErrorParser
import com.example.widget.KrotySmartWidgetProvider
import com.example.worker.KrotyNotificationHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PortalSessionState(
    val currentNetwork: NetworkEntity? = null,
    val statusMessage: String = "جاري فحص الشبكة المتصلة...",
    val isProbing: Boolean = false,
    val isInternetReachable: Boolean = false,
    val pendingAutoLoginCode: String? = null,
    val openWebPortalAutomatically: Boolean = false
)

class KrotyViewModel(
    private val appContext: Context,
    private val repository: KrotyRepository,
    private val preferences: UserPreferencesRepository,
    val wifiRadar: WifiRadarEngine,
    private val portalProber: CaptivePortalProber
) : ViewModel() {

    val wifiState: StateFlow<LiveWifiState> = wifiRadar.wifiState
    val allNetworks: StateFlow<List<NetworkEntity>> =
        repository.allNetworks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allVouchers: StateFlow<List<VoucherEntity>> =
        repository.allVouchers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val activeVoucher: StateFlow<VoucherEntity?> =
        repository.activeVoucher.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val logs: StateFlow<List<ConnectionLogEntity>> =
        repository.logs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ghostModeEnabled: StateFlow<Boolean> =
        preferences.ghostModeEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val dataSaverEnabled: StateFlow<Boolean> =
        preferences.dataSaverEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val lowBalanceAlertMb: StateFlow<Int> =
        preferences.lowBalanceAlertMb.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100)
    val biometricLockEnabled: StateFlow<Boolean> =
        preferences.biometricLockEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val themeMode: StateFlow<String> =
        preferences.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DARK")

    private val _portalSession = MutableStateFlow(PortalSessionState())
    val portalSession: StateFlow<PortalSessionState> = _portalSession.asStateFlow()

    init {
        refreshEverything()
    }

    fun refreshEverything() {
        viewModelScope.launch {
            _portalSession.update { it.copy(isProbing = true, statusMessage = "جاري فحص الشبكة والبوابة الحقيقية...") }
            val liveWifi = wifiRadar.refreshNow()
            if (!liveWifi.isConnectedToWifi || liveWifi.gatewayIp.isBlank()) {
                _portalSession.update {
                    it.copy(
                        isProbing = false,
                        isInternetReachable = false,
                        statusMessage = "غير متصل بشبكة Wi-Fi حالياً"
                    )
                }
                return@launch
            }

            repository.recordAccessPoint(liveWifi.bssid, liveWifi.ssid, liveWifi.rssiDbm)
            wifiRadar.runDualPing()

            val probeResult = portalProber.probeCurrentNetwork()
            val dynamicNet = repository.getOrUpdateDynamicNetwork(
                rawSsid = liveWifi.ssid,
                gatewayIp = liveWifi.gatewayIp,
                discoveredLoginUrl = probeResult.discoveredLoginUrl
            )

            val liveStatus = probeResult.liveStatus
            if (liveStatus != null && (liveStatus.remainingMegabytes != null || liveStatus.activeUsername != null || liveStatus.isLoggedInStatusPage)) {
                repository.syncLivePortalSession(
                    detectedUsername = liveStatus.activeUsername,
                    remainingMb = liveStatus.remainingMegabytes,
                    remainingSec = liveStatus.remainingTimeSeconds,
                    networkSsid = liveWifi.ssid
                )
                val msg = buildString {
                    append("متصل بالبوابة (${liveWifi.gatewayIp})")
                    liveStatus.activeUsername?.let { append(" • الكرت: $it") }
                    liveStatus.remainingMegabytes?.let { append(" • المتبقي: ${String.format("%.1f", it)} MB") }
                }
                _portalSession.update {
                    it.copy(
                        currentNetwork = dynamicNet,
                        isProbing = false,
                        isInternetReachable = true,
                        statusMessage = msg
                    )
                }
            } else {
                _portalSession.update {
                    it.copy(
                        currentNetwork = dynamicNet,
                        isProbing = false,
                        isInternetReachable = probeResult.isInternetActive,
                        statusMessage = "تم اكتشاف البوابة (${liveWifi.gatewayIp}) — جاهز لتسجيل الدخول أو قراءة الرصيد"
                    )
                }
            }
            KrotySmartWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun saveAndConnectVoucher(
        code: String,
        password: String = "",
        price: Double? = null,
        quotaMb: Long? = null,
        connectImmediately: Boolean = true
    ) {
        val cleanCode = code.trim()
        if (cleanCode.isEmpty()) return
        viewModelScope.launch {
            val ssid = wifiState.value.ssid
            val voucher = repository.addOrActivateVoucher(
                code = cleanCode,
                password = password,
                price = price,
                quotaMb = quotaMb,
                networkSsid = ssid,
                activateImmediately = connectImmediately
            )
            repository.logEvent(
                ssid = ssid,
                code = cleanCode,
                type = if (connectImmediately) "VOUCHER_CONNECT" else "VOUCHER_SAVED",
                messageAr = if (connectImmediately) "جاري الدخول بالكرت $cleanCode في بوابة الشبكة" else "تم حفظ الكرت $cleanCode في المحفظة"
            )
            if (connectImmediately) {
                _portalSession.update {
                    it.copy(
                        pendingAutoLoginCode = voucher.code,
                        openWebPortalAutomatically = true,
                        statusMessage = "جاري فتح البوابة وحقن الكرت ${voucher.code}..."
                    )
                }
            }
            KrotySmartWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun clearPendingAutoLogin() {
        _portalSession.update { it.copy(pendingAutoLoginCode = null, openWebPortalAutomatically = false) }
    }

    fun onWebPortalHtmlScraped(url: String, html: String) {
        viewModelScope.launch {
            val parsed = BalanceAndErrorParser.parseStatusOrLoginHtml(url, html)
            val ssid = wifiState.value.ssid
            if (parsed.errorState != null && parsed.errorMessageAr != null) {
                _portalSession.update { it.copy(statusMessage = parsed.errorMessageAr) }
                val active = repository.getActiveVoucherNow()
                if (active != null && (parsed.errorState == "EXPIRED_DATA" || parsed.errorState == "EXPIRED_TIME" || parsed.errorState == "INVALID")) {
                    repository.updateVoucherStatus(active, parsed.errorState)
                    repository.logEvent(ssid, active.code, parsed.errorState, parsed.errorMessageAr)
                    triggerAutoFailoverIfAvailable()
                }
                return@launch
            }

            if (parsed.isLoggedInStatusPage || parsed.remainingMegabytes != null || parsed.activeUsername != null) {
                repository.syncLivePortalSession(
                    detectedUsername = parsed.activeUsername,
                    remainingMb = parsed.remainingMegabytes,
                    remainingSec = parsed.remainingTimeSeconds,
                    networkSsid = ssid
                )
                val mbStr = parsed.remainingMegabytes?.let { "${String.format("%.1f", it)} MB" } ?: "متصل"
                _portalSession.update {
                    it.copy(
                        isInternetReachable = true,
                        statusMessage = "تم تحديث الجلسة من البوابة بنجاح ($mbStr)"
                    )
                }
                KrotySmartWidgetProvider.updateAllWidgets(appContext)
            }
        }
    }

    fun saveLearnedInputSelector(selector: String) {
        viewModelScope.launch {
            val net = _portalSession.value.currentNetwork ?: return@launch
            val updated = net.copy(learnedInputSelector = selector)
            repository.saveNetwork(updated)
            _portalSession.update {
                it.copy(
                    currentNetwork = updated,
                    statusMessage = "✅ تم حفظ مربع إدخال الكرت ($selector) لهذه الشبكة!"
                )
            }
        }
    }

    fun saveDetectedCardLength(maxLength: Int) {
        viewModelScope.launch {
            val net = _portalSession.value.currentNetwork ?: return@launch
            if (net.expectedCardLength == null && maxLength in 4..20) {
                val updated = net.copy(expectedCardLength = maxLength)
                repository.saveNetwork(updated)
                _portalSession.update { it.copy(currentNetwork = updated) }
            }
        }
    }

    fun freezeActiveVoucher() {
        viewModelScope.launch {
            val gw = wifiState.value.gatewayIp
            val logoutUrl = _portalSession.value.currentNetwork?.logoutUrl
                ?: if (gw.isNotBlank()) "http://$gw/logout" else ""
            if (logoutUrl.isNotBlank()) {
                portalProber.sendLogoutRequest(logoutUrl)
            }
            val active = repository.getActiveVoucherNow()
            if (active != null) {
                repository.updateVoucherStatus(active, "PAUSED")
                repository.logEvent(wifiState.value.ssid, active.code, "CARD_FROZEN", "تم تجميد الكرت وتسجيل الخروج المؤقت من البوابة")
            }
            _portalSession.update { it.copy(statusMessage = "❄️ تم تجميد الكرت وإرسال أمر الخروج للبوابة") }
            KrotySmartWidgetProvider.updateAllWidgets(appContext)
        }
    }

    fun triggerAutoFailoverIfAvailable() {
        viewModelScope.launch {
            val nextVoucher = repository.getNextQueuedVoucher()
            if (nextVoucher != null) {
                repository.updateVoucherStatus(nextVoucher, "ACTIVE")
                repository.logEvent(
                    wifiState.value.ssid,
                    nextVoucher.code,
                    "AUTO_FAILOVER",
                    "تم التبديل التلقائي إلى الكرت الاحتياطي ${nextVoucher.code}"
                )
                KrotyNotificationHelper.showNotification(
                    appContext,
                    4001,
                    "🔄 تم التبديل التلقائي للكرت الاحتياطي",
                    "تم تفعيل الكرت الاحتياطي (${nextVoucher.code}) تلقائياً."
                )
                _portalSession.update {
                    it.copy(
                        pendingAutoLoginCode = nextVoucher.code,
                        openWebPortalAutomatically = true,
                        statusMessage = "جاري الدخول التلقائي بالكرت الاحتياطي ${nextVoucher.code}..."
                    )
                }
            }
        }
    }

    fun deleteVoucher(voucher: VoucherEntity) {
        viewModelScope.launch { repository.deleteVoucher(voucher) }
    }

    fun activateExistingVoucher(voucher: VoucherEntity) {
        viewModelScope.launch {
            repository.updateVoucherStatus(voucher, "ACTIVE")
            _portalSession.update {
                it.copy(
                    pendingAutoLoginCode = voucher.code,
                    openWebPortalAutomatically = true,
                    statusMessage = "جاري الدخول بالكرت ${voucher.code}..."
                )
            }
        }
    }

    fun moveVoucherPriority(voucher: VoucherEntity, delta: Int) {
        viewModelScope.launch {
            val updated = voucher.copy(priorityOrder = (voucher.priorityOrder + delta).coerceAtLeast(0))
            repository.addOrActivateVoucher(
                code = updated.code,
                password = updated.password,
                price = updated.price,
                quotaMb = updated.dataQuotaMb,
                networkSsid = updated.networkSsid,
                activateImmediately = updated.status == "ACTIVE"
            )
        }
    }

    fun updateNetworkSettings(network: NetworkEntity) {
        viewModelScope.launch {
            repository.saveNetwork(network)
            _portalSession.update { it.copy(currentNetwork = network) }
        }
    }

    suspend fun exportBackupJson(): String {
        val nets = repository.getAllNetworksSnapshot()
        val cards = repository.getAllVouchersSnapshot()
        return BackupRestoreManager.exportToJson(nets, cards)
    }

    fun importBackupJson(rawJson: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val payload = BackupRestoreManager.importFromJson(rawJson)
                payload.networks.forEach { repository.saveNetwork(it.copy(id = 0)) }
                payload.vouchers.forEach {
                    repository.addOrActivateVoucher(
                        code = it.code,
                        password = it.password,
                        price = it.price,
                        quotaMb = it.dataQuotaMb,
                        networkSsid = it.networkSsid,
                        activateImmediately = false
                    )
                }
                onResult(true, "تم استعادة ${payload.vouchers.size} كرت و ${payload.networks.size} شبكة بنجاح!")
            } catch (e: Exception) {
                onResult(false, "فشل استيراد الملف: تأكد من صحة ملف النسخة الاحتياطية")
            }
        }
    }

    fun setGhostMode(enabled: Boolean) = viewModelScope.launch { preferences.setGhostMode(enabled) }
    fun setDataSaver(enabled: Boolean) = viewModelScope.launch { preferences.setDataSaver(enabled) }
    fun setLowBalanceAlertMb(mb: Int) = viewModelScope.launch { preferences.setLowBalanceAlertMb(mb) }
    fun setBiometricLock(enabled: Boolean) = viewModelScope.launch { preferences.setBiometricLock(enabled) }
    fun setThemeMode(mode: String) = viewModelScope.launch { preferences.setThemeMode(mode) }
    fun clearLogs() = viewModelScope.launch { repository.clearLogs() }

    companion object {
        fun factory(
            context: Context,
            repository: KrotyRepository,
            preferences: UserPreferencesRepository,
            wifiRadar: WifiRadarEngine,
            portalProber: CaptivePortalProber
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return KrotyViewModel(context.applicationContext, repository, preferences, wifiRadar, portalProber) as T
            }
        }
    }
}
""")

# 4. RadarScreen.kt (Real Dynamic Gateway + Full-Screen WebView Portal with Editable URL Bar & Wi-Fi Binding)
write_file("app/src/main/java/com/example/ui/screens/RadarScreen.kt", """
package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.web.MikroTikJsScripts
import com.example.ui.KrotyViewModel

@Composable
fun RadarScreen(
    viewModel: KrotyViewModel,
    onNavigateToScanner: () -> Unit
) {
    val context = LocalContext.current
    val wifi by viewModel.wifiState.collectAsState()
    val session by viewModel.portalSession.collectAsState()
    val activeVoucher by viewModel.activeVoucher.collectAsState()

    var showPortalDialog by remember { mutableStateOf(false) }
    var initialPortalUrl by remember { mutableStateOf("") }

    val dynamicGateway = wifi.gatewayIp.ifBlank { session.currentNetwork?.gatewayIp.orEmpty() }
    val dynamicLoginUrl = session.currentNetwork?.loginUrl?.takeIf { it.isNotBlank() }
        ?: if (dynamicGateway.isNotBlank()) "http://$dynamicGateway/login" else ""
    val dynamicStatusUrl = session.currentNetwork?.statusUrl?.takeIf { it.isNotBlank() }
        ?: if (dynamicGateway.isNotBlank()) "http://$dynamicGateway/status" else ""

    LaunchedEffect(session.openWebPortalAutomatically, session.pendingAutoLoginCode) {
        if (session.openWebPortalAutomatically && !session.pendingAutoLoginCode.isNullOrBlank()) {
            initialPortalUrl = dynamicLoginUrl.ifBlank { "http://$dynamicGateway" }
            showPortalDialog = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Location Permission / GPS Warning Banner if SSID is hidden by Android
        if (wifi.locationPermissionMissing) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF451A03)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFFBBF24))
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("تفعيل الموقع ضروري لقراءة اسم الشبكة (SSID)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("تم اكتشاف البوابة (${wifi.gatewayIp}) بنجاح، فعل GPS لإظهار اسم الشبكة.", color = Color(0xFFFDE68A), fontSize = 12.sp)
                    }
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }) {
                        Text("تفعيل GPS", color = Color(0xFF38BDF8))
                    }
                }
            }
        }

        // Main Dynamic Network Radar Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = if (wifi.isConnectedToWifi) Color(0xFF38BDF8) else Color.Gray,
                    modifier = Modifier.size(54.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = wifi.ssid,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (dynamicGateway.isNotBlank()) "بوابة الشبكة المكتشفة: $dynamicGateway • BSSID: ${wifi.bssid}"
                    else "يرجى الاتصال بشبكة Wi-Fi لاكتشاف البوابة تلقائياً",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = Color(0xFF0F2942),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "الإشارة: ${wifi.signalPercent}% (${wifi.rssiDbm} dBm)",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Surface(
                        color = if (session.isInternetReachable) Color(0xFF064E3B) else Color(0xFF3B181E),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (session.isInternetReachable) "البوابة متصلة ✅" else "بانتظار الدخول 🔒",
                            color = if (session.isInternetReachable) Color(0xFF34D399) else Color(0xFFF87171),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = session.statusMessage,
                    fontSize = 13.sp,
                    color = Color(0xFFCBD5E1)
                )
            }
        }

        // Dual Ping Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("مقياس النبض المزدوج (Dual Ping)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = { viewModel.refreshEverything() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("بوابة الراوتر (${dynamicGateway.ifBlank { "---" }})", fontSize = 12.sp, color = Color.Gray)
                            Text(
                                text = wifi.routerPingMs?.let { "$it ms" } ?: "غير متاح",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("الإنترنت العالمي (8.8.8.8)", fontSize = 12.sp, color = Color.Gray)
                            Text(
                                text = wifi.internetPingMs?.let { "$it ms" } ?: "غير متصل",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (wifi.internetPingMs != null) Color(0xFF10B981) else Color(0xFFF59E0B)
                            )
                        }
                    }
                }
            }
        }

        // Real Active Voucher & Live Portal Balance Card (NO FAKE DATA)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("الكرت النشط وإدارة الجلسة الحقيقية", fontWeight = FontWeight.Bold, fontSize = 17.sp)

                if (activeVoucher != null) {
                    val v = activeVoucher!!
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("رقم الكرت المتصل", fontSize = 12.sp, color = Color.Gray)
                            Text(v.code, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("الرصيد المتبقي الفعلي", fontSize = 12.sp, color = Color.Gray)
                            Text(
                                text = v.remainingMegabytes?.let { "${String.format("%.1f", it)} MB" } ?: "افتح صفحة الرصيد للقراءة",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                } else {
                    Text(
                        text = "لا يوجد كرت مسجل حالياً. اضغط (فتح صفحة الرصيد / البوابة) لقراءة الكرت المتصل حالياً من الشبكة، أو امسح كرتاً جديداً بالكاميرا.",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.refreshEverything() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("فحص الرصيد")
                    }
                    OutlinedButton(
                        onClick = { viewModel.freezeActiveVoucher() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تجميد الكرت")
                    }
                }

                Button(
                    onClick = {
                        initialPortalUrl = if (dynamicStatusUrl.isNotBlank()) dynamicStatusUrl else "http://neverssl.com"
                        showPortalDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Icon(Icons.Default.Language, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("فتح متصفح بوابة الشبكة (${dynamicGateway.ifBlank { "تلقائي" }})")
                }

                OutlinedButton(
                    onClick = onNavigateToScanner,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("مسح كرت جديد بالكاميرا الحقيقية 📷")
                }
            }
        }

        // Dynamic Portal Endpoints Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("روابط البوابة المكتشفة ديناميكياً للشبكة الحالية", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("عنوان البوابة (Gateway): ${dynamicGateway.ifBlank { "بانتظار الواي فاي" }}", fontSize = 13.sp)
                Text("رابط الدخول: ${dynamicLoginUrl.ifBlank { "---" }}", fontSize = 13.sp, color = Color(0xFF38BDF8))
                Text("رابط الرصيد: ${dynamicStatusUrl.ifBlank { "---" }}", fontSize = 13.sp, color = Color(0xFF10B981))
                Text(
                    "محدد الحقل المتعلم: ${session.currentNetwork?.learnedInputSelector ?: "تلقائي (الأوزان الذكية)"}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }

    if (showPortalDialog) {
        RealPortalWebViewDialog(
            viewModel = viewModel,
            gatewayIp = dynamicGateway,
            startUrl = initialPortalUrl.ifBlank {
                if (dynamicGateway.isNotBlank()) "http://$dynamicGateway/login" else "http://neverssl.com"
            },
            autoInjectCode = session.pendingAutoLoginCode,
            learnedSelector = session.currentNetwork?.learnedInputSelector,
            passwordMode = session.currentNetwork?.passwordMode ?: "EMPTY",
            onDismiss = {
                viewModel.wifiRadar.bindProcessToWifi(false)
                viewModel.clearPendingAutoLogin()
                showPortalDialog = false
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RealPortalWebViewDialog(
    viewModel: KrotyViewModel,
    gatewayIp: String,
    startUrl: String,
    autoInjectCode: String?,
    learnedSelector: String?,
    passwordMode: String,
    onDismiss: () -> Unit
) {
    var urlInput by remember { mutableStateOf(startUrl) }
    var isLoading by remember { mutableStateOf(true) }
    var touchLearnMode by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Bind process to Wi-Fi while portal dialog is open so 3G/4G doesn't hijack local router traffic!
    DisposableEffect(Unit) {
        viewModel.wifiRadar.bindProcessToWifi(true)
        onDispose {
            viewModel.wifiRadar.bindProcessToWifi(false)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0B1120)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top URL & Controls Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF162032))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("رابط بوابة الشبكة") }
                        )
                        IconButton(onClick = {
                            val target = if (urlInput.startsWith("http")) urlInput else "http://$urlInput"
                            webViewRef?.loadUrl(target)
                        }) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "انتقال", tint = Color(0xFF38BDF8))
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFFEF4444))
                        }
                    }

                    // Quick Action Chips for ANY Gateway
                    val baseHost = if (gatewayIp.isNotBlank()) "http://$gatewayIp" else "http://10.0.0.1"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                val u = "$baseHost/status"
                                urlInput = u
                                webViewRef?.loadUrl(u)
                            },
                            label = { Text("الرصيد /status", fontSize = 11.sp) }
                        )
                        AssistChip(
                            onClick = {
                                val u = "$baseHost/login"
                                urlInput = u
                                webViewRef?.loadUrl(u)
                            },
                            label = { Text("الدخول /login", fontSize = 11.sp) }
                        )
                        AssistChip(
                            onClick = {
                                val u = "$baseHost/logout"
                                urlInput = u
                                webViewRef?.loadUrl(u)
                            },
                            label = { Text("خروج /logout", fontSize = 11.sp) }
                        )
                        AssistChip(
                            onClick = {
                                touchLearnMode = !touchLearnMode
                                if (touchLearnMode) {
                                    webViewRef?.evaluateJavascript(MikroTikJsScripts.buildTouchToLearnScript(), null)
                                }
                            },
                            label = {
                                Text(
                                    if (touchLearnMode) "إلمس المربع الآن 👆" else "تعليم المربع 👆",
                                    fontSize = 11.sp,
                                    color = if (touchLearnMode) Color(0xFF10B981) else Color.White
                                )
                            }
                        )
                    }
                }

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFF38BDF8))
                }

                // Real Full-Screen Android WebView
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadsImagesAutomatically = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            CookieManager.getInstance().setAcceptCookie(true)

                            addJavascriptInterface(object {
                                @JavascriptInterface
                                fun onPageHtmlScraped(url: String, html: String) {
                                    viewModel.onWebPortalHtmlScraped(url, html)
                                }

                                @JavascriptInterface
                                fun onSelectorLearned(selector: String) {
                                    viewModel.saveLearnedInputSelector(selector)
                                }

                                @JavascriptInterface
                                fun onDetectedMaxLength(maxLen: Int) {
                                    viewModel.saveDetectedCardLength(maxLen)
                                }

                                @JavascriptInterface
                                fun onLoginFieldMissing() {}
                            }, "KrotyBridge")

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                    if (!url.isNullOrBlank()) urlInput = url
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    if (!url.isNullOrBlank()) urlInput = url

                                    // Scrape page HTML to extract active voucher, balance, or errors
                                    view?.evaluateJavascript(MikroTikJsScripts.buildPageScrapeScript(), null)

                                    // Auto-inject voucher code if requested and on login page
                                    if (!autoInjectCode.isNullOrBlank() && (url?.contains("status", ignoreCase = true) != true)) {
                                        val js = MikroTikJsScripts.buildAutoLoginScript(
                                            voucherCode = autoInjectCode,
                                            password = "",
                                            passwordMode = passwordMode,
                                            learnedSelector = learnedSelector
                                        )
                                        view?.evaluateJavascript(js, null)
                                    }
                                }
                            }

                            webViewRef = this
                            loadUrl(startUrl)
                        }
                    }
                )
            }
        }
    }
}
""")

# 5. ScannerScreen.kt (100% Real Live CameraX PreviewView + ML Kit OCR/QR - ZERO Random Simulation!)
write_file("app/src/main/java/com/example/ui/screens/ScannerScreen.kt", """
package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.domain.scanner.KrotyCameraAnalyzer
import com.example.ui.KrotyViewModel
import java.util.concurrent.Executors

@Composable
fun ScannerScreen(
    viewModel: KrotyViewModel,
    onConnectedNavigateToRadar: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var codeInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var priceInput by remember { mutableStateOf("") }
    var quotaMbInput by remember { mutableStateOf("") }

    var liveDetectedCode by remember { mutableStateOf("") }
    var confidence by remember { mutableStateOf(0f) }
    var selectedLengthLock by remember { mutableStateOf<Int?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableStateOf(1f) }
    var autoConnectOnConsensus by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var statusBanner by remember { mutableStateOf("وجّه الكاميرا الحقيقية نحو أرقام الكرت أو الباركود") }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            statusBanner = "جاري قراءة أرقام الكرت من الصورة..."
            KrotyCameraAnalyzer.scanGalleryUri(context, uri, selectedLengthLock) { result ->
                if (result != null) {
                    codeInput = result.code
                    liveDetectedCode = result.code
                    result.detectedPrice?.let { priceInput = it.toInt().toString() }
                    result.detectedQuotaMb?.let { quotaMbInput = it.toString() }
                    statusBanner = "✅ تم استخراج الكرت (${result.code}) من الصورة بنجاح!"
                    vibratePhone(context)
                } else {
                    statusBanner = "⚠️ لم يتم العثور على رقم كرت واضح في الصورة المختارة"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Real Live CameraX Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("الماسح الحقيقي للكروت (CameraX + ML Kit)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Row {
                        IconButton(onClick = {
                            torchEnabled = !torchEnabled
                            cameraRef?.cameraControl?.enableTorch(torchEnabled)
                        }) {
                            Icon(
                                imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "الفلاش",
                                tint = if (torchEnabled) Color(0xFFFBBF24) else Color.White
                            )
                        }
                        IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "صورة من المعرض", tint = Color(0xFF38BDF8))
                        }
                    }
                }

                // Live CameraX PreviewView Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .border(2.dp, Color(0xFF38BDF8), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraPermission) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                val previewView = PreviewView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                val executor = Executors.newSingleThreadExecutor()

                                cameraProviderFuture.addListener({
                                    try {
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }
                                        val analyzer = KrotyCameraAnalyzer(
                                            getExpectedLength = { selectedLengthLock },
                                            isNumericOnly = { true },
                                            onLiveCandidate = { candidate, prog, confirmed ->
                                                liveDetectedCode = candidate.code
                                                confidence = prog
                                                if (confirmed && codeInput != candidate.code) {
                                                    codeInput = candidate.code
                                                    candidate.detectedPrice?.let { priceInput = it.toInt().toString() }
                                                    candidate.detectedQuotaMb?.let { quotaMbInput = it.toString() }
                                                    statusBanner = "✅ تم التقاط رقم الكرت الحقيقي: ${candidate.code}"
                                                    vibratePhone(ctx)
                                                    if (autoConnectOnConsensus) {
                                                        viewModel.saveAndConnectVoucher(
                                                            code = candidate.code,
                                                            password = passwordInput,
                                                            price = priceInput.toDoubleOrNull(),
                                                            quotaMb = quotaMbInput.toLongOrNull(),
                                                            connectImmediately = true
                                                        )
                                                        onConnectedNavigateToRadar()
                                                    }
                                                }
                                            }
                                        )
                                        val imageAnalysis = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()
                                            .also { it.setAnalyzer(executor, analyzer) }

                                        cameraProvider.unbindAll()
                                        cameraRef = cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageAnalysis
                                        )
                                    } catch (_: Exception) {}
                                }, ContextCompat.getMainExecutor(ctx))

                                previewView
                            }
                        )

                        // Target ROI Frame Overlay
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.82f)
                                .height(95.dp)
                                .border(2.dp, Color(0xFF10B981), RoundedCornerShape(12.dp))
                        )

                        // Live Detected Overlay Badge at Bottom of Camera
                        Surface(
                            color = Color(0xCC0F172A),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = if (liveDetectedCode.isNotBlank())
                                    "الملتقط: $liveDetectedCode (${(confidence * 100).toInt()}%)"
                                else statusBanner,
                                color = Color(0xFF38BDF8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("يرجى منح صلاحية الكاميرا لتشغيل الماسح الحقيقي", color = Color.White, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) {
                                Text("منح صلاحية الكاميرا الآن")
                            }
                        }
                    }
                }

                // Zoom Slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("تقريب العدسة: ${String.format("%.1fx", zoomRatio)}", fontSize = 12.sp, color = Color.Gray)
                    Slider(
                        value = zoomRatio,
                        onValueChange = {
                            zoomRatio = it
                            cameraRef?.cameraControl?.setZoomRatio(it)
                        },
                        valueRange = 1f..3f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                }

                // Length Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val lengths = listOf(null to "تلقائي", 6 to "6 أرقام", 8 to "8 أرقام", 10 to "10 أرقام", 12 to "12 رقم")
                    for ((len, label) in lengths) {
                        FilterChip(
                            selected = selectedLengthLock == len,
                            onClick = { selectedLengthLock = len },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                // Auto-Connect Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("الاتصال التلقائي بالبوابة فور التقاط الكرت ⚡", fontSize = 13.sp)
                    Switch(checked = autoConnectOnConsensus, onCheckedChange = { autoConnectOnConsensus = it })
                }
            }
        }

        // Extracted / Manual Voucher Form Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("بيانات الكرت المستخرج والتنفيذ الفوري", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    label = { Text("رقم الكرت (يُملأ تلقائياً من الكاميرا أو يدوياً)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("كلمة المرور (اتركه فارغاً لكروت المايكروتك العادية)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = { priceInput = it },
                        label = { Text("السعر (اختياري)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = quotaMbInput,
                        onValueChange = { quotaMbInput = it },
                        label = { Text("الباقة MB (اختياري)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            if (codeInput.isNotBlank()) {
                                viewModel.saveAndConnectVoucher(
                                    code = codeInput,
                                    password = passwordInput,
                                    price = priceInput.toDoubleOrNull(),
                                    quotaMb = quotaMbInput.toLongOrNull(),
                                    connectImmediately = true
                                )
                                onConnectedNavigateToRadar()
                            } else {
                                statusBanner = "⚠️ وجّه الكاميرا نحو الكرت أولاً أو اكتب رقم الكرت"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("اتصال فوري بالبوابة")
                    }

                    OutlinedButton(
                        onClick = {
                            if (codeInput.isNotBlank()) {
                                viewModel.saveAndConnectVoucher(
                                    code = codeInput,
                                    password = passwordInput,
                                    price = priceInput.toDoubleOrNull(),
                                    quotaMb = quotaMbInput.toLongOrNull(),
                                    connectImmediately = false
                                )
                                statusBanner = "✅ تم حفظ الكرت (${codeInput}) في طابور المحفظة!"
                                codeInput = ""
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("حفظ في المحفظة")
                    }
                }
            }
        }
    }
}

private fun vibratePhone(context: Context) {
    try {
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(120)
        }
    } catch (_: Exception) {}
}
""")

# 6. WalletScreen.kt (Real Vouchers, QR Share, Biometrics, Safe Clipboard)
write_file("app/src/main/java/com/example/ui/screens/WalletScreen.kt", """
package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.data.local.entity.VoucherEntity
import com.example.domain.wallet.VoucherQrGenerator
import com.example.ui.KrotyViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WalletScreen(
    viewModel: KrotyViewModel,
    onConnectNavigateToRadar: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vouchers by viewModel.allVouchers.collectAsState()
    val biometricEnabled by viewModel.biometricLockEnabled.collectAsState()

    var isUnlocked by remember { mutableStateOf(!biometricEnabled) }
    var maskCodes by remember { mutableStateOf(false) }
    var qrVoucher by remember { mutableStateOf<VoucherEntity?>(null) }

    LaunchedEffect(biometricEnabled) {
        if (!biometricEnabled) isUnlocked = true
    }

    if (biometricEnabled && !isUnlocked) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(12.dp))
            Text("المحفظة محمية بالبصمة الحيوية", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                val activity = context as? FragmentActivity
                if (activity != null) {
                    val executor = ContextCompat.getMainExecutor(context)
                    val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            isUnlocked = true
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            isUnlocked = true
                        }
                    })
                    val info = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("فتح محفظة كروتي نت")
                        .setSubtitle("استخدم البصمة لعرض الكروت المحفوظة")
                        .setNegativeButtonText("فتح مباشر")
                        .build()
                    prompt.authenticate(info)
                } else {
                    isUnlocked = true
                }
            }) {
                Text("تحقق بالبصمة لفتح المحفظة")
            }
        }
        return
    }

    val totalPrice = vouchers.sumOf { it.price ?: 0.0 }
    val queuedCount = vouchers.count { it.status == "NEW_QUEUED" }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Analytics Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("إجمالي الكروت: ${vouchers.size} (الاحتياط: $queuedCount)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("إجمالي التكلفة المسجلة: ${totalPrice.toInt()} ريال", fontSize = 13.sp, color = Color(0xFF38BDF8))
                }
                IconButton(onClick = { maskCodes = !maskCodes }) {
                    Icon(
                        imageVector = if (maskCodes) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "إخفاء/إظهار الأرقام"
                    )
                }
            }
        }

        if (vouchers.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("المحفظة فارغة حالياً", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("امسح كرتاً بالكاميرا أو اتصل بالبوابة ليتم حفظ كروتك الحقيقية هنا.", fontSize = 13.sp, color = Color.Gray)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(vouchers, key = { it.id }) { voucher ->
                    val displayCode = if (maskCodes && voucher.code.length > 4)
                        "${voucher.code.take(2)}****${voucher.code.takeLast(2)}"
                    else voucher.code

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(displayCode, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                Text(
                                    text = when (voucher.status) {
                                        "ACTIVE" -> "نشط ✅"
                                        "PAUSED" -> "مجمد ❄️"
                                        "NEW_QUEUED" -> "احتياطي في الطابور ⏳"
                                        else -> voucher.status
                                    },
                                    fontSize = 12.sp,
                                    color = Color(0xFF10B981)
                                )
                            }

                            Text(
                                text = "الشبكة: ${voucher.networkSsid.ifBlank { "عام" }} • المتبقي: ${voucher.remainingMegabytes?.let { "${String.format("%.1f", it)} MB" } ?: "غير مقروء"}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.activateExistingVoucher(voucher)
                                        onConnectNavigateToRadar()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("دخول الآن", fontSize = 12.sp)
                                }
                                OutlinedButton(onClick = { qrVoucher = voucher }) {
                                    Icon(Icons.Default.QrCode, contentDescription = "QR", modifier = Modifier.size(18.dp))
                                }
                                OutlinedButton(onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("voucher", voucher.code))
                                    scope.launch {
                                        delay(60000)
                                        try { cm.setPrimaryClip(ClipData.newPlainText("", "")) } catch (_: Exception) {}
                                    }
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { viewModel.deleteVoucher(voucher) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFEF4444))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (qrVoucher != null) {
        val v = qrVoucher!!
        val qrBitmap = remember(v.code) { VoucherQrGenerator.generateQrBitmap(v.code, 512) }
        AlertDialog(
            onDismissRequest = { qrVoucher = null },
            title = { Text("مشاركة الكرت عبر QR Code") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR",
                        modifier = Modifier.size(220.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("رقم الكرت: ${v.code}", fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "كرت شبكة (${v.networkSsid}): ${v.code}")
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "مشاركة الكرت"))
                }) {
                    Text("مشاركة كنص")
                }
            },
            dismissButton = {
                TextButton(onClick = { qrVoucher = null }) {
                    Text("إغلاق")
                }
            }
        )
    }
}
""")

# 7. SettingsScreen.kt (Saved Dynamic Networks, JSON Backup/Restore, Logs)
write_file("app/src/main/java/com/example/ui/screens/SettingsScreen.kt", """
package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.KrotyViewModel
import com.example.worker.SmartBalanceWorker
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: KrotyViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val networks by viewModel.allNetworks.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val ghostMode by viewModel.ghostModeEnabled.collectAsState()
    val dataSaver by viewModel.dataSaverEnabled.collectAsState()
    val biometricLock by viewModel.biometricLockEnabled.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    var backupJsonDialogText by remember { mutableStateOf<String?>(null) }
    var importJsonInput by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var feedbackBanner by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        feedbackBanner?.let {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B))) {
                Text(it, modifier = Modifier.padding(12.dp), color = Color.White, fontSize = 13.sp)
            }
        }

        // General Preferences
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("الإعدادات الذكية والمظهر", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("وضع الدخول التلقائي بالبوابة", fontSize = 14.sp)
                    Switch(checked = ghostMode, onCheckedChange = { viewModel.setGhostMode(it) })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("حماية المحفظة بالبصمة", fontSize = 14.sp)
                    Switch(checked = biometricLock, onCheckedChange = { viewModel.setBiometricLock(it) })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("توفير بيانات الكرت داخل البوابة", fontSize = 14.sp)
                    Switch(checked = dataSaver, onCheckedChange = { viewModel.setDataSaver(it) })
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = themeMode == "DARK", onClick = { viewModel.setThemeMode("DARK") }, label = { Text("داكن ملكي") })
                    FilterChip(selected = themeMode == "AMOLED", onClick = { viewModel.setThemeMode("AMOLED") }, label = { Text("أسود AMOLED") })
                    FilterChip(selected = themeMode == "LIGHT", onClick = { viewModel.setThemeMode("LIGHT") }, label = { Text("فاتح") })
                }

                Button(
                    onClick = {
                        SmartBalanceWorker.runOnceNow(context)
                        feedbackBanner = "✅ تم تشغيل فحص الرصيد الخلفي وتحديث الودجت الآن"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("تشغيل فحص الرصيد الخلفي وتحديث الودجت الآن")
                }
            }
        }

        // Backup & Restore Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("النسخ الاحتياطي والاستعادة (JSON)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                backupJsonDialogText = viewModel.exportBackupJson()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("تصدير الكروت والشبكات")
                    }
                    OutlinedButton(
                        onClick = { showImportDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("استعادة نسخة")
                    }
                }
            }
        }

        // Discovered Dynamic Networks Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الشبكات المكتشفة تلقائياً (${networks.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (networks.isEmpty()) {
                    Text("لم يتم حفظ أي شبكة بعد. اتصل بالواي فاي ليتم اكتشاف البوابة تلقائياً.", fontSize = 12.sp, color = Color.Gray)
                } else {
                    networks.forEach { net ->
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(net.ssid, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("البوابة: ${net.gatewayIp} • الدخول: ${net.loginUrl}", fontSize = 11.sp, color = Color(0xFF38BDF8))
                                }
                                if (net.learnedInputSelector != null) {
                                    TextButton(onClick = {
                                        viewModel.updateNetworkSettings(net.copy(learnedInputSelector = null))
                                    }) {
                                        Text("مسح المربع المتعلم", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Real Connection Logs
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("السجل التشخيصي المباشر (${logs.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    TextButton(onClick = { viewModel.clearLogs() }) {
                        Text("مسح السجل")
                    }
                }
                if (logs.isEmpty()) {
                    Text("لا توجد أحداث مسجلة بعد.", fontSize = 12.sp, color = Color.Gray)
                } else {
                    logs.take(15).forEach { log ->
                        Text("• [${log.networkSsid}] ${log.messageAr}", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                    }
                }
            }
        }
    }

    if (backupJsonDialogText != null) {
        val jsonText = backupJsonDialogText!!
        AlertDialog(
            onDismissRequest = { backupJsonDialogText = null },
            title = { Text("النسخة الاحتياطية (JSON)") },
            text = {
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, jsonText)
                    }
                    context.startActivity(Intent.createChooser(share, "مشاركة النسخة الاحتياطية"))
                }) {
                    Text("مشاركة / حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { backupJsonDialogText = null }) { Text("إغلاق") }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("استعادة نسخة احتياطية") },
            text = {
                OutlinedTextField(
                    value = importJsonInput,
                    onValueChange = { importJsonInput = it },
                    label = { Text("الصق كود JSON هنا") },
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importBackupJson(importJsonInput) { _, msg ->
                        feedbackBanner = msg
                        showImportDialog = false
                    }
                }) {
                    Text("استيراد الآن")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("إلغاء") }
            }
        )
    }
}
""")

# 8. MainActivity.kt (Inherits FragmentActivity, Requests Permissions on Launch, RTL Layout)
write_file("app/src/main/java/com/example/MainActivity.kt", """
package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.KrotyViewModel
import com.example.ui.screens.RadarScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WalletScreen
import com.example.ui.theme.KrotyNetTheme

class MainActivity : FragmentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as KrotyApplication).container

        setContent {
            val krotyViewModel: KrotyViewModel = viewModel(
                factory = KrotyViewModel.factory(
                    context = this,
                    repository = container.repository,
                    preferences = container.preferences,
                    wifiRadar = container.wifiRadarEngine,
                    portalProber = container.portalProber
                )
            )

            val themeMode by krotyViewModel.themeMode.collectAsState()
            var selectedTab by remember { mutableStateOf(0) }

            // Request Camera, Location (for SSID/BSSID), and Notification permissions immediately on launch!
            val permissionsLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) {
                krotyViewModel.refreshEverything()
            }

            LaunchedEffect(Unit) {
                val perms = mutableListOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                permissionsLauncher.launch(perms.toTypedArray())
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                KrotyNetTheme(themeMode = themeMode) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = {
                                    Text("كروتي نت (Kroty Net)", fontWeight = FontWeight.Bold)
                                }
                            )
                        },
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = { Icon(Icons.Default.Radar, contentDescription = null) },
                                    label = { Text("الرادار والشبكة") }
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                                    label = { Text("الماسح الذكي") }
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                                    label = { Text("محفظة الكروت") }
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 3,
                                    onClick = { selectedTab = 3 },
                                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    label = { Text("الإعدادات والسجل") }
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (selectedTab) {
                                0 -> RadarScreen(
                                    viewModel = krotyViewModel,
                                    onNavigateToScanner = { selectedTab = 1 }
                                )
                                1 -> ScannerScreen(
                                    viewModel = krotyViewModel,
                                    onConnectedNavigateToRadar = { selectedTab = 0 }
                                )
                                2 -> WalletScreen(
                                    viewModel = krotyViewModel,
                                    onConnectNavigateToRadar = { selectedTab = 0 }
                                )
                                3 -> SettingsScreen(viewModel = krotyViewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}
""")

print("Part 3 generated successfully!")
