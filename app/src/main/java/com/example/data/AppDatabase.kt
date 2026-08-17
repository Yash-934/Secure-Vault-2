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
                } catch (e: Exception) {
                    Log.w(TAG, "SQLCipher load error", e)
                }

                val passphrase = DatabaseKeyManager.getDatabasePassphrase(appCtx)
                ensureDatabaseValid(appCtx, "secure_vault_db", passphrase)
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
                } catch (e: Exception) {
                    Log.w(TAG, "SQLCipher load error", e)
                }

                val passphrase = DatabaseKeyManager.getDatabasePassphrase(appCtx)
                ensureDatabaseValid(appCtx, "decoy_vault_db", passphrase)
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

        private fun ensureDatabaseValid(context: Context, dbName: String, passphrase: ByteArray) {
            val dbFile = context.getDatabasePath(dbName)
            if (dbFile.exists() && dbFile.length() > 0) {
                var testDb: SQLiteDatabase? = null
                try {
                    val passString = String(passphrase, Charsets.ISO_8859_1)
                    testDb = SQLiteDatabase.openDatabase(
                        dbFile.absolutePath,
                        passString,
                        null,
                        SQLiteDatabase.OPEN_READWRITE
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Database $dbName cannot be opened with current key (legacy unencrypted or key changed). Deleting to recreate securely.", e)
                    try {
                        context.deleteDatabase(dbName)
                    } catch (delEx: Exception) {
                        Log.e(TAG, "Failed to delete incompatible database $dbName", delEx)
                    }
                } finally {
                    try {
                        testDb?.close()
                    } catch (_: Exception) {}
                }
            }
        }
    }
}

