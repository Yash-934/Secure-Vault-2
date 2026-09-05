package com.quantumvault.wkqpx.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.quantumvault.wkqpx.security.DatabaseKeyManager
import com.quantumvault.wkqpx.util.VaultLogger
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

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `vault_folders` (`name` TEXT NOT NULL PRIMARY KEY, `createdTimestamp` INTEGER NOT NULL, `iconType` TEXT NOT NULL)")
                db.execSQL("ALTER TABLE `vault_items` ADD COLUMN `folderName` TEXT NOT NULL DEFAULT 'Root'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `intruder_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `attemptType` TEXT NOT NULL, `details` TEXT NOT NULL, `imagePath` TEXT)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `vault_passwords` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, `usernameOrEmail` TEXT NOT NULL, `encryptedPasswordBlob` TEXT NOT NULL, `websiteOrUrl` TEXT NOT NULL, `encryptedNotesBlob` TEXT NOT NULL, `isFavorite` INTEGER NOT NULL, `createdTimestamp` INTEGER NOT NULL, `updatedTimestamp` INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 5 schema verification & index optimizations if needed
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null
        @Volatile
        private var DECOY_INSTANCE: AppDatabase? = null

        @Volatile
        var simulateSqlCipherUnavailableForTesting: Boolean = false

        private fun isRobolectricTestEnv(): Boolean {
            return com.quantumvault.wkqpx.BuildConfig.DEBUG &&
                    android.os.Build.FINGERPRINT.lowercase(java.util.Locale.US).contains("robolectric")
        }

        fun getDatabase(context: Context): AppDatabase {
            if (!com.quantumvault.wkqpx.security.VaultKeyManager.isRealVaultAuthorized()) {
                throw SecurityException("REAL_VAULT_NOT_AUTHORIZED: Access to real database denied.")
            }
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val appCtx = context.applicationContext
                    var sqlCipherLoaded = false
                    try {
                        SQLiteDatabase.loadLibs(appCtx)
                        sqlCipherLoaded = true
                    } catch (e: UnsatisfiedLinkError) {
                        Log.w(TAG, "SQLCipher native libs already loaded or unsatisfied link", e)
                    } catch (e: Exception) {
                        Log.w(TAG, "SQLCipher load error", e)
                    }

                    if (simulateSqlCipherUnavailableForTesting || (!sqlCipherLoaded && !isRobolectricTestEnv())) {
                        throw SecurityException("SQLCipher native library is unavailable. Database initialization failed closed to prevent plaintext storage.")
                    }

                    val passphrase = DatabaseKeyManager.getDatabasePassphrase(appCtx, isDecoy = false)
                    val factory = if (sqlCipherLoaded) {
                        try {
                            SupportFactory(passphrase)
                        } catch (t: Throwable) {
                            throw SecurityException("Failed to initialize SupportFactory with encryption passphrase. Failing closed.", t)
                        }
                    } else if (isRobolectricTestEnv()) {
                        null
                    } else {
                        throw SecurityException("SQLCipher is strictly mandatory. Refusing plaintext Room database creation.")
                    }

                    val builder = Room.databaseBuilder(
                        appCtx,
                        AppDatabase::class.java,
                        "secure_vault_db"
                    )
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

                    if (factory != null) {
                        builder.openHelperFactory(factory)
                    } else if (!isRobolectricTestEnv()) {
                        throw SecurityException("SQLCipher openHelperFactory missing in production. Refusing plaintext database creation.")
                    }

                    val db = builder.build()
                    VaultLogger.log(appCtx, TAG, "Initialized primary encrypted Room database with SQLCipher")
                    INSTANCE = db
                    db
                }
            }
        }

        fun getDecoyDatabase(context: Context): AppDatabase {
            if (!com.quantumvault.wkqpx.security.VaultKeyManager.isDecoyVaultAuthorized()) {
                throw SecurityException("DECOY_VAULT_NOT_AUTHORIZED: Access to decoy database denied.")
            }
            return DECOY_INSTANCE ?: synchronized(this) {
                DECOY_INSTANCE ?: run {
                    val appCtx = context.applicationContext
                    var sqlCipherLoaded = false
                    try {
                        SQLiteDatabase.loadLibs(appCtx)
                        sqlCipherLoaded = true
                    } catch (e: UnsatisfiedLinkError) {
                        Log.w(TAG, "SQLCipher native libs already loaded or unsatisfied link", e)
                    } catch (e: Exception) {
                        Log.w(TAG, "SQLCipher load error", e)
                    }

                    if (simulateSqlCipherUnavailableForTesting || (!sqlCipherLoaded && !isRobolectricTestEnv())) {
                        throw SecurityException("SQLCipher native library is unavailable. Decoy database initialization failed closed to prevent plaintext storage.")
                    }

                    val passphrase = DatabaseKeyManager.getDatabasePassphrase(appCtx, isDecoy = true)
                    val factory = if (sqlCipherLoaded) {
                        try {
                            SupportFactory(passphrase)
                        } catch (t: Throwable) {
                            throw SecurityException("Failed to initialize SupportFactory for decoy database. Failing closed.", t)
                        }
                    } else if (isRobolectricTestEnv()) {
                        null
                    } else {
                        throw SecurityException("SQLCipher is strictly mandatory for decoy database. Refusing plaintext fallback.")
                    }

                    val builder = Room.databaseBuilder(
                        appCtx,
                        AppDatabase::class.java,
                        "secure_vault_decoy_db"
                    )
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

                    if (factory != null) {
                        builder.openHelperFactory(factory)
                    } else if (!isRobolectricTestEnv()) {
                        throw SecurityException("SQLCipher openHelperFactory missing in production for decoy database. Refusing plaintext fallback.")
                    }

                    val db = builder.build()
                    VaultLogger.log(appCtx, TAG, "Initialized decoy encrypted Room database with SQLCipher")
                    DECOY_INSTANCE = db
                    db
                }
            }
        }

        /**
         * Closes both real and decoy databases, invalidates singletons, and wipes memory secrets on lock.
         * Ensures previously open Room/DAO connections fail closed immediately.
         */
        @Synchronized
        fun closeDatabases() {
            try {
                INSTANCE?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing real database instance", e)
            }
            INSTANCE = null

            try {
                DECOY_INSTANCE?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing decoy database instance", e)
            }
            DECOY_INSTANCE = null

            DatabaseKeyManager.clearMemory()
        }
    }
}

