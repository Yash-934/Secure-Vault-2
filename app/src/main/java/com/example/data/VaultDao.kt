package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_items ORDER BY addedTimestamp DESC")
    fun getAllVaultItems(): Flow<List<VaultItem>>

    @Query("SELECT * FROM vault_items WHERE mimeType LIKE 'image/%' ORDER BY addedTimestamp DESC")
    fun getPhotos(): Flow<List<VaultItem>>

    @Query("SELECT * FROM vault_items WHERE mimeType LIKE 'video/%' ORDER BY addedTimestamp DESC")
    fun getVideos(): Flow<List<VaultItem>>

    @Query("SELECT * FROM vault_items WHERE mimeType NOT LIKE 'image/%' AND mimeType NOT LIKE 'video/%' ORDER BY addedTimestamp DESC")
    fun getDocuments(): Flow<List<VaultItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultItem(item: VaultItem): Long

    @Delete
    suspend fun deleteVaultItem(item: VaultItem)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteById(id: Long)
}
