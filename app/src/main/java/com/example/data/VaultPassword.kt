package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_passwords")
data class VaultPassword(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val category: String = "Login",
    val usernameOrEmail: String = "",
    val encryptedPasswordBlob: String = "",
    val websiteOrUrl: String = "",
    val encryptedNotesBlob: String = "",
    val isFavorite: Boolean = false,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val updatedTimestamp: Long = System.currentTimeMillis()
)
