package com.example.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.security.DatabaseKeyManager
import com.example.util.VaultLogger
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.io.File
import java.io.FileInputStream

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
                } catch (e: Exception) {
                    Log.w(TAG, "SQLCipher load error", e)
                }

                val passphrase = DatabaseKeyManager.getDatabasePassphrase(appCtx)
                ensureDatabaseValid(appCtx, "secure_vault_db")
                val factory = SupportFactory(passphrase)

                val db = Room.databaseBuilder(
                    appCtx,
                    AppDatabase::class.java,
                    "secure_vault_db"
                )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration(true)
                    .build()

                VaultLogger.log(appCtx, TAG, "Initialized primary encrypted Room database with SQLCipher")
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
                } catch (e: Exception) {
                    Log.w(TAG, "SQLCipher load error", e)
                }

                val passphrase = DatabaseKeyManager.getDatabasePassphrase(appCtx)
                ensureDatabaseValid(appCtx, "decoy_vault_db")
                val factory = SupportFactory(passphrase)

                val db = Room.databaseBuilder(
                    appCtx,
                    AppDatabase::class.java,
                    "decoy_vault_db"
                )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration(true)
                    .build()

                VaultLogger.log(appCtx, TAG, "Initialized decoy encrypted Room database with SQLCipher")
                DECOY_INSTANCE = db
                db
            }
        }

        private fun ensureDatabaseValid(context: Context, dbName: String) {
            val dbFile = context.getDatabasePath(dbName)
            if (dbFile.exists() && dbFile.length() >= 16) {
                if (isPlaintextSqliteHeader(dbFile)) {
                    VaultLogger.log(context, TAG, "Detected legacy unencrypted plaintext database: $dbName. Removing legacy file to migrate to SQLCipher.")
                    try {
                        context.deleteDatabase(dbName)
                    } catch (e: Exception) {
                        VaultLogger.logError(context, TAG, "Failed to delete legacy plaintext database: $dbName", e)
                    }
                } else {
                    VaultLogger.log(context, TAG, "Verified encrypted SQLCipher database container exists for: $dbName (${dbFile.length()} bytes)")
                }
            }
        }

        private fun isPlaintextSqliteHeader(dbFile: File): Boolean {
            return try {
                val header = ByteArray(16)
                FileInputStream(dbFile).use { it.read(header) }
                val sqliteMagic = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
                header.contentEquals(sqliteMagic)
            } catch (e: Exception) {
                false
            }
        }
    }
}

