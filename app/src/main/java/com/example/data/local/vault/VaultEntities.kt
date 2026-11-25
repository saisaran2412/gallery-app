package com.example.data.local.vault

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.local.MediaItemEntity
import com.example.data.local.MediaType

@Entity(
    tableName = "vault_items",
    indices = [
        Index(value = ["encrypted_at_millis"]),
        Index(value = ["date_taken_millis"])
    ]
)
data class VaultItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long,
    @ColumnInfo(name = "original_uri_string") val originalUriString: String,
    @ColumnInfo(name = "media_type") val mediaType: MediaType,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "date_taken_millis") val dateTakenMillis: Long,
    @ColumnInfo(name = "date_added_millis") val dateAddedMillis: Long,
    @ColumnInfo(name = "date_modified_millis") val dateModifiedMillis: Long,
    val width: Int,
    val height: Int,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "duration_millis") val durationMillis: Long = 0L,
    @ColumnInfo(name = "bucket_id") val bucketId: String = "",
    @ColumnInfo(name = "bucket_name") val bucketName: String = "Camera",
    @ColumnInfo(name = "relative_path") val relativePath: String = "",
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    val note: String = "",
    @ColumnInfo(name = "edit_params_json") val editParamsJson: String = "",
    @ColumnInfo(name = "vault_file_name") val vaultFileName: String,
    @ColumnInfo(name = "sha256_hash") val sha256Hash: String = "",
    @ColumnInfo(name = "encrypted_at_millis") val encryptedAtMillis: Long = System.currentTimeMillis()
) {
    fun toMediaItemEntity(): MediaItemEntity = MediaItemEntity(
        id = id,
        mediaStoreId = mediaStoreId,
        uriString = originalUriString,
        mediaType = mediaType,
        displayName = displayName,
        mimeType = mimeType,
        dateTakenMillis = dateTakenMillis,
        dateAddedMillis = dateAddedMillis,
        dateModifiedMillis = dateModifiedMillis,
        width = width,
        height = height,
        sizeBytes = sizeBytes,
        durationMillis = durationMillis,
        bucketId = bucketId,
        bucketName = bucketName,
        relativePath = relativePath,
        isFavorite = isFavorite,
        note = note,
        isTrashed = false,
        trashedAtMillis = 0L,
        isInVault = true,
        vaultFileName = vaultFileName,
        sha256Hash = sha256Hash,
        editParamsJson = editParamsJson
    )
}

fun MediaItemEntity.toVaultItemEntity(vaultFileName: String): VaultItemEntity = VaultItemEntity(
    id = id,
    mediaStoreId = mediaStoreId,
    originalUriString = uriString,
    mediaType = mediaType,
    displayName = displayName,
    mimeType = mimeType,
    dateTakenMillis = dateTakenMillis,
    dateAddedMillis = dateAddedMillis,
    dateModifiedMillis = dateModifiedMillis,
    width = width,
    height = height,
    sizeBytes = sizeBytes,
    durationMillis = durationMillis,
    bucketId = bucketId,
    bucketName = bucketName,
    relativePath = relativePath,
    isFavorite = isFavorite,
    note = note,
    editParamsJson = editParamsJson,
    vaultFileName = vaultFileName,
    sha256Hash = sha256Hash,
    encryptedAtMillis = System.currentTimeMillis()
)

@Entity(
    tableName = "vault_audit_logs",
    indices = [Index(value = ["timestamp_millis"])]
)
data class VaultAuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "event_type") val eventType: String,
    val details: String,
    @ColumnInfo(name = "timestamp_millis") val timestampMillis: Long = System.currentTimeMillis()
)
