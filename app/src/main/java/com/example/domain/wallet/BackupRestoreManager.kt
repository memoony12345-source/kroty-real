package com.example.domain.wallet

import com.example.data.local.entity.NetworkEntity
import com.example.data.local.entity.VoucherEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class KrotyBackupPayload(
    val version: Int = 3,
    val exportedAt: Long = System.currentTimeMillis(),
    val networks: List<NetworkEntity> = emptyList(),
    val vouchers: List<VoucherEntity> = emptyList()
)

object BackupRestoreManager {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun exportToJson(networks: List<NetworkEntity>, vouchers: List<VoucherEntity>): String {
        return json.encodeToString(KrotyBackupPayload(networks = networks, vouchers = vouchers))
    }

    fun importFromJson(rawJson: String): KrotyBackupPayload {
        return json.decodeFromString(rawJson)
    }
}
