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

    // Folder Specific Queries
    @Query("SELECT * FROM vault_folders ORDER BY createdTimestamp ASC")
    fun getAllFolders(): Flow<List<VaultFolder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: VaultFolder)

    @Query("DELETE FROM vault_folders WHERE name = :folderName")
    suspend fun deleteFolder(folderName: String)

    @Query("UPDATE vault_items SET folderName = 'Root' WHERE folderName = :folderName")
    suspend fun resetItemsInFolderToRoot(folderName: String)

    @Query("UPDATE vault_items SET folderName = :newFolder WHERE folderName = :oldFolder")
    suspend fun renameItemsFolder(oldFolder: String, newFolder: String)

    @Query("UPDATE vault_items SET folderName = :newFolder WHERE id = :itemId")
    suspend fun updateItemFolder(itemId: Long, newFolder: String)

    @Query("SELECT * FROM vault_items WHERE folderName = :folderName ORDER BY addedTimestamp DESC")
    fun getItemsByFolder(folderName: String): Flow<List<VaultItem>>

    @Query("SELECT * FROM vault_items WHERE folderName = :folderName AND mimeType LIKE 'image/%' ORDER BY addedTimestamp DESC")
    fun getPhotosByFolder(folderName: String): Flow<List<VaultItem>>

    @Query("SELECT * FROM vault_items WHERE folderName = :folderName AND mimeType LIKE 'video/%' ORDER BY addedTimestamp DESC")
    fun getVideosByFolder(folderName: String): Flow<List<VaultItem>>

    @Query("SELECT * FROM vault_items WHERE folderName = :folderName AND mimeType NOT LIKE 'image/%' AND mimeType NOT LIKE 'video/%' ORDER BY addedTimestamp DESC")
    fun getDocumentsByFolder(folderName: String): Flow<List<VaultItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultItem(item: VaultItem): Long

    @Delete
    suspend fun deleteVaultItem(item: VaultItem)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteById(id: Long)
}

