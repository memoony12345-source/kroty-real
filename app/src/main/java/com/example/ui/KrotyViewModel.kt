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
