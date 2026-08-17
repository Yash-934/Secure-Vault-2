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
                val pass = com.example.security.DatabaseKeyManager.getDatabasePassphrase(context)
                val factory = net.sqlcipher.database.SupportFactory(pass)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "secure_vault_db"
                )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                pass.fill(0)
                instance
            }
        }

        fun getDecoyDatabase(context: Context): AppDatabase {
            return DECOY_INSTANCE ?: synchronized(this) {
                val pass = com.example.security.DatabaseKeyManager.getDatabasePassphrase(context)
                // Use a derived/hashed version for the decoy DB or just use the same securely 
                // for simplicity in this implementation (they are stored in different files).
                // Actually, let's just use the same hardware-backed passphrase since it's an offline vault
                val factory = net.sqlcipher.database.SupportFactory(pass)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "decoy_vault_db"
                )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration(true)
                    .build()
                DECOY_INSTANCE = instance
                pass.fill(0)
                instance
            }
        }
    }
}
