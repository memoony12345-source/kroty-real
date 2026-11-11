#!/usr/bin/env python3
import os

def write_file(path, content):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content.strip() + "\n")
    print(f"[PART 3] Created: {path}")

# 1. AppContainer.kt
write_file("app/src/main/java/com/example/di/AppContainer.kt", r"""
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
""")

# 2. KrotyApplication.kt
write_file("app/src/main/java/com/example/KrotyApplication.kt", r"""
package com.example

import android.app.Application
import com.example.di.AppContainer
import com.example.domain.system.SmartBalanceWorker

class KrotyApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SmartBalanceWorker.schedulePeriodicSync(this)
    }
}
""")

# 3. Color.kt
write_file("app/src/main/java/com/example/ui/theme/Color.kt", r"""
package com.example.ui.theme

import androidx.compose.ui.graphics.Color

val KrotyDarkBg = Color(0xFF0A0F1D)
val KrotyCardBg = Color(0xFF131C31)
val KrotyCardBorder = Color(0xFF1E2D4A)
val KrotyCyan = Color(0xFF00E5FF)
val KrotyEmerald = Color(0xFF00E676)
val KrotyAmber = Color(0xFFFFB300)
val KrotyCoralRed = Color(0xFFFF5252)
val KrotyTextPrimary = Color(0xFFF1F5F9)
val KrotyTextSecondary = Color(0xFF94A3B8)
""")

# 4. Theme.kt
write_file("app/src/main/java/com/example/ui/theme/Theme.kt", r"""
package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val KrotyColorScheme = darkColorScheme(
    primary = KrotyCyan,
    secondary = KrotyEmerald,
    tertiary = KrotyAmber,
    background = KrotyDarkBg,
    surface = KrotyCardBg,
    onPrimary = KrotyDarkBg,
    onSecondary = KrotyDarkBg,
    onBackground = KrotyTextPrimary,
    onSurface = KrotyTextPrimary,
    error = KrotyCoralRed
)

@Composable
fun KrotyNetTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = KrotyColorScheme,
            content = content
        )
    }
}
""")

