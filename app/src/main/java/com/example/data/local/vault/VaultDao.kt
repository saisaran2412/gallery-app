package com.example.data.local.vault

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    @Query("SELECT * FROM vault_items ORDER BY encrypted_at_millis DESC")
    fun observeVaultItems(): Flow<List<VaultItemEntity>>

    @Query("SELECT COUNT(*) FROM vault_items")
    fun observeVaultItemCount(): Flow<Int>

    @Query("SELECT * FROM vault_items ORDER BY encrypted_at_millis DESC")
    suspend fun getVaultItemsSnapshot(): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items WHERE id = :id LIMIT 1")
    suspend fun getVaultItemById(id: String): VaultItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVaultItems(items: List<VaultItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVaultItem(item: VaultItemEntity)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteVaultItemById(id: String)

    @Query("DELETE FROM vault_items")
    suspend fun clearAllVaultItems()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: VaultAuditLogEntity)

    @Query("SELECT * FROM vault_audit_logs ORDER BY timestamp_millis DESC LIMIT 20")
    fun observeRecentAuditLogs(): Flow<List<VaultAuditLogEntity>>
}
