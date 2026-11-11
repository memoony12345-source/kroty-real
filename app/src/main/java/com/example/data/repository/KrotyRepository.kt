package com.example.data.repository

import com.example.data.local.dao.KrotyDao
import com.example.data.local.entity.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class KrotyRepository(private val dao: KrotyDao) {
    val allNetworks: Flow<List<NetworkEntity>> = dao.observeAllNetworks()
    val allVouchers: Flow<List<VoucherEntity>> = dao.observeAllVouchers()
    val activeVoucher: Flow<VoucherEntity?> = dao.observeActiveVoucher()
    val logs: Flow<List<ConnectionLogEntity>> = dao.observeLogs()

    fun normalizeSsid(ssid: String): String =
        ssid.replace("\"", "").trim().lowercase()

    suspend fun getOrUpdateDynamicNetwork(
        rawSsid: String,
        gatewayIp: String,
        discoveredLoginUrl: String? = null
    ): NetworkEntity {
        val cleanSsid = rawSsid.replace("\"", "").trim().ifEmpty { "MikroTik-$gatewayIp" }
        val norm = normalizeSsid(cleanSsid)
        val existing = dao.getNetworkBySsid(norm)

        val baseHost = if (!discoveredLoginUrl.isNullOrBlank() && discoveredLoginUrl.startsWith("http")) {
            try {
                val uri = java.net.URI(discoveredLoginUrl)
                "${uri.scheme}://${uri.authority}"
            } catch (_: Exception) {
                "http://$gatewayIp"
            }
        } else {
            "http://$gatewayIp"
        }

        val loginUrl = discoveredLoginUrl?.takeIf { it.startsWith("http") } ?: "$baseHost/login"
        val statusUrl = "$baseHost/status"
        val logoutUrl = "$baseHost/logout"

        val updated = if (existing != null) {
            existing.copy(
                ssid = cleanSsid,
                gatewayIp = gatewayIp,
                loginUrl = loginUrl,
                statusUrl = statusUrl,
                logoutUrl = logoutUrl,
                lastConnectedTimestamp = System.currentTimeMillis()
            )
        } else {
            NetworkEntity(
                ssid = cleanSsid,
                normalizedSsid = norm,
                gatewayIp = gatewayIp,
                loginUrl = loginUrl,
                statusUrl = statusUrl,
                logoutUrl = logoutUrl
            )
        }
        val id = dao.upsertNetwork(updated)
        return if (updated.id == 0L) updated.copy(id = id) else updated
    }

    suspend fun saveNetwork(network: NetworkEntity) {
        dao.upsertNetwork(network)
    }

    suspend fun getLastNetwork(): NetworkEntity? = dao.getLastConnectedNetwork()

    suspend fun getActiveVoucherNow(): VoucherEntity? = dao.getActiveVoucherNow()

    suspend fun getPausedVoucherNow(): VoucherEntity? = dao.getPausedVoucherNow()

    suspend fun getNextQueuedVoucher(): VoucherEntity? = dao.getNextQueuedVoucher()

    suspend fun addOrActivateVoucher(
        code: String,
        password: String = "",
        price: Double? = null,
        quotaMb: Long? = null,
        networkSsid: String = "",
        activateImmediately: Boolean = false
    ): VoucherEntity {
        val cleanCode = code.trim()
        val existing = dao.getVoucherByCode(cleanCode)
        if (activateImmediately) {
            dao.pauseAllActiveVouchers()
        }
        val voucher = existing?.copy(
            status = if (activateImmediately) "ACTIVE" else existing.status,
            price = price ?: existing.price,
            dataQuotaMb = quotaMb ?: existing.dataQuotaMb,
            networkSsid = networkSsid.ifEmpty { existing.networkSsid },
            lastSyncedAt = System.currentTimeMillis()
        ) ?: VoucherEntity(
            networkSsid = networkSsid,
            code = cleanCode,
            password = password,
            price = price,
            dataQuotaMb = quotaMb,
            status = if (activateImmediately) "ACTIVE" else "NEW_QUEUED",
            lastSyncedAt = System.currentTimeMillis()
        )
        val id = dao.upsertVoucher(voucher)
        return if (voucher.id == 0L) voucher.copy(id = id) else voucher
    }

    suspend fun syncLivePortalSession(
        detectedUsername: String?,
        remainingMb: Double?,
        remainingSec: Long?,
        networkSsid: String
    ) {
        val currentActive = dao.getActiveVoucherNow()
        val targetCode = detectedUsername?.trim()?.takeIf { it.isNotEmpty() } ?: currentActive?.code ?: return
        val existing = dao.getVoucherByCode(targetCode) ?: currentActive

        dao.pauseAllActiveVouchers()
        val updated = existing?.copy(
            code = targetCode,
            status = "ACTIVE",
            remainingMegabytes = remainingMb ?: existing.remainingMegabytes,
            remainingTimeSeconds = remainingSec ?: existing.remainingTimeSeconds,
            networkSsid = networkSsid.ifEmpty { existing.networkSsid },
            lastSyncedAt = System.currentTimeMillis()
        ) ?: VoucherEntity(
            networkSsid = networkSsid,
            code = targetCode,
            remainingMegabytes = remainingMb,
            remainingTimeSeconds = remainingSec,
            status = "ACTIVE",
            lastSyncedAt = System.currentTimeMillis()
        )
        dao.upsertVoucher(updated)
    }

    suspend fun updateVoucherStatus(voucher: VoucherEntity, newStatus: String) {
        if (newStatus == "ACTIVE") {
            dao.pauseAllActiveVouchers()
        }
        dao.upsertVoucher(voucher.copy(status = newStatus, lastSyncedAt = System.currentTimeMillis()))
    }

    suspend fun deleteVoucher(voucher: VoucherEntity) = dao.deleteVoucher(voucher)

    suspend fun recordAccessPoint(bssid: String, ssid: String, rssi: Int) {
        if (bssid.isBlank() || bssid == "02:00:00:00:00:00") return
        dao.upsertAccessPoint(
            AccessPointEntity(
                bssid = bssid,
                ssid = ssid,
                lastSignalRssi = rssi,
                lastSeenAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun logEvent(ssid: String, code: String, type: String, messageAr: String) {
        val masked = if (code.length > 4) "${code.take(2)}***${code.takeLast(2)}" else code
        dao.insertLog(
            ConnectionLogEntity(
                networkSsid = ssid,
                voucherCodeMasked = masked,
                eventType = type,
                messageAr = messageAr
            )
        )
    }

    suspend fun clearLogs() = dao.clearLogs()

    suspend fun getAllNetworksSnapshot(): List<NetworkEntity> = allNetworks.first()
    suspend fun getAllVouchersSnapshot(): List<VoucherEntity> = allVouchers.first()
}