# 5. KrotyViewModel.kt
write_file("app/src/main/java/com/example/ui/KrotyViewModel.kt", r"""
package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ConnectionLogEntity
import com.example.data.local.entity.NetworkEntity
import com.example.data.local.entity.VoucherEntity
import com.example.data.local.entity.VoucherStatus
import com.example.data.preferences.UserPreferencesState
import com.example.di.AppContainer
import com.example.domain.radar.WifiTelemetry
import com.example.domain.system.KrotySmartWidgetProvider
import com.example.domain.system.SmartBalanceWorker
import com.example.domain.web.MikroTikErrorType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PortalUiState(
    val isPortalDialogOpen: Boolean = false,
    val currentPortalUrl: String = "",
    val activePortalHost: String = "",
    val pendingAutoLoginCode: String? = null,
    val pendingAutoLoginPassword: String = "",
    val touchToLearnMode: Boolean = false,
    val liveRemainingMb: String = "غير متزامن",
    val liveRemainingTime: String = "غير متزامن",
    val statusBannerMessage: String? = null,
    val isProbingSession: Boolean = false
)

class KrotyViewModel(
    private val appContext: Context,
    val container: AppContainer
) : ViewModel() {

    val wifiTelemetry: StateFlow<WifiTelemetry> = container.wifiRadarEngine.telemetry

    val allVouchers: StateFlow<List<VoucherEntity>> = container.repository.allVouchers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeVoucher: StateFlow<VoucherEntity?> = container.repository.activeVoucher
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val queuedVouchers: StateFlow<List<VoucherEntity>> = container.repository.queuedVouchers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allNetworks: StateFlow<List<NetworkEntity>> = container.repository.allNetworks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentLogs: StateFlow<List<ConnectionLogEntity>> = container.repository.recentLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val preferences: StateFlow<UserPreferencesState> = container.preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesState())

    private val _portalUiState = MutableStateFlow(PortalUiState())
    val portalUiState: StateFlow<PortalUiState> = _portalUiState.asStateFlow()

    private var pingJob: Job? = null
    private var lastSyncedGateway: String = ""

    init {
        container.wifiRadarEngine.startRadar()
        startContinuousTelemetryLoop()
    }

    private fun startContinuousTelemetryLoop() {
        pingJob?.cancel()
        pingJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                container.wifiRadarEngine.refreshSnapshot()
                val current = wifiTelemetry.value
                if (current.isWifiConnected && current.gatewayIp.isNotBlank()) {
                    container.wifiRadarEngine.measureDualPing(current.gatewayIp)
                    if (current.ssid.isNotBlank() && current.ssid != "غير متصل بالواي فاي") {
                        val existing = container.repository.getNetworkBySsid(current.ssid)
                        if (existing == null) {
                            container.repository.saveNetwork(
                                NetworkEntity(
                                    ssid = current.ssid,
                                    gatewayIp = current.gatewayIp,
                                    loginUrl = "http://${current.gatewayIp}/login",
                                    statusUrl = "http://${current.gatewayIp}/status",
                                    logoutUrl = "http://${current.gatewayIp}/logout"
                                )
                            )
                        } else if (existing.gatewayIp != current.gatewayIp) {
                            container.repository.saveNetwork(
                                existing.copy(
                                    gatewayIp = current.gatewayIp,
                                    loginUrl = "http://${current.gatewayIp}/login",
                                    statusUrl = "http://${current.gatewayIp}/status",
                                    logoutUrl = "http://${current.gatewayIp}/logout"
                                )
                            )
                        }
                    }
                    if (lastSyncedGateway != current.gatewayIp) {
                        lastSyncedGateway = current.gatewayIp
                        syncLiveMikroTikSession(silent = true)
                    }
                }
                delay(6000)
            }
        }
    }

    fun syncLiveMikroTikSession(silent: Boolean = false) {
        val snap = wifiTelemetry.value
        val gateway = snap.gatewayIp
        if (gateway.isBlank()) {
            if (!silent) {
                _portalUiState.value = _portalUiState.value.copy(
                    statusBannerMessage = "يرجى الاتصال بشبكة واي فاي لاكتشاف بوابة المايكروتك تلقائياً."
                )
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _portalUiState.value = _portalUiState.value.copy(isProbingSession = true)
            val result = container.captivePortalProber.probeAndSyncSession(gateway)
            val parsed = result.parsedState

            if (parsed != null && (parsed.isLoggedIn || parsed.activeUsername != null || parsed.remainingMb != null || parsed.remainingTimeText != null)) {
                val mbText = parsed.remainingMb?.let { String.format("%.1f MB", it) } ?: "مفتوح / غير محدد"
                val timeText = parsed.remainingTimeText ?: "مفتوح"

                var currentActive = activeVoucher.value
                val detectedCode = parsed.activeUsername
                if (!detectedCode.isNullOrBlank()) {
                    val matched = allVouchers.value.firstOrNull { it.code == detectedCode }
                    if (matched != null) {
                        container.repository.activateVoucher(matched)
                        currentActive = matched
                    } else if (currentActive == null) {
                        val id = container.repository.addVoucher(
                            code = detectedCode,
                            ssid = snap.ssid,
                            categoryLabel = "جلسة مكتشفة تلقائياً",
                            initialMb = parsed.remainingMb ?: 0.0
                        )
                        val newlyAdded = VoucherEntity(
                            id = id,
                            code = detectedCode,
                            ssid = snap.ssid,
                            categoryLabel = "جلسة مكتشفة تلقائياً",
                            status = VoucherStatus.ACTIVE,
                            remainingMb = parsed.remainingMb,
                            remainingTimeSeconds = parsed.remainingTimeSeconds
                        )
                        container.repository.activateVoucher(newlyAdded)
                        currentActive = newlyAdded
                    }
                }

                if (currentActive != null) {
                    container.repository.updateActiveVoucherTelemetry(
                        voucherId = currentActive.id,
                        remainingMb = parsed.remainingMb,
                        remainingTime = parsed.remainingTimeText
                    )
                }

                container.notificationHelper.showLiveSessionNotification(
                    ssid = snap.ssid,
                    voucherCode = detectedCode ?: currentActive?.code ?: "متصل",
                    remainingMbText = mbText,
                    remainingTimeText = timeText
                )
                KrotySmartWidgetProvider.updateAllWidgets(
                    context = appContext,
                    ssid = snap.ssid,
                    voucherCode = detectedCode ?: currentActive?.code ?: "متصل",
                    balanceText = "$mbText • $timeText"
                )

                _portalUiState.value = _portalUiState.value.copy(
                    isProbingSession = false,
                    liveRemainingMb = mbText,
                    liveRemainingTime = timeText,
                    statusBannerMessage = "تمت مزامنة الجلسة الحية من البوابة ($gateway)"
                )
            } else {
                _portalUiState.value = _portalUiState.value.copy(
                    isProbingSession = false,
                    statusBannerMessage = if (silent) null else "تم فحص البوابة ($gateway): بانتظار تسجيل الدخول بالكرت."
                )
            }
        }
    }

    fun openRealPortal(
        targetPath: String = "/status",
        autoLoginVoucher: VoucherEntity? = null,
        enableTouchToLearn: Boolean = false
    ) {
        val snap = wifiTelemetry.value
        val gateway = snap.gatewayIp.ifBlank {
            allNetworks.value.firstOrNull { it.ssid == snap.ssid }?.gatewayIp ?: ""
        }
        if (gateway.isBlank()) {
            _portalUiState.value = _portalUiState.value.copy(
                statusBannerMessage = "لا توجد بوابة شبكة نشطة حالياً. تأكد من اتصالك بالواي فاي."
            )
            return
        }

        val hostToUse = _portalUiState.value.activePortalHost.ifBlank { gateway }
        val normalizedPath = if (targetPath.startsWith("http")) {
            targetPath
        } else if (targetPath.startsWith("/")) {
            "http://$hostToUse$targetPath"
        } else {
            "http://$hostToUse/$targetPath"
        }

        _portalUiState.value = _portalUiState.value.copy(
            isPortalDialogOpen = true,
            currentPortalUrl = normalizedPath,
            activePortalHost = hostToUse,
            pendingAutoLoginCode = autoLoginVoucher?.code,
            pendingAutoLoginPassword = autoLoginVoucher?.password ?: "",
            touchToLearnMode = enableTouchToLearn
        )
    }

    fun updateActivePortalHost(host: String) {
        if (host.isNotBlank() && !host.contains("gstatic") && !host.contains("neverssl")) {
            _portalUiState.value = _portalUiState.value.copy(activePortalHost = host)
        }
    }

    fun setPendingAutoLoginCode(code: String) {
        _portalUiState.value = _portalUiState.value.copy(pendingAutoLoginCode = code.trim())
    }

    fun toggleTouchToLearnMode(enabled: Boolean) {
        _portalUiState.value = _portalUiState.value.copy(touchToLearnMode = enabled)
    }

    fun closePortalDialog() {
        container.wifiRadarEngine.unbindProcess()
        _portalUiState.value = _portalUiState.value.copy(
            isPortalDialogOpen = false,
            pendingAutoLoginCode = null,
            touchToLearnMode = false
        )
    }

    fun bindWifiForPortal() {
        container.wifiRadarEngine.bindProcessToWifi()
    }

    fun onWebViewHtmlExtracted(url: String, html: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val parsed = container.balanceParser.parsePortalHtml(html)
            val snap = wifiTelemetry.value

            if (parsed.errorType != MikroTikErrorType.NONE && parsed.arabicErrorMessage != null) {
                _portalUiState.value = _portalUiState.value.copy(
                    statusBannerMessage = parsed.arabicErrorMessage
                )
                container.repository.logEvent(
                    ssid = snap.ssid,
                    bssid = snap.bssid,
                    voucherCode = _portalUiState.value.pendingAutoLoginCode ?: activeVoucher.value?.code ?: "",
                    eventType = "PORTAL_ERROR",
                    details = parsed.arabicErrorMessage
                )

                if (parsed.errorType == MikroTikErrorType.TRAFFIC_LIMIT_REACHED ||
                    parsed.errorType == MikroTikErrorType.UPTIME_LIMIT_REACHED
                ) {
                    activeVoucher.value?.let { exhausted ->
                        container.repository.markVoucherStatus(exhausted.id, VoucherStatus.EXHAUSTED)
                    }
                    val nextInQueue = queuedVouchers.value.firstOrNull()
                    if (nextInQueue != null) {
                        container.repository.activateVoucher(nextInQueue)
                        _portalUiState.value = _portalUiState.value.copy(
                            pendingAutoLoginCode = nextInQueue.code,
                            pendingAutoLoginPassword = nextInQueue.password,
                            statusBannerMessage = "انتهى الكرت الحالي. جاري التحويل التلقائي للكرت الاحتياطي: ${nextInQueue.code}"
                        )
                    }
                }
                return@launch
            }

            if (parsed.isLoggedIn || parsed.remainingMb != null || parsed.remainingTimeText != null || url.contains("/status")) {
                val mbText = parsed.remainingMb?.let { String.format("%.1f MB", it) }
                    ?: _portalUiState.value.liveRemainingMb
                val timeText = parsed.remainingTimeText ?: _portalUiState.value.liveRemainingTime

                val codeToActivate = parsed.activeUsername
                    ?: _portalUiState.value.pendingAutoLoginCode
                    ?: activeVoucher.value?.code

                if (!codeToActivate.isNullOrBlank()) {
                    val existing = allVouchers.value.firstOrNull { it.code == codeToActivate }
                    if (existing != null) {
                        container.repository.activateVoucher(existing)
                        container.repository.updateActiveVoucherTelemetry(
                            voucherId = existing.id,
                            remainingMb = parsed.remainingMb,
                            remainingTime = parsed.remainingTimeText
                        )
                    }
                }

                _portalUiState.value = _portalUiState.value.copy(
                    liveRemainingMb = mbText,
                    liveRemainingTime = timeText,
                    pendingAutoLoginCode = null,
                    statusBannerMessage = "متصل بنجاح • الرصيد: $mbText • الوقت: $timeText"
                )

                container.notificationHelper.showLiveSessionNotification(
                    ssid = snap.ssid,
                    voucherCode = codeToActivate ?: "متصل",
                    remainingMbText = mbText,
                    remainingTimeText = timeText
                )
                KrotySmartWidgetProvider.updateAllWidgets(
                    context = appContext,
                    ssid = snap.ssid,
                    voucherCode = codeToActivate ?: "متصل",
                    balanceText = "$mbText • $timeText"
                )
            }
        }
    }

    fun saveCustomFieldSelector(selector: String) {
        val snap = wifiTelemetry.value
        if (snap.ssid.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val net = container.repository.getNetworkBySsid(snap.ssid)
            if (net != null) {
                container.repository.saveNetwork(net.copy(voucherFieldSelector = selector))
            } else {
                container.repository.saveNetwork(
                    NetworkEntity(
                        ssid = snap.ssid,
                        gatewayIp = snap.gatewayIp,
                        loginUrl = "http://${snap.gatewayIp}/login",
                        statusUrl = "http://${snap.gatewayIp}/status",
                        logoutUrl = "http://${snap.gatewayIp}/logout",
                        voucherFieldSelector = selector
                    )
                )
            }
            _portalUiState.value = _portalUiState.value.copy(
                touchToLearnMode = false,
                statusBannerMessage = "تم حفظ حقل الكرت المخصص للشبكة (${snap.ssid}): $selector"
            )
        }
    }

    fun freezeActiveSessionNow() {
        val snap = wifiTelemetry.value
        val gateway = _portalUiState.value.activePortalHost.ifBlank { snap.gatewayIp }
        if (gateway.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val ok = container.captivePortalProber.executeLogoutFreeze(gateway)
            _portalUiState.value = _portalUiState.value.copy(
                statusBannerMessage = if (ok) {
                    "تم تجميد الجلسة وتسجيل الخروج المؤقت (/logout) لحفظ رصيد الكرت."
                } else {
                    "تعذر الوصول لرابط الخروج المباشر، جرب فتح البوابة والضغط على تجميد الكرت."
                }
            )
            container.repository.logEvent(
                ssid = snap.ssid,
                bssid = snap.bssid,
                voucherCode = activeVoucher.value?.code ?: "",
                eventType = "FREEZE_LOGOUT",
                details = "Logout freeze executed on $gateway (success=$ok)"
            )
        }
    }

    fun saveAndConnectVoucher(
        code: String,
        password: String = "",
        categoryLabel: String = "كرت فوري",
        priceRials: Double = 0.0,
        initialMb: Double = 0.0,
        connectImmediately: Boolean = true
    ) {
        val cleanCode = code.trim()
        if (cleanCode.isBlank()) return
        val snap = wifiTelemetry.value
        viewModelScope.launch(Dispatchers.IO) {
            val id = container.repository.addVoucher(
                code = cleanCode,
                password = password,
                ssid = snap.ssid.ifBlank { "عام" },
                categoryLabel = categoryLabel,
                priceRials = priceRials,
                initialMb = initialMb
            )
            val entity = VoucherEntity(
                id = id,
                code = cleanCode,
                password = password,
                ssid = snap.ssid.ifBlank { "عام" },
                categoryLabel = categoryLabel,
                priceRials = priceRials,
                initialMb = initialMb,
                remainingMb = if (initialMb > 0) initialMb else null,
                status = if (connectImmediately) VoucherStatus.ACTIVE else VoucherStatus.NEW
            )
            if (connectImmediately) {
                container.repository.activateVoucher(entity)
                openRealPortal(targetPath = "/login", autoLoginVoucher = entity)
            } else {
                _portalUiState.value = _portalUiState.value.copy(
                    statusBannerMessage = "تم حفظ الكرت ($cleanCode) في المحفظة بنجاح."
                )
            }
        }
    }

    fun activateAndConnectFromWallet(voucher: VoucherEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            container.repository.activateVoucher(voucher)
            openRealPortal(targetPath = "/login", autoLoginVoucher = voucher)
        }
    }

    fun deleteVoucher(voucher: VoucherEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            container.repository.deleteVoucher(voucher)
        }
    }

    fun copyVoucherSecurely(code: String) {
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("KrotyVoucher", code))
        _portalUiState.value = _portalUiState.value.copy(
            statusBannerMessage = "تم نسخ الكرت ($code) وسيتم مسح الحافظة تلقائياً بعد 60 ثانية للأمان."
        )
        viewModelScope.launch {
            delay(60_000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }

    fun triggerBackgroundWorkerNow() {
        SmartBalanceWorker.enqueueImmediateSync(appContext)
        _portalUiState.value = _portalUiState.value.copy(
            statusBannerMessage = "تم تشغيل عامل فحص الرصيد في الخلفية وتحديث الإشعار والويدجت."
        )
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val result = container.backupRestoreManager.exportToUri(uri)
            _portalUiState.value = _portalUiState.value.copy(
                statusBannerMessage = result.fold(
                    onSuccess = { count -> "تم تصدير النسخة الاحتياطية بنجاح ($count عنصر)." },
                    onFailure = { err -> "فشل التصدير: ${err.localizedMessage}" }
                )
            )
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val result = container.backupRestoreManager.importFromUri(uri)
            _portalUiState.value = _portalUiState.value.copy(
                statusBannerMessage = result.fold(
                    onSuccess = { count -> "تم استيراد $count كرت وشبكة بنجاح." },
                    onFailure = { err -> "فشل الاستيراد: ${err.localizedMessage}" }
                )
            )
        }
    }

    fun clearBanner() {
        _portalUiState.value = _portalUiState.value.copy(statusBannerMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        container.wifiRadarEngine.stopRadar()
    }

    companion object {
        fun provideFactory(appContext: Context, container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return KrotyViewModel(appContext, container) as T
                }
            }
    }
}
""")

