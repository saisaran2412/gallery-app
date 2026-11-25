package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MediaType {
    PHOTO,
    VIDEO
}

@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["date_taken_millis"]),
        Index(value = ["bucket_id"]),
        Index(value = ["sha256_hash"]),
        Index(value = ["is_trashed", "is_in_vault"])
    ]
)
data class MediaItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long,
    @ColumnInfo(name = "uri_string") val uriString: String,
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
    @ColumnInfo(name = "is_trashed") val isTrashed: Boolean = false,
    @ColumnInfo(name = "trashed_at_millis") val trashedAtMillis: Long = 0L,
    @ColumnInfo(name = "is_in_vault") val isInVault: Boolean = false,
    @ColumnInfo(name = "vault_file_name") val vaultFileName: String = "",
    @ColumnInfo(name = "sha256_hash") val sha256Hash: String = "",
    @ColumnInfo(name = "d_hash") val dHash: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    @ColumnInfo(name = "camera_model") val cameraModel: String = "",
    @ColumnInfo(name = "edit_params_json") val editParamsJson: String = "",
    @ColumnInfo(name = "playback_position_ms") val playbackPositionMs: Long = 0L
) {
    val isVideo: Boolean get() = mediaType == MediaType.VIDEO
    val hasEdits: Boolean get() = editParamsJson.isNotBlank()
}

@Entity(tableName = "virtual_albums")
data class VirtualAlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    @ColumnInfo(name = "parent_album_id") val parentAlbumId: Long? = null,
    @ColumnInfo(name = "cover_uri") val coverUri: String = "",
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "album_media_cross_ref",
    primaryKeys = ["album_id", "media_id"],
    indices = [Index(value = ["media_id"])]
)
data class AlbumMediaCrossRef(
    @ColumnInfo(name = "album_id") val albumId: Long,
    @ColumnInfo(name = "media_id") val mediaId: String,
    @ColumnInfo(name = "added_at_millis") val addedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    @ColumnInfo(name = "color_hex") val colorHex: Long = 0xFF48CAE4L
)

@Entity(
    tableName = "media_tag_cross_ref",
    primaryKeys = ["media_id", "tag_id"],
    indices = [Index(value = ["tag_id"])]
)
data class MediaTagCrossRef(
    @ColumnInfo(name = "media_id") val mediaId: String,
    @ColumnInfo(name = "tag_id") val tagId: Long
)

@Entity(tableName = "person_groups")
data class PersonGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val label: String,
    @ColumnInfo(name = "cover_media_id") val coverMediaId: String,
    @ColumnInfo(name = "face_count") val faceCount: Int = 1,
    val signature: String = ""
)

@Entity(
    tableName = "media_faces",
    indices = [Index(value = ["media_id"]), Index(value = ["person_id"])]
)
data class MediaFaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "media_id") val mediaId: String,
    @ColumnInfo(name = "person_id") val personId: Long,
    @ColumnInfo(name = "center_x") val centerX: Float,
    @ColumnInfo(name = "center_y") val centerY: Float,
    @ColumnInfo(name = "eye_distance") val eyeDistance: Float,
    val signature: String = ""
)

data class AlbumSummary(
    val id: Long,
    val name: String,
    val parentAlbumId: Long?,
    val coverUri: String,
    val itemCount: Int
)

data class DeviceFolderSummary(
    val bucketId: String,
    val bucketName: String,
    val coverUri: String,
    val itemCount: Int,
    val latestMillis: Long
)

/** A birthday the user entered (or imported): repeats every year on [day]/[month]. */
@Entity(tableName = "birthdays")
data class BirthdayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val day: Int,
    val month: Int,                 // 1..12
    val year: Int? = null,          // birth year, optional (used for the age in the notification)
    @ColumnInfo(name = "face_group_id") val faceGroupId: Long? = null,
    val enabled: Boolean = true
)

/** Text found in a photo by offline OCR. Kept apart from media_items so a media re-sync never wipes it. */
@Entity(tableName = "media_ocr")
data class OcrEntity(
    @PrimaryKey @ColumnInfo(name = "media_id") val mediaId: String,
    val text: String,
    @ColumnInfo(name = "scanned_at") val scannedAt: Long = System.currentTimeMillis()
)
