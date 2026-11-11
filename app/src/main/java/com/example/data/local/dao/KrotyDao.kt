package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface KrotyDao {
    @Query("SELECT * FROM networks ORDER BY lastConnectedTimestamp DESC")
    fun observeAllNetworks(): Flow<List<NetworkEntity>>

    @Query("SELECT * FROM networks WHERE normalizedSsid = :normSsid LIMIT 1")
    suspend fun getNetworkBySsid(normSsid: String): NetworkEntity?

    @Query("SELECT * FROM networks ORDER BY lastConnectedTimestamp DESC LIMIT 1")
    suspend fun getLastConnectedNetwork(): NetworkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNetwork(network: NetworkEntity): Long

    @Delete
    suspend fun deleteNetwork(network: NetworkEntity)

    @Query("SELECT * FROM vouchers ORDER BY CASE status WHEN 'ACTIVE' THEN 0 WHEN 'PAUSED' THEN 1 WHEN 'NEW_QUEUED' THEN 2 ELSE 3 END, priorityOrder ASC, createdAt DESC")
    fun observeAllVouchers(): Flow<List<VoucherEntity>>

    @Query("SELECT * FROM vouchers WHERE status = 'ACTIVE' ORDER BY lastSyncedAt DESC LIMIT 1")
    fun observeActiveVoucher(): Flow<VoucherEntity?>

    @Query("SELECT * FROM vouchers WHERE status = 'ACTIVE' ORDER BY lastSyncedAt DESC LIMIT 1")
    suspend fun getActiveVoucherNow(): VoucherEntity?

    @Query("SELECT * FROM vouchers WHERE status = 'PAUSED' ORDER BY lastSyncedAt DESC LIMIT 1")
    suspend fun getPausedVoucherNow(): VoucherEntity?

    @Query("SELECT * FROM vouchers WHERE status = 'NEW_QUEUED' ORDER BY priorityOrder ASC, createdAt ASC LIMIT 1")
    suspend fun getNextQueuedVoucher(): VoucherEntity?

    @Query("SELECT * FROM vouchers WHERE code = :code LIMIT 1")
    suspend fun getVoucherByCode(code: String): VoucherEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVoucher(voucher: VoucherEntity): Long

    @Query("UPDATE vouchers SET status = 'PAUSED' WHERE status = 'ACTIVE'")
    suspend fun pauseAllActiveVouchers()

    @Delete
    suspend fun deleteVoucher(voucher: VoucherEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccessPoint(ap: AccessPointEntity)

    @Query("SELECT * FROM access_points WHERE ssid = :ssid ORDER BY lastSeenAt DESC")
    fun observeAccessPointsForSsid(ssid: String): Flow<List<AccessPointEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ConnectionLogEntity)

    @Query("SELECT * FROM connection_logs ORDER BY timestamp DESC LIMIT 100")
    fun observeLogs(): Flow<List<ConnectionLogEntity>>

    @Query("DELETE FROM connection_logs")
    suspend fun clearLogs()
}
