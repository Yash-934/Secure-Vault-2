package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_folders")
data class VaultFolder(
    @PrimaryKey val name: String,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val iconType: String = "FOLDER"
)
