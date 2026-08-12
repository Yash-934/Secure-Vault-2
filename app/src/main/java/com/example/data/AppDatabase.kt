package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [VaultItem::class, VaultFolder::class, IntruderLog::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
    abstract fun intruderLogDao(): IntruderLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        @Volatile
        private var DECOY_INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "secure_vault_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun getDecoyDatabase(context: Context): AppDatabase {
            return DECOY_INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "decoy_vault_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                DECOY_INSTANCE = instance
                instance
            }
        }
    }
}
