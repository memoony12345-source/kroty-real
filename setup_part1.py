#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Kroty Net v3.0.0-PRO - Part 1: Core Gradle, Manifest, Widget XMLs, Clean Room DB, DataStore, QR & Backup
100% Real Production Code - Zero Mock/Seed Data
"""
import os

def write_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content.strip() + "\n")
    print(f"[PART 1] Created: {path}")

# 1. settings.gradle.kts
write_file("settings.gradle.kts", """
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "KrotyNet"
include(":app")
""")

# 2. gradle.properties
write_file("gradle.properties", """
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
""")

# 3. gradle/libs.versions.toml
write_file("gradle/libs.versions.toml", """
[versions]
agp = "8.7.2"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.27"
coreKtx = "1.15.0"
lifecycle = "2.8.7"
activityCompose = "1.9.3"
fragmentKtx = "1.8.5"
composeBom = "2024.10.01"
room = "2.6.1"
datastore = "1.1.1"
workManager = "2.10.0"
camerax = "1.4.0"
mlkitText = "16.0.1"
mlkitBarcode = "17.3.0"
okhttp = "4.12.0"
coroutines = "1.9.0"
serializationJson = "1.7.3"
biometric = "1.1.0"
zxing = "3.5.3"
guava = "32.1.3-android"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-fragment-ktx = { group = "androidx.fragment", name = "fragment-ktx", version.ref = "fragmentKtx" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }

androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workManager" }
androidx-biometric = { group = "androidx.biometric", name = "biometric", version.ref = "biometric" }

androidx-camera-core = { group = "androidx.camera", name = "camera-core", version.ref = "camerax" }
androidx-camera-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "camerax" }
androidx-camera-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }
androidx-camera-view = { group = "androidx.camera", name = "camera-view", version.ref = "camerax" }
guava = { group = "com.google.guava", name = "guava", version.ref = "guava" }

mlkit-text-recognition = { group = "com.google.mlkit", name = "text-recognition", version.ref = "mlkitText" }
mlkit-barcode-scanning = { group = "com.google.mlkit", name = "barcode-scanning", version.ref = "mlkitBarcode" }

okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serializationJson" }
zxing-core = { group = "com.google.zxing", name = "core", version.ref = "zxing" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
""")

# 4. Root build.gradle.kts
write_file("build.gradle.kts", """
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
""")

# 5. app/build.gradle.kts
write_file("app/build.gradle.kts", """
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aistudio.kurootinet.zxpq"
        minSdk = 24
        targetSdk = 35
        versionCode = 6
        versionName = "3.1.0-REAL"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.biometric)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.guava)

    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.barcode.scanning)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core)
}
""")

# 6. AndroidManifest.xml
write_file("app/src/main/AndroidManifest.xml", """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-feature android:name="android.hardware.camera" android:required="false" />
    <uses-feature android:name="android.hardware.wifi" android:required="false" />

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.USE_BIOMETRIC" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

    <application
        android:name=".KrotyApplication"
        android:allowBackup="true"
        android:icon="@drawable/ic_launcher_foreground"
        android:roundIcon="@drawable/ic_launcher_foreground"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:usesCleartextTraffic="true"
        android:theme="@android:style/Theme.Material.NoActionBar"
        tools:targetApi="35">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.KrotyFreezeTileService"
            android:exported="true"
            android:icon="@drawable/ic_launcher_foreground"
            android:label="تجميد الكرت (كروتي)"
            android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
            <intent-filter>
                <action android:name="android.service.quicksettings.action.QS_TILE" />
            </intent-filter>
        </service>

        <receiver
            android:name=".widget.KrotySmartWidgetProvider"
            android:exported="true"
            android:label="ودجت كروتي نت الذكي">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
                <action android:name="com.example.widget.ACTION_REFRESH_BALANCE" />
                <action android:name="com.example.widget.ACTION_TOGGLE_FREEZE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/kroty_widget_info" />
        </receiver>

    </application>
</manifest>
""")

# 7. Resources: strings.xml, ic_launcher_foreground.xml, widget XMLs
write_file("app/src/main/res/values/strings.xml", """
<resources>
    <string name="app_name">كروتي نت</string>
    <string name="widget_desc">متابعة الرصيد الحقيقي وتجميد الكرت بضغطة واحدة</string>
</resources>
""")

write_file("app/src/main/res/drawable/ic_launcher_foreground.xml", """
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#0F172A"
        android:pathData="M0,0h108v108h-108z"/>
    <path
        android:fillColor="#38BDF8"
        android:pathData="M54,28C38,28 24,36 16,48L24,55C30,45 41,39 54,39C67,39 78,45 84,55L92,48C84,36 70,28 54,28ZM54,48C44,48 35,53 30,61L38,68C42,62 47,59 54,59C61,59 66,62 70,68L78,61C73,53 64,48 54,48ZM54,68C49,68 45,72 45,77C45,82 49,86 54,86C59,86 63,82 63,77C63,72 59,68 54,68Z"/>
</vector>
""")

write_file("app/src/main/res/xml/kroty_widget_info.xml", """
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp"
    android:minHeight="110dp"
    android:updatePeriodMillis="1800000"
    android:initialLayout="@layout/widget_kroty_smart"
    android:description="@string/widget_desc"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen" />
