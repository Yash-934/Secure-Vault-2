package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [VaultItem::class, VaultFolder::class, IntruderLog::class, VaultPassword::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
    abstract fun intruderLogDao(): IntruderLogDao
    abstract fun vaultPasswordDao(): VaultPasswordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        @Volatile
        private var DECOY_INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val appCtx = context.applicationContext
                val db = Room.databaseBuilder(
                    appCtx,
                    AppDatabase::class.java,
                    "secure_vault_db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = db
                db
            }
        }

        fun getDecoyDatabase(context: Context): AppDatabase {
            return DECOY_INSTANCE ?: synchronized(this) {
                val appCtx = context.applicationContext
                val db = Room.databaseBuilder(
                    appCtx,
                    AppDatabase::class.java,
                    "decoy_vault_db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                DECOY_INSTANCE = db
                db
            }
        }
    }
}