# 6. RadarScreen.kt (With Ultra-Modern Kroty Pro Portal Cockpit)
write_file("app/src/main/java/com/example/ui/screens/RadarScreen.kt", r"""
package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.VoucherEntity
import com.example.domain.radar.WifiTelemetry
import com.example.domain.web.MikroTikJsScripts
import com.example.ui.KrotyViewModel
import com.example.ui.PortalUiState
import com.example.ui.theme.*

@Composable
fun RadarScreen(
    viewModel: KrotyViewModel,
    onRequestLocationEnable: () -> Unit
) {
    val telemetry by viewModel.wifiTelemetry.collectAsState()
    val activeVoucher by viewModel.activeVoucher.collectAsState()
    val allVouchers by viewModel.allVouchers.collectAsState()
    val portalState by viewModel.portalUiState.collectAsState()
    val networks by viewModel.allNetworks.collectAsState()

    val currentNetConfig = networks.firstOrNull { it.ssid == telemetry.ssid }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(KrotyDarkBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            portalState.statusBannerMessage?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2942)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = msg,
                            color = KrotyCyan,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearBanner() }) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = KrotyTextSecondary)
                        }
                    }
                }
            }
        }

        if (telemetry.isLocationToggleRequired && telemetry.isWifiConnected) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2510)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = KrotyAmber)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تفعيل الموقع مطلوب لقراءة اسم الشبكة", color = KrotyAmber, fontWeight = FontWeight.Bold)
                            Text("نظام أندرويد يخفي اسم الـ SSID بدون تشغيل الـ GPS.", color = KrotyTextPrimary, fontSize = 12.sp)
                        }
                        TextButton(onClick = onRequestLocationEnable) {
                            Text("تفعيل الآن", color = KrotyCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            LiveWifiRadarCard(
                telemetry = telemetry,
                isProbing = portalState.isProbingSession,
                onRefreshRadar = { viewModel.syncLiveMikroTikSession(silent = false) }
            )
        }

        if (telemetry.isMacRandomized) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF331B24)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = KrotyCoralRed)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("تنبيه: العنوان العشوائي (MAC Randomization) مفعل", color = KrotyCoralRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "قد يرفض سيرفر المايكروتك إعادة دخول الكرت إذا تغير عنوان MAC (${telemetry.localMacAddress}). يفضل ضبط MAC الهاتف كعنوان ثابت لهذه الشبكة.",
                                color = KrotyTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            LiveSessionAndBalanceCard(
                telemetry = telemetry,
                activeVoucher = activeVoucher,
                portalState = portalState,
                onOpenLoginPortal = {
                    viewModel.openRealPortal(targetPath = "/login", autoLoginVoucher = activeVoucher)
                },
                onOpenStatusPortal = {
                    viewModel.openRealPortal(targetPath = "/status")
                },
                onFreezeSession = {
                    viewModel.freezeActiveSessionNow()
                }
            )
        }

        item {
            PortalQuickActionsCard(
                gatewayIp = telemetry.gatewayIp,
                onOpenCustomUrl = { path -> viewModel.openRealPortal(targetPath = path) },
                onTouchToLearn = {
                    viewModel.openRealPortal(targetPath = "/login", enableTouchToLearn = true)
                },
                onRunWorkerSync = { viewModel.triggerBackgroundWorkerNow() }
            )
        }
    }

    if (portalState.isPortalDialogOpen) {
        RealPortalWebViewDialog(
            viewModel = viewModel,
            telemetry = telemetry,
            portalState = portalState,
            activeVoucher = activeVoucher,
            allVouchers = allVouchers,
            customSelector = currentNetConfig?.voucherFieldSelector ?: ""
        )
    }
}

@Composable
private fun LiveWifiRadarCard(
    telemetry: WifiTelemetry,
    isProbing: Boolean,
    onRefreshRadar: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, KrotyCardBorder, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (telemetry.isWifiConnected) KrotyCyan.copy(alpha = 0.15f) else KrotyCoralRed.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = if (telemetry.isWifiConnected) KrotyCyan else KrotyCoralRed
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = telemetry.ssid,
                            color = KrotyTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (telemetry.gatewayIp.isNotBlank()) {
                                "البوابة المكتشفة: ${telemetry.gatewayIp}"
                            } else {
                                "بانتظار اكتشاف بوابة الشبكة..."
                            },
                            color = KrotyCyan,
                            fontSize = 13.sp
                        )
                    }
                }

                IconButton(onClick = onRefreshRadar) {
                    if (isProbing) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = KrotyCyan, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "مزامنة", tint = KrotyCyan)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = KrotyCardBorder)
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RadarMetricItem("قوة الإشارة", "${telemetry.signalQualityPercent}% (${telemetry.rssiDbm}dBm)", KrotyEmerald)
                RadarMetricItem(
                    "بنق الراوتر",
                    if (telemetry.gatewayPingMs >= 0) "${telemetry.gatewayPingMs} ms" else "--",
                    KrotyCyan
                )
                RadarMetricItem(
                    "بنق الإنترنت",
                    if (telemetry.internetPingMs >= 0) "${telemetry.internetPingMs} ms" else "بوابة كابتف",
                    if (telemetry.internetPingMs >= 0) KrotyEmerald else KrotyAmber
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "نقطة الوصول (BSSID): ${telemetry.bssid} • سرعة الربط: ${telemetry.linkSpeedMbps} Mbps",
                color = KrotyTextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun RadarMetricItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = KrotyTextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun LiveSessionAndBalanceCard(
    telemetry: WifiTelemetry,
    activeVoucher: VoucherEntity?,
    portalState: PortalUiState,
    onOpenLoginPortal: () -> Unit,
    onOpenStatusPortal: () -> Unit,
    onFreezeSession: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, KrotyCardBorder, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الجلسة الحية والرصيد الحقيقي",
                    color = KrotyTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Surface(
                    color = if (activeVoucher != null) KrotyEmerald.copy(alpha = 0.15f) else KrotyAmber.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (activeVoucher != null) "كرت نشط: ${activeVoucher.code}" else "لا يوجد كرت محفوظ نشط",
                        color = if (activeVoucher != null) KrotyEmerald else KrotyAmber,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("الرصيد المتبقي (من البوابة)", color = KrotyTextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = activeVoucher?.remainingMb?.let { String.format("%.1f MB", it) }
                            ?: portalState.liveRemainingMb,
                        color = KrotyCyan,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("الوقت المتبقي", color = KrotyTextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = portalState.liveRemainingTime,
                        color = KrotyEmerald,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenLoginPortal,
                    enabled = telemetry.gatewayIp.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = KrotyCyan),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Login, contentDescription = null, tint = KrotyDarkBg, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("بوابة الدخول", color = KrotyDarkBg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onOpenStatusPortal,
                    enabled = telemetry.gatewayIp.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Analytics, contentDescription = null, tint = KrotyCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("صفحة الرصيد", color = KrotyCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onFreezeSession,
                enabled = telemetry.gatewayIp.isNotBlank(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = KrotyAmber),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PauseCircle, contentDescription = null, tint = KrotyAmber)
                Spacer(modifier = Modifier.width(8.dp))
                Text("تجميد الكرت مؤقتاً (تسجيل خروج /logout لحفظ الرصيد)", color = KrotyAmber, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PortalQuickActionsCard(
    gatewayIp: String,
    onOpenCustomUrl: (String) -> Unit,
    onTouchToLearn: () -> Unit,
    onRunWorkerSync: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, KrotyCardBorder, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "أدوات التحكم المتقدمة بالبوابة (${gatewayIp.ifBlank { "غير متصل" }})",
                color = KrotyTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onTouchToLearn,
                    enabled = gatewayIp.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تحديد الحقل باللمس", fontSize = 12.sp)
                }
                FilledTonalButton(
                    onClick = onRunWorkerSync,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تحديث الإشعار والويدجت", fontSize = 12.sp)
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RealPortalWebViewDialog(
    viewModel: KrotyViewModel,
    telemetry: WifiTelemetry,
    portalState: PortalUiState,
    activeVoucher: VoucherEntity?,
    allVouchers: List<VoucherEntity>,
    customSelector: String
) {
    var urlInput by remember(portalState.currentPortalUrl) { mutableStateOf(portalState.currentPortalUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember { mutableStateOf("بوابة المايكروتك الذكية") }
    var loadProgress by remember { mutableIntStateOf(0) }
    var isDesktopMode by remember { mutableStateOf(false) }
    var showQuickVoucherDrawer by remember { mutableStateOf(false) }
    var manualQuickCode by remember(portalState.pendingAutoLoginCode, activeVoucher) {
        mutableStateOf(portalState.pendingAutoLoginCode ?: activeVoucher?.code ?: "")
    }
    var hasLoadError by remember { mutableStateOf(false) }
    var loadErrorDescription by remember { mutableStateOf("") }

    val effectiveHost = portalState.activePortalHost.ifBlank { telemetry.gatewayIp }

    Dialog(
        onDismissRequest = { viewModel.closePortalDialog() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = KrotyDarkBg
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Cyber Cockpit Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF162442), KrotyCardBg)
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(KrotyCyan.copy(alpha = 0.16f))
                                    .border(1.dp, KrotyCyan.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Router,
                                    contentDescription = null,
                                    tint = KrotyCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = telemetry.ssid,
                                        color = KrotyTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = KrotyCyan.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = effectiveHost,
                                            color = KrotyCyan,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "$pageTitle • الرصيد: ${portalState.liveRemainingMb} • الوقت: ${portalState.liveRemainingTime}",
                                    color = KrotyEmerald,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalIconButton(
                                onClick = { showQuickVoucherDrawer = !showQuickVoucherDrawer },
                                modifier = Modifier.size(34.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (showQuickVoucherDrawer) KrotyCyan else Color(0xFF1E2D4A)
                                )
                            ) {
                                Icon(
                                    Icons.Default.Key,
                                    contentDescription = "تبديل الكرت",
                                    tint = if (showQuickVoucherDrawer) KrotyDarkBg else KrotyCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            FilledTonalIconButton(
                                onClick = { viewModel.closePortalDialog() },
                                modifier = Modifier.size(34.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = Color(0xFF331B24)
                                )
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "إغلاق البوابة",
                                    tint = KrotyCoralRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Collapsible Quick Voucher Switcher & Auto-Injector Drawer
                    AnimatedVisibility(visible = showQuickVoucherDrawer) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1628)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .border(1.dp, KrotyCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    "حقن وتبديل الكرت الفوري داخل البوابة:",
                                    color = KrotyCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = manualQuickCode,
                                        onValueChange = { manualQuickCode = it },
                                        placeholder = { Text("اكتب أو اختر رقم الكرت...", fontSize = 12.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        textStyle = LocalTextStyle.current.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            if (manualQuickCode.isNotBlank()) {
                                                viewModel.setPendingAutoLoginCode(manualQuickCode)
                                                val script = MikroTikJsScripts.buildAutoLoginScript(
                                                    voucherCode = manualQuickCode.trim(),
                                                    password = "",
                                                    customSelector = customSelector
                                                )
                                                webViewRef?.evaluateJavascript(script, null)
                                                showQuickVoucherDrawer = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = KrotyEmerald)
                                    ) {
                                        Icon(Icons.Default.Bolt, contentDescription = null, tint = KrotyDarkBg, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("حقن ودخول", color = KrotyDarkBg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }

                                if (allVouchers.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(allVouchers.take(10)) { v ->
                                            FilterChip(
                                                selected = manualQuickCode == v.code,
                                                onClick = {
                                                    manualQuickCode = v.code
                                                    viewModel.setPendingAutoLoginCode(v.code)
                                                },
                                                label = {
                                                    Text(
                                                        "${v.code} (${v.categoryLabel})",
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. Modern Browser Navigation & URL Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { if (webViewRef?.canGoBack() == true) webViewRef?.goBack() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "رجوع", tint = KrotyTextPrimary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = {
                                hasLoadError = false
                                webViewRef?.reload()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = KrotyCyan, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = {
                                isDesktopMode = !isDesktopMode
                                webViewRef?.settings?.apply {
                                    userAgentString = if (isDesktopMode) {
                                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                                    } else {
                                        null
                                    }
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                }
                                webViewRef?.reload()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (isDesktopMode) Icons.Default.DesktopWindows else Icons.Default.PhoneAndroid,
                                contentDescription = "وضع العرض",
                                tint = if (isDesktopMode) KrotyAmber else KrotyTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Surface(
                            color = Color(0xFF0A1020),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, KrotyCardBorder, RoundedCornerShape(10.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Language,
                                    contentDescription = null,
                                    tint = KrotyCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                androidx.compose.foundation.text.BasicTextField(
                                    value = urlInput,
                                    onValueChange = { urlInput = it },
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(
                                        color = KrotyTextPrimary,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                    keyboardActions = KeyboardActions(
                                        onGo = {
                                            val target = if (urlInput.startsWith("http")) urlInput else "http://$urlInput"
                                            hasLoadError = false
                                            webViewRef?.loadUrl(target)
                                        }
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val target = if (urlInput.startsWith("http")) urlInput else "http://$urlInput"
                                hasLoadError = false
                                webViewRef?.loadUrl(target)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KrotyCyan),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("فتح", color = KrotyDarkBg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3. Scrollable Smart Action Ribbon (Never wraps or clips!)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            PortalActionChip(
                                label = "⚡ دخول فوري بالكرت",
                                color = KrotyEmerald,
                                onClick = {
                                    val codeToInject = manualQuickCode.ifBlank {
                                        portalState.pendingAutoLoginCode ?: activeVoucher?.code ?: ""
                                    }
                                    if (codeToInject.isNotBlank()) {
                                        val script = MikroTikJsScripts.buildAutoLoginScript(
                                            voucherCode = codeToInject,
                                            password = portalState.pendingAutoLoginPassword,
                                            customSelector = customSelector
                                        )
                                        webViewRef?.evaluateJavascript(script, null)
                                    } else {
                                        showQuickVoucherDrawer = true
                                    }
                                }
                            )
                        }
                        item {
                            PortalActionChip(
                                label = "📊 الرصيد (/status)",
                                color = KrotyCyan,
                                onClick = {
                                    val target = "http://$effectiveHost/status"
                                    urlInput = target
                                    hasLoadError = false
                                    webViewRef?.loadUrl(target)
                                }
                            )
                        }
                        item {
                            PortalActionChip(
                                label = "🔑 الدخول (/login)",
                                color = KrotyTextPrimary,
                                onClick = {
                                    val target = "http://$effectiveHost/login"
                                    urlInput = target
                                    hasLoadError = false
                                    webViewRef?.loadUrl(target)
                                }
                            )
                        }
                        item {
                            PortalActionChip(
                                label = "❄️ تجميد الكرت (/logout)",
                                color = KrotyAmber,
                                onClick = {
                                    val target = "http://$effectiveHost/logout"
                                    urlInput = target
                                    hasLoadError = false
                                    webViewRef?.loadUrl(target)
                                }
                            )
                        }
                        item {
                            PortalActionChip(
                                label = if (portalState.touchToLearnMode) "🎯 جاري تعلّم الحقل..." else "🎯 تحديد الحقل باللمس",
                                color = if (portalState.touchToLearnMode) KrotyEmerald else Color(0xFFB388FF),
                                onClick = {
                                    viewModel.toggleTouchToLearnMode(true)
                                    webViewRef?.evaluateJavascript(MikroTikJsScripts.buildTouchToLearnScript(), null)
                                }
                            )
                        }
                        item {
                            PortalActionChip(
                                label = "🌐 كشف تحويل الشبكة",
                                color = KrotyCyan,
                                onClick = {
                                    val fallback = "http://${telemetry.gatewayIp.ifBlank { "10.0.0.1" }}/"
                                    urlInput = fallback
                                    hasLoadError = false
                                    webViewRef?.loadUrl(fallback)
                                }
                            )
                        }
                    }
                }

                // Loading Progress Bar
                if (loadProgress in 1..99) {
                    LinearProgressIndicator(
                        progress = { loadProgress / 100f },
                        color = KrotyCyan,
                        trackColor = KrotyCardBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                    )
                } else {
                    HorizontalDivider(color = KrotyCardBorder)
                }

                // Touch-to-Learn Instruction Banner
                if (portalState.touchToLearnMode) {
                    Surface(
                        color = Color(0xFF281847),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.TouchApp, contentDescription = null, tint = Color(0xFFD1C4E9), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "وضع التعلّم باللمس نشط: المس مربع إدخال رقم الكرت داخل الصفحة ليتم حفظه تلقائياً لهذه الشبكة.",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.toggleTouchToLearnMode(false) }) {
                                Text("إلغاء", color = KrotyAmber, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Status / Error Banner inside Portal
                portalState.statusBannerMessage?.let { banner ->
                    Surface(
                        color = Color(0xFF102838),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = KrotyCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = banner,
                                color = KrotyCyan,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearBanner() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = KrotyTextSecondary, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                // 4. Main WebView Container with Smart Recovery Overlay
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            viewModel.bindWifiForPortal()
                            WebView(ctx).apply {
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.LoadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                settings.cacheMode = WebSettings.LOAD_DEFAULT
                                settings.javaScriptCanOpenWindowsAutomatically = true

                                addJavascriptInterface(
                                    object {
                                        @JavascriptInterface
                                        fun onHtmlExtracted(url: String, html: String) {
                                            viewModel.onWebViewHtmlExtracted(url, html)
                                        }

                                        @JavascriptInterface
                                        fun onFieldSelectorLearned(selector: String) {
                                            viewModel.saveCustomFieldSelector(selector)
                                        }

                                        @JavascriptInterface
                                        fun onMaxLengthDetected(maxLen: Int) {
                                            // Auto-detected voucher field maxLength
                                        }

                                        @JavascriptInterface
                                        fun onAutoLoginInjected(code: String) {
                                            // Auto-login script injected
                                        }
                                    },
                                    "KrotyBridge"
                                )

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        loadProgress = newProgress
                                    }

                                    override fun onReceivedTitle(view: WebView?, title: String?) {
                                        if (!title.isNullOrBlank()) {
                                            pageTitle = title
                                        }
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        if (!url.isNullOrBlank()) {
                                            urlInput = url
                                            Uri.parse(url).host?.let { host ->
                                                viewModel.updateActivePortalHost(host)
                                            }
                                        }
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        val currentUrl = url ?: ""
                                        urlInput = currentUrl

                                        Uri.parse(currentUrl).host?.let { host ->
                                            viewModel.updateActivePortalHost(host)
                                        }

                                        view?.evaluateJavascript(
                                            "(function(){ if(window.KrotyBridge && document.documentElement){ window.KrotyBridge.onHtmlExtracted(window.location.href, document.documentElement.outerHTML); } })();",
                                            null
                                        )
                                        view?.evaluateJavascript(
                                            MikroTikJsScripts.buildInputMaxLengthDetectorScript(),
                                            null
                                        )

                                        if (portalState.touchToLearnMode) {
                                            view?.evaluateJavascript(
                                                MikroTikJsScripts.buildTouchToLearnScript(),
                                                null
                                            )
                                        }

                                        val pendingCode = portalState.pendingAutoLoginCode
                                        if (!pendingCode.isNullOrBlank() && !currentUrl.contains("/status")) {
                                            val script = MikroTikJsScripts.buildAutoLoginScript(
                                                voucherCode = pendingCode,
                                                password = portalState.pendingAutoLoginPassword,
                                                customSelector = customSelector
                                            )
                                            view?.evaluateJavascript(script, null)
                                        }
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        super.onReceivedError(view, request, error)
                                        if (request?.isForMainFrame == true) {
                                            hasLoadError = true
                                            loadErrorDescription = error?.description?.toString() ?: "تعذر الوصول لبوابة الشبكة"
                                        }
                                    }
                                }

                                loadUrl(portalState.currentPortalUrl)
                                webViewRef = this
                            }
                        }
                    )

                    if (hasLoadError) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(KrotyDarkBg.copy(alpha = 0.95f))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, KrotyCardBorder, RoundedCornerShape(18.dp))
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.WifiFind,
                                        contentDescription = null,
                                        tint = KrotyAmber,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        "تعذر فتح الرابط المباشر للبوابة",
                                        color = KrotyTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "بعض شبكات المايكروتك تستخدم اسم نطاق خاص بدلاً من الرقم ($effectiveHost). اضغط على الزر أدناه لاكتشاف صفحة التحويل تلقائياً.",
                                        color = KrotyTextSecondary,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        loadErrorDescription,
                                        color = KrotyCoralRed,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = {
                                                hasLoadError = false
                                                viewModel.bindWifiForPortal()
                                                webViewRef?.loadUrl("http://${telemetry.gatewayIp.ifBlank { "10.0.0.1" }}/login")
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = KrotyCyan)
                                        ) {
                                            Text("إعادة المحاولة", color = KrotyDarkBg, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                hasLoadError = false
                                                viewModel.bindWifiForPortal()
                                                webViewRef?.loadUrl("http://neverssl.com")
                                            }
                                        ) {
                                            Text("كشف التحويل التلقائي", color = KrotyEmerald)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PortalActionChip(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
""")

