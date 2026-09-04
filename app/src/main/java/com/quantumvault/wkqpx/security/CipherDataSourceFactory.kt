package com.quantumvault.wkqpx.security

import androidx.media3.datasource.DataSource
import java.io.File

class CipherDataSourceFactory(
    private val encryptedFile: File
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return CipherDataSource(encryptedFile)
    }
}
