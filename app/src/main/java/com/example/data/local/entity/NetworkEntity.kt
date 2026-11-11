package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "networks")
data class NetworkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ssid: String,
    val normalizedSsid: String,
    val gatewayIp: String,
    val loginUrl: String,
    val statusUrl: String,
    val logoutUrl: String,
    val learnedInputSelector: String? = null,
    val learnedPasswordSelector: String? = null,
    val learnedSubmitSelector: String? = null,
    val expectedCardLength: Int? = null,
    val isNumericOnly: Boolean = true,
    val passwordMode: String = "EMPTY",
    val autoLoginEnabled: Boolean = true,
    val autoFailoverEnabled: Boolean = true,
    val lastConnectedTimestamp: Long = System.currentTimeMillis()
)
