package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "access_points")
data class AccessPointEntity(
    @PrimaryKey val bssid: String,
    val ssid: String,
    val customName: String? = null,
    val lastSignalRssi: Int = -70,
    val lastSeenAt: Long = System.currentTimeMillis()
)
