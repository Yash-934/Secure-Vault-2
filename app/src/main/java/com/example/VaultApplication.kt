package com.example

import android.app.Application
import net.sqlcipher.database.SQLiteDatabase

class VaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            SQLiteDatabase.loadLibs(this)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}
