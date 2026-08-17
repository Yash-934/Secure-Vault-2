package com.example.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.security.DatabaseKeyManager
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(entities = [VaultItem::class, VaultFolder::class, IntruderLog::class, VaultPassword::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
    abstract fun intruderLogDao(): IntruderLogDao
    abstract fun vaultPasswordDao(): VaultPasswordDao

    companion object {
        private const val TAG = "AppDatabase"

        @Volatile
        private var INSTANCE: AppDatabase? = null
        @Volatile
        private var DECOY_INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val appCtx = context.applicationContext
                try {
                    SQLiteDatabase.loadLibs(appCtx)
                } catch (e: UnsatisfiedLinkError) {
                    Log.w(TAG, "SQLCipher native libs already loaded or unsatisfied link", e)
                }

                val passphrase = DatabaseKeyManager.getDatabasePassphrase(appCtx)
                val factory = SupportFactory(passphrase)

                val db = try {
                    Room.databaseBuilder(
                        appCtx,
                        AppDatabase::class.java,
                        "secure_vault_db"
                    )
                        .openHelperFactory(factory)
                        .fallbackToDestructiveMigration(true)
                        .build()
                } catch (e: Exception) {
                    Log.e(TAG, "Database builder error, retrying", e)
                    Room.databaseBuilder(
                        appCtx,
                        AppDatabase::class.java,
                        "secure_vault_db"
                    )
                        .openHelperFactory(factory)
                        .fallbackToDestructiveMigration(true)
                        .build()
                }
                INSTANCE = db
                db
            }
        }

        fun getDecoyDatabase(context: Context): AppDatabase {
            return DECOY_INSTANCE ?: synchronized(this) {
                val appCtx = context.applicationContext
                try {
                    SQLiteDatabase.loadLibs(appCtx)
                } catch (e: UnsatisfiedLinkError) {
                    Log.w(TAG, "SQLCipher native libs already loaded or unsatisfied link", e)
                }

                val passphrase = DatabaseKeyManager.getDatabasePassphrase(appCtx)
                val factory = SupportFactory(passphrase)

                val db = try {
                    Room.databaseBuilder(
                        appCtx,
                        AppDatabase::class.java,
                        "decoy_vault_db"
                    )
                        .openHelperFactory(factory)
                        .fallbackToDestructiveMigration(true)
                        .build()
                } catch (e: Exception) {
                    Log.e(TAG, "Decoy database builder error, retrying", e)
                    Room.databaseBuilder(
                        appCtx,
                        AppDatabase::class.java,
                        "decoy_vault_db"
                    )
                        .openHelperFactory(factory)
                        .fallbackToDestructiveMigration(true)
                        .build()
                }
                DECOY_INSTANCE = db
                db
            }
        }
    }
}