# 7. ScannerScreen.kt (With Prominent Flash & Gallery Buttons, LazyRow Filter Chips & COMPATIBLE CameraX Viewfinder)
write_file("app/src/main/java/com/example/ui/screens/ScannerScreen.kt", r"""
package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.util.Size
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.domain.scanner.KrotyCameraAnalyzer
import com.example.ui.KrotyViewModel
import com.example.ui.theme.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

@Composable
fun ScannerScreen(
    viewModel: KrotyViewModel,
    onNavigateToRadar: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val appContainer = viewModel.container

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var scannedCode by remember { mutableStateOf("") }
    var voucherPassword by remember { mutableStateOf("") }
    var voucherPrice by remember { mutableStateOf("") }
    var voucherMb by remember { mutableStateOf("") }
    var targetLength by remember { mutableIntStateOf(0) }
    var torchEnabled by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1.0f) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var autoConnectOnScan by remember { mutableStateOf(false) }
    var scanStatusMessage by remember { mutableStateOf<String?>(null) }
    var isAnalyzingGallery by remember { mutableStateOf(false) }
    var cameraRebindKey by remember { mutableIntStateOf(0) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val krotyAnalyzer = remember {
        KrotyCameraAnalyzer(
            ocrParser = appContainer.ocrParser,
            consensusTracker = appContainer.consensusTracker,
            onVoucherLocked = { lockedCode ->
                if (scannedCode != lockedCode) {
                    scannedCode = lockedCode
                    scanStatusMessage = "✅ تم التقاط رقم الكرت بالكاميرا: $lockedCode"
                    try {
                        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
                            .startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                    } catch (_: Exception) {}

                    if (autoConnectOnScan) {
                        viewModel.saveAndConnectVoucher(
                            code = lockedCode,
                            connectImmediately = true
                        )
                        onNavigateToRadar()
                    }
                }
            }
        )
    }

    LaunchedEffect(targetLength) {
        krotyAnalyzer.targetCodeLength = targetLength
        appContainer.consensusTracker.reset()
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isAnalyzingGallery = true
            scanStatusMessage = "⏳ جاري قراءة صورة الكرت من المعرض بالذكاء الاصطناعي..."
            try {
                val inputImage = InputImage.fromFilePath(context, uri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val barcodeScanner = BarcodeScanning.getClient()

                barcodeScanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        val qr = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                        if (!qr.isNullOrBlank()) {
                            val cleaned = qr.trim()
                            scannedCode = cleaned
                            isAnalyzingGallery = false
                            scanStatusMessage = "✅ تم استخراج الكود من باركود الصورة: $cleaned"
                            if (autoConnectOnScan) {
                                viewModel.saveAndConnectVoucher(code = cleaned, connectImmediately = true)
                                onNavigateToRadar()
                            }
                        } else {
                            recognizer.process(inputImage)
                                .addOnSuccessListener { visionText ->
                                    val best = appContainer.ocrParser.extractBestCandidate(
                                        visionText = visionText,
                                        targetLength = targetLength,
                                        customRegex = null
                                    )
                                    val fallbackCode = if (best == null) {
                                        val normalized = appContainer.ocrParser.normalizeOpticalMistakes(visionText.text)
                                        Regex("\\b\\d{5,16}\\b").findAll(normalized)
                                            .map { it.value }
                                            .firstOrNull { candidate ->
                                                targetLength <= 0 || candidate.length == targetLength
                                            }
                                    } else null

                                    val finalCode = best?.code ?: fallbackCode
                                    isAnalyzingGallery = false
                                    if (!finalCode.isNullOrBlank()) {
                                        scannedCode = finalCode
                                        scanStatusMessage = "✅ تم استخراج رقم الكرت من صورة المعرض: $finalCode"
                                        try {
                                            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
                                                .startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                                        } catch (_: Exception) {}
                                        if (autoConnectOnScan) {
                                            viewModel.saveAndConnectVoucher(code = finalCode, connectImmediately = true)
                                            onNavigateToRadar()
                                        }
                                    } else {
                                        scanStatusMessage = "⚠️ لم يتم العثور على أرقام واضحة في الصورة. جرب تقريب الصورة أو تحديد طول الكرت."
                                    }
                                }
                                .addOnFailureListener {
                                    isAnalyzingGallery = false
                                    scanStatusMessage = "❌ تعذر تحليل النص في الصورة المختارة."
                                }
                        }
                    }
                    .addOnFailureListener {
                        isAnalyzingGallery = false
                        scanStatusMessage = "❌ تعذر قراءة الصورة من المعرض."
                    }
            } catch (_: Exception) {
                isAnalyzingGallery = false
                scanStatusMessage = "❌ حدث خطأ أثناء فتح الصورة من المعرض."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KrotyDarkBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Live Camera & AI Scanner Card
        Card(
            colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, KrotyCardBorder, RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Header Row with weight(1f) so buttons NEVER get pushed off-screen!
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = KrotyCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "الماسح الذكي للكروت (AI Camera)",
                            color = KrotyCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = {
                            appContainer.consensusTracker.reset()
                            cameraRebindKey++
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "إعادة تنشيط الكاميرا",
                            tint = KrotyTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Camera Viewfinder Box (Uses COMPATIBLE TextureView for 100% Xiaomi/Android reliability)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(235.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .border(2.dp, KrotyCyan.copy(alpha = 0.7f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraPermission) {
                        key(cameraRebindKey) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx).apply {
                                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                        scaleType = PreviewView.ScaleType.FILL_CENTER
                                    }
                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                    cameraProviderFuture.addListener({
                                        try {
                                            val cameraProvider = cameraProviderFuture.get()
                                            val preview = Preview.Builder().build().also {
                                                it.setSurfaceProvider(previewView.surfaceProvider)
                                            }
                                            val imageAnalysis = ImageAnalysis.Builder()
                                                .setTargetResolution(Size(1280, 720))
                                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                                .build()
                                                .also {
                                                    it.setAnalyzer(cameraExecutor, krotyAnalyzer)
                                                }

                                            cameraProvider.unbindAll()
                                            val cam = cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                CameraSelector.DEFAULT_BACK_CAMERA,
                                                preview,
                                                imageAnalysis
                                            )
                                            cameraRef = cam
                                        } catch (_: Exception) {
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))
                                    previewView
                                }
                            )
                        }

                        // Target Reticle Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.84f)
                                .height(88.dp)
                                .border(2.dp, KrotyEmerald, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .height(1.5.dp)
                                    .background(KrotyCyan.copy(alpha = 0.65f))
                            )
                        }

                        Surface(
                            color = KrotyDarkBg.copy(alpha = 0.78f),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp)
                        ) {
                            Text(
                                text = "وجّه الكاميرا نحو أرقام الكرت أو الباركود",
                                color = KrotyCyan,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                            )
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = KrotyAmber,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "صلاحية الكاميرا مطلوبة لتشغيل الماسح الضوئي الحي",
                                color = KrotyTextPrimary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = KrotyCyan)
                            ) {
                                Text("تفعيل الكاميرا الآن", color = KrotyDarkBg, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // PROMINENT FLASH & GALLERY CONTROL BAR (Clear Arabic Buttons + Icons!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val newState = !torchEnabled
                            torchEnabled = newState
                            cameraRef?.cameraControl?.enableTorch(newState)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (torchEnabled) KrotyAmber else Color(0xFF1F2E4D)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Icon(
                            imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "الفلاش",
                            tint = if (torchEnabled) KrotyDarkBg else KrotyAmber
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (torchEnabled) "إطفاء الفلاش" else "تشغيل الفلاش",
                            color = if (torchEnabled) KrotyDarkBg else KrotyTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1A3648)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        if (isAnalyzingGallery) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = KrotyCyan,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = "المعرض",
                                tint = KrotyCyan
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "صورة من المعرض",
                            color = KrotyCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                // Status feedback banner for camera / gallery OCR
                scanStatusMessage?.let { statusMsg ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFF0E2238),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, KrotyCyan.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    ) {
                        Text(
                            text = statusMsg,
                            color = KrotyEmerald,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Zoom Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "تقريب العدسة: ${String.format("%.1fx", zoomRatio)}",
                        color = KrotyTextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Slider(
                        value = zoomRatio,
                        onValueChange = {
                            zoomRatio = it
                            cameraRef?.cameraControl?.setZoomRatio(it)
                        },
                        valueRange = 1.0f..3.0f,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Length Filter Chips inside LazyRow so "12 رقم" NEVER wraps vertically!
                val lengthOptions = listOf(0, 6, 8, 10, 12)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(lengthOptions) { len ->
                        FilterChip(
                            selected = targetLength == len,
                            onClick = { targetLength = len },
                            label = {
                                Text(
                                    text = if (len == 0) "تلقائي (كل الأطوال)" else "$len أرقام",
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "الاتصال التلقائي بالبوابة فور التقاط الكرت ⚡",
                        color = KrotyTextPrimary,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = autoConnectOnScan,
                        onCheckedChange = { autoConnectOnScan = it }
                    )
                }
            }
        }

        // 2. Extracted Voucher & Instant Execution Card
        Card(
            colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, KrotyCardBorder, RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "بيانات الكرت المستخرج والتنفيذ الفوري",
                    color = KrotyTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = scannedCode,
                    onValueChange = { scannedCode = it },
                    label = { Text("رقم الكرت (يُملأ تلقائياً من الكاميرا أو المعرض أو يدوياً)") },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = KrotyEmerald
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = voucherPassword,
                    onValueChange = { voucherPassword = it },
                    label = { Text("كلمة المرور (اتركه فارغاً لكروت المايكروتك العادية)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = voucherPrice,
                        onValueChange = { voucherPrice = it },
                        label = { Text("السعر (اختياري)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = voucherMb,
                        onValueChange = { voucherMb = it },
                        label = { Text("الباقة MB (اختياري)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.saveAndConnectVoucher(
                                code = scannedCode,
                                password = voucherPassword,
                                priceRials = voucherPrice.toDoubleOrNull() ?: 0.0,
                                initialMb = voucherMb.toDoubleOrNull() ?: 0.0,
                                connectImmediately = true
                            )
                            onNavigateToRadar()
                        },
                        enabled = scannedCode.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = KrotyEmerald),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = KrotyDarkBg)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اتصال فوري بالبوابة", color = KrotyDarkBg, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.saveAndConnectVoucher(
                                code = scannedCode,
                                password = voucherPassword,
                                priceRials = voucherPrice.toDoubleOrNull() ?: 0.0,
                                initialMb = voucherMb.toDoubleOrNull() ?: 0.0,
                                connectImmediately = false
                            )
                            scannedCode = ""
                            appContainer.consensusTracker.reset()
                        },
                        enabled = scannedCode.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = KrotyCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("حفظ في المحفظة", color = KrotyCyan)
                    }
                }
            }
        }
    }
}
""")

