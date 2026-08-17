package com.example

import android.app.Application
import android.util.Log
import net.sqlcipher.database.SQLiteDatabase

class VaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            SQLiteDatabase.loadLibs(this)
        } catch (e: UnsatisfiedLinkError) {
            Log.e("VaultApplication", "SQLCipher native libraries load failure", e)
        } catch (e: SecurityException) {
            Log.e("VaultApplication", "Security exception while loading SQLCipher", e)
        } catch (e: Exception) {
            Log.e("VaultApplication", "Exception during SQLCipher initialization", e)
        }
    }
}

