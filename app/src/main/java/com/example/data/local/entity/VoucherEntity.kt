package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "vouchers")
data class VoucherEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val networkSsid: String = "",
    val code: String,
    val password: String = "",
    val price: Double? = null,
    val dataQuotaMb: Long? = null,
    val remainingMegabytes: Double? = null,
    val remainingTimeSeconds: Long? = null,
    val status: String = "NEW_QUEUED", // NEW_QUEUED, ACTIVE, PAUSED, EXPIRED_DATA, EXPIRED_TIME, INVALID
    val priorityOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long? = null
)