# 8. WalletScreen.kt
write_file("app/src/main/java/com/example/ui/screens/WalletScreen.kt", r"""
package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.entity.VoucherEntity
import com.example.data.local.entity.VoucherStatus
import com.example.ui.KrotyViewModel
import com.example.ui.theme.*

@Composable
fun WalletScreen(
    viewModel: KrotyViewModel,
    isAuthenticated: Boolean,
    onRequestBiometricAuth: () -> Unit,
    onNavigateToRadar: () -> Unit
) {
    val context = LocalContext.current
    val vouchers by viewModel.allVouchers.collectAsState()
    val prefs by viewModel.preferences.collectAsState()

    var maskCodes by remember(prefs.maskVoucherCodes) { mutableStateOf(prefs.maskVoucherCodes) }
    var selectedQrVoucher by remember { mutableStateOf<VoucherEntity?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    if (prefs.biometricLockEnabled && !isAuthenticated) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(KrotyDarkBg),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = KrotyCyan, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("خزنة الكروت محمية بالبصمة", color = KrotyTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("يرجى تأكيد هويتك لعرض الكروت المحفوظة والاحتياطية.", color = KrotyTextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onRequestBiometricAuth,
                        colors = ButtonDefaults.buttonColors(containerColor = KrotyCyan)
                    ) {
                        Text("فتح القفل بالبصمة", color = KrotyDarkBg, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    val totalSpent = vouchers.sumOf { it.priceRials }
    val activeCount = vouchers.count { it.status == VoucherStatus.ACTIVE }
    val readyCount = vouchers.count { it.status == VoucherStatus.NEW || it.status == VoucherStatus.PAUSED }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(KrotyDarkBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, KrotyCardBorder, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("محفظة الكروت والتحليل المالي", color = KrotyTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        IconButton(onClick = { maskCodes = !maskCodes }) {
                            Icon(
                                imageVector = if (maskCodes) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "إخفاء/إظهار",
                                tint = KrotyCyan
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        WalletStatColumn("إجمالي الكروت", "${vouchers.size}", KrotyCyan)
                        WalletStatColumn("جاهز بالاحتياط", "$readyCount", KrotyEmerald)
                        WalletStatColumn("نشط حالياً", "$activeCount", KrotyAmber)
                        WalletStatColumn("إجمالي التكلفة", "${totalSpent.toInt()} ريال", KrotyTextPrimary)
                    }
                }
            }
        }

        if (vouchers.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = KrotyTextSecondary, modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("المحفظة فارغة حالياً", color = KrotyTextPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            "لا توجد أي كروت وهمية! قم بمسح كرت جديد بالكاميرا أو إضافته ليظهر هنا.",
                            color = KrotyTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            items(vouchers, key = { it.id }) { voucher ->
                VoucherWalletItemCard(
                    voucher = voucher,
                    maskCode = maskCodes,
                    onConnectNow = {
                        viewModel.activateAndConnectFromWallet(voucher)
                        onNavigateToRadar()
                    },
                    onCopy = { viewModel.copyVoucherSecurely(voucher.code) },
                    onShareQr = {
                        selectedQrVoucher = voucher
                        qrBitmap = viewModel.container.qrGenerator.generateQrBitmap(voucher.code)
                    },
                    onShareText = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "كود الكرت: ${voucher.code}\nالفئة: ${voucher.categoryLabel}\nالشبكة: ${voucher.ssid}"
                            )
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "مشاركة الكرت"))
                    },
                    onDelete = { viewModel.deleteVoucher(voucher) }
                )
            }
        }
    }

    if (selectedQrVoucher != null && qrBitmap != null) {
        Dialog(onDismissRequest = { selectedQrVoucher = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("مشاركة الكرت عبر QR", color = KrotyCyan, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(selectedQrVoucher!!.code, color = KrotyTextPrimary, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(14.dp))
                    Image(
                        bitmap = qrBitmap!!.asImageBitmap(),
                        contentDescription = "QR",
                        modifier = Modifier
                            .size(220.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(onClick = { selectedQrVoucher = null }) {
                        Text("إغلاق")
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletStatColumn(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = KrotyTextSecondary, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun VoucherWalletItemCard(
    voucher: VoucherEntity,
    maskCode: Boolean,
    onConnectNow: () -> Unit,
    onCopy: () -> Unit,
    onShareQr: () -> Unit,
    onShareText: () -> Unit,
    onDelete: () -> Unit
) {
    val displayedCode = if (maskCode && voucher.code.length > 4) {
        "••••" + voucher.code.takeLast(3)
    } else {
        voucher.code
    }

    val statusColor = when (voucher.status) {
        VoucherStatus.ACTIVE -> KrotyEmerald
        VoucherStatus.NEW -> KrotyCyan
        VoucherStatus.PAUSED -> KrotyAmber
        VoucherStatus.EXHAUSTED -> KrotyCoralRed
    }

    val statusLabel = when (voucher.status) {
        VoucherStatus.ACTIVE -> "نشط الآن"
        VoucherStatus.NEW -> "احتياطي جاهز"
        VoucherStatus.PAUSED -> "مجمد مؤقتاً"
        VoucherStatus.EXHAUSTED -> "منتهي"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayedCode,
                    color = KrotyTextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "الشبكة: ${voucher.ssid} • الفئة: ${voucher.categoryLabel}" +
                    (voucher.remainingMb?.let { " • المتبقي: ${String.format("%.1f MB", it)}" } ?: ""),
                color = KrotyTextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onConnectNow,
                    colors = ButtonDefaults.buttonColors(containerColor = KrotyEmerald)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = KrotyDarkBg, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("اتصال بالبوابة", color = KrotyDarkBg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Row {
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = KrotyCyan)
                    }
                    IconButton(onClick = onShareQr) {
                        Icon(Icons.Default.QrCode, contentDescription = "QR", tint = KrotyCyan)
                    }
                    IconButton(onClick = onShareText) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = KrotyEmerald)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = KrotyCoralRed)
                    }
                }
            }
        }
    }
}
""")

