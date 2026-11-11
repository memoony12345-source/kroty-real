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
