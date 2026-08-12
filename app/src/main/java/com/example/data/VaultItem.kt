package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_items")
data class VaultItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalName: String,
    val encryptedFileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val isVideo: Boolean = false
)