# 9. SettingsScreen.kt
write_file("app/src/main/java/com/example/ui/screens/SettingsScreen.kt", r"""
package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.KrotyViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: KrotyViewModel) {
    val prefs by viewModel.preferences.collectAsState()
    val networks by viewModel.allNetworks.collectAsState()
    val logs by viewModel.recentLogs.collectAsState()
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) viewModel.exportBackup(uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) viewModel.importBackup(uri)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(KrotyDarkBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, KrotyCardBorder, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("الأمان والأتمتة الذكية", color = KrotyCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    SettingToggleRow(
                        title = "قفل محفظة الكروت بالبصمة (Biometric Lock)",
                        subtitle = "حماية الكروت المخزنة ببصمة الإصبع أو الوجه",
                        checked = prefs.biometricLockEnabled,
                        onCheckedChange = {
                            scope.launch { viewModel.container.preferencesRepository.setBiometricLock(it) }
                        }
                    )

                    HorizontalDivider(color = KrotyCardBorder, modifier = Modifier.padding(vertical = 8.dp))

                    SettingToggleRow(
                        title = "إخفاء أرقام الكروت افتراضياً (Privacy Mask)",
                        subtitle = "حماية أرقام الكروت من التلصص على الشاشة",
                        checked = prefs.maskVoucherCodes,
                        onCheckedChange = {
                            scope.launch { viewModel.container.preferencesRepository.setMaskVoucherCodes(it) }
                        }
                    )

                    HorizontalDivider(color = KrotyCardBorder, modifier = Modifier.padding(vertical = 8.dp))

                    SettingToggleRow(
                        title = "تجميد الكرت عند إغلاق الشاشة (Auto-Freeze)",
                        subtitle = "إرسال طلب تسجيل خروج مؤقت لتوفير الرصيد",
                        checked = prefs.autoFreezeOnScreenOff,
                        onCheckedChange = {
                            scope.launch { viewModel.container.preferencesRepository.setAutoFreezeOnScreenOff(it) }
                        }
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, KrotyCardBorder, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("النسخ الاحتياطي والاستعادة (JSON)", color = KrotyCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "احفظ كافة كروتك وإعدادات شبكات المايكروتك في ملف خارجي أو استعدها في أي وقت.",
                        color = KrotyTextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { exportLauncher.launch("kroty_net_backup.json") },
                            colors = ButtonDefaults.buttonColors(containerColor = KrotyCyan),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, tint = KrotyDarkBg)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تصدير الكروت", color = KrotyDarkBg, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = KrotyEmerald)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("استيراد نسخة", color = KrotyEmerald, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, KrotyCardBorder, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("الشبكات المكتشفة والمحفوظة (${networks.size})", color = KrotyCyan, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (networks.isEmpty()) {
                        Text("سيتم حفظ كل شبكة مايكروتك تتصل بها تلقائياً هنا مع بوابتها الخاصة.", color = KrotyTextSecondary, fontSize = 12.sp)
                    } else {
                        networks.forEach { net ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(net.ssid, color = KrotyTextPrimary, fontWeight = FontWeight.Bold)
                                    Text(
                                        "البوابة: ${net.gatewayIp} • الحقل المخصص: ${net.voucherFieldSelector.ifBlank { "تلقائي" }}",
                                        color = KrotyTextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch { viewModel.container.repository.deleteNetwork(net) }
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = KrotyCoralRed)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (logs.isNotEmpty()) {
            item {
                Text("سجل العمليات الحية", color = KrotyTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            items(logs.take(15)) { log ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = KrotyCardBg),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${log.eventType} • ${log.ssid}", color = KrotyCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(log.details, color = KrotyTextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = KrotyTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = KrotyTextSecondary, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
""")

