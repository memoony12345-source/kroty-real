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