""")

write_file("app/src/main/res/layout/widget_kroty_smart.xml", """
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#0F172A"
    android:orientation="vertical"
    android:padding="12dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical">

        <TextView
            android:id="@+id/widget_network_name"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="كروتي نت - بانتظار الشبكة"
            android:textColor="#38BDF8"
            android:textSize="15sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/widget_status_badge"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="فحص..."
            android:textColor="#10B981"
            android:textSize="12sp"
            android:textStyle="bold" />
    </LinearLayout>

    <TextView
        android:id="@+id/widget_card_info"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="6dp"
        android:text="الكرت النشط: غير مسجل"
        android:textColor="#E2E8F0"
        android:textSize="13sp" />

    <TextView
        android:id="@+id/widget_balance_text"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        android:text="الرصيد المتبقي: اضغط تحديث لقراءة البوابة"
        android:textColor="#F8FAFC"
        android:textSize="14sp"
        android:textStyle="bold" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:orientation="horizontal">

        <TextView
            android:id="@+id/widget_btn_refresh"
            android:layout_width="0dp"
            android:layout_height="34dp"
            android:layout_weight="1"
            android:background="#1E293B"
            android:gravity="center"
            android:text="تحديث الرصيد 🔄"
            android:textColor="#38BDF8"
            android:textSize="12sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/widget_btn_freeze"
            android:layout_width="0dp"
            android:layout_height="34dp"
            android:layout_weight="1"
            android:layout_marginStart="6dp"
            android:background="#1E293B"
            android:gravity="center"
            android:text="تجميد / استئناف ❄️"
            android:textColor="#F59E0B"
            android:textSize="12sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/widget_btn_scan"
            android:layout_width="0dp"
            android:layout_height="34dp"
            android:layout_weight="1"
            android:layout_marginStart="6dp"
            android:background="#0284C7"
            android:gravity="center"
            android:text="مسح كرت 📷"
            android:textColor="#FFFFFF"
            android:textSize="12sp"
            android:textStyle="bold" />
    </LinearLayout>
</LinearLayout>
""")

# 8. Database Entities (100% Real, Serializable for Backup)
write_file("app/src/main/java/com/example/data/local/entity/NetworkEntity.kt", """
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
""")

write_file("app/src/main/java/com/example/data/local/entity/VoucherEntity.kt", """
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
""")

write_file("app/src/main/java/com/example/data/local/entity/AccessPointEntity.kt", """
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
""")

write_file("app/src/main/java/com/example/data/local/entity/ConnectionLogEntity.kt", """
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
""")

# 9. KrotyDao.kt
write_file("app/src/main/java/com/example/data/local/dao/KrotyDao.kt", """
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
""")

# 10. KrotyDatabase.kt (Version 4 - ZERO Seed Data!)
write_file("app/src/main/java/com/example/data/local/KrotyDatabase.kt", """
package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.KrotyDao
import com.example.data.local.entity.*

@Database(
    entities = [
        NetworkEntity::class,
        VoucherEntity::class,
        AccessPointEntity::class,
        ConnectionLogEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class KrotyDatabase : RoomDatabase() {
    abstract fun krotyDao(): KrotyDao

    companion object {
        @Volatile
        private var INSTANCE: KrotyDatabase? = null

        fun getInstance(context: Context): KrotyDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    KrotyDatabase::class.java,
                    "kroty_net_real.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
""")

# 11. KrotyRepository.kt (100% Real Data Operations)
write_file("app/src/main/java/com/example/data/repository/KrotyRepository.kt", """
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
""")

# 12. UserPreferencesRepository.kt
write_file("app/src/main/java/com/example/data/preferences/UserPreferencesRepository.kt", """
package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "kroty_prefs")

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        val GHOST_MODE = booleanPreferencesKey("ghost_mode")
        val DATA_SAVER = booleanPreferencesKey("data_saver")
        val LOW_BALANCE_MB = intPreferencesKey("low_balance_mb")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val ghostModeEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.GHOST_MODE] ?: true }
    val dataSaverEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.DATA_SAVER] ?: true }
    val lowBalanceAlertMb: Flow<Int> = context.dataStore.data.map { it[Keys.LOW_BALANCE_MB] ?: 100 }
    val biometricLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.BIOMETRIC_LOCK] ?: false }
    val themeMode: Flow<String> = context.dataStore.data.map { it[Keys.THEME_MODE] ?: "DARK" }

    suspend fun setGhostMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.GHOST_MODE] = enabled }
    }
    suspend fun setDataSaver(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DATA_SAVER] = enabled }
    }
    suspend fun setLowBalanceAlertMb(mb: Int) {
        context.dataStore.edit { it[Keys.LOW_BALANCE_MB] = mb }
    }
    suspend fun setBiometricLock(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC_LOCK] = enabled }
    }
    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }
}
""")

# 13. QR Code Generator & Backup/Restore Manager
write_file("app/src/main/java/com/example/domain/wallet/VoucherQrGenerator.kt", """
package com.example.domain.wallet

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object VoucherQrGenerator {
    fun generateQrBitmap(code: String, sizePx: Int = 512): Bitmap {
        val content = code.trim().ifEmpty { "0000" }
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }
}
""")

write_file("app/src/main/java/com/example/domain/wallet/BackupRestoreManager.kt", """
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
""")

print("Part 1 generated successfully!")