# 10. MainActivity.kt
write_file("app/src/main/java/com/example/MainActivity.kt", r"""
package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.KrotyViewModel
import com.example.ui.screens.RadarScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WalletScreen
import com.example.ui.theme.*

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as KrotyApplication

        setContent {
            KrotyNetTheme {
                val krotyViewModel: KrotyViewModel = viewModel(
                    factory = KrotyViewModel.provideFactory(applicationContext, app.container)
                )

                var selectedTab by remember { mutableIntStateOf(0) }
                var isBiometricAuthenticated by remember { mutableStateOf(false) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    krotyViewModel.container.wifiRadarEngine.refreshSnapshot()
                    krotyViewModel.syncLiveMikroTikSession(silent = true)
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
                    permissionLauncher.launch(perms.toTypedArray())
                }

                Scaffold(
                    containerColor = KrotyDarkBg,
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
                        TopAppBar(
                            title = {
                                Column {
                                    Text(
                                        text = "كروتي نت • Kroty Net",
                                        color = KrotyCyan,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = KrotyCardBg),
                            actions = {
                                IconButton(
                                    onClick = { krotyViewModel.syncLiveMikroTikSession(silent = false) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "مزامنة الجلسة الحية",
                                        tint = KrotyEmerald
                                    )
                                }
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar(containerColor = KrotyCardBg) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Radar, contentDescription = null) },
                                label = { Text("الرادار والبوابة") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                                label = { Text("الماسح") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                                label = { Text("المحفظة") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text("الإعدادات") }
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
                                onRequestLocationEnable = {
                                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                }
                            )
                            1 -> ScannerScreen(
                                viewModel = krotyViewModel,
                                onNavigateToRadar = { selectedTab = 0 }
                            )
                            2 -> WalletScreen(
                                viewModel = krotyViewModel,
                                isAuthenticated = isBiometricAuthenticated,
                                onRequestBiometricAuth = {
                                    authenticateWithBiometrics {
                                        isBiometricAuthenticated = true
                                    }
                                },
                                onNavigateToRadar = { selectedTab = 0 }
                            )
                            3 -> SettingsScreen(viewModel = krotyViewModel)
                        }
                    }
                }
            }
        }
    }

    private fun authenticateWithBiometrics(onSuccess: () -> Unit) {
        val biometricManager = BiometricManager.from(this)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            onSuccess()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("تأكيد الهوية لفتح خزنة الكروت")
            .setSubtitle("استخدم البصمة أو قفل الشاشة لعرض الكروت المحفوظة")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(promptInfo)
    }
}
""")

print("Part 3 generated successfully!")
