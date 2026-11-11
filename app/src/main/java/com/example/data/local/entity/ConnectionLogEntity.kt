package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connection_logs")
data class ConnectionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val networkSsid: String,
    val voucherCodeMasked: String,
    val eventType: String,
    val messageAr: String,
    val timestamp: Long = System.currentTimeMillis()
)
