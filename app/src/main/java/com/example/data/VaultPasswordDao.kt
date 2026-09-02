package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultPasswordDao {
    @Query("SELECT * FROM vault_passwords ORDER BY isFavorite DESC, updatedTimestamp DESC")
    fun getAllPasswords(): Flow<List<VaultPassword>>

    @Query("SELECT * FROM vault_passwords ORDER BY isFavorite DESC, updatedTimestamp DESC")
    suspend fun getAllPasswordsSync(): List<VaultPassword>

    @Query("SELECT * FROM vault_passwords WHERE title LIKE '%' || :query || '%' OR usernameOrEmail LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' ORDER BY isFavorite DESC, updatedTimestamp DESC")
    fun searchPasswords(query: String): Flow<List<VaultPassword>>

    @Query("SELECT * FROM vault_passwords WHERE id = :id")
    suspend fun getPasswordById(id: Long): VaultPassword?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPassword(password: VaultPassword): Long

    @Update
    suspend fun updatePassword(password: VaultPassword)

    @Delete
    suspend fun deletePassword(password: VaultPassword)

    @Query("DELETE FROM vault_passwords WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM vault_passwords")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM vault_passwords")
    suspend fun getCount(): Int
}
