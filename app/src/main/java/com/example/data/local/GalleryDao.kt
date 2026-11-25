package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GalleryDao {

    @Query("SELECT * FROM media_items WHERE is_trashed = 0 AND is_in_vault = 0 ORDER BY date_taken_millis DESC")
    fun observeActiveMedia(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE is_trashed = 0 AND is_in_vault = 0 AND is_favorite = 1 ORDER BY date_taken_millis DESC")
    fun observeFavoriteMedia(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE is_trashed = 1 AND is_in_vault = 0 ORDER BY trashed_at_millis DESC")
    fun observeTrashedMedia(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE is_in_vault = 1 ORDER BY date_taken_millis DESC")
    fun observeVaultMedia(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE is_trashed = 0 AND is_in_vault = 0 AND bucket_id = :bucketId ORDER BY date_taken_millis DESC")
    fun observeFolderMedia(bucketId: String): Flow<List<MediaItemEntity>>

    @Query(
        """
        SELECT m.* FROM media_items m
        INNER JOIN album_media_cross_ref c ON m.id = c.media_id
        WHERE c.album_id = :albumId AND m.is_trashed = 0 AND m.is_in_vault = 0
        ORDER BY m.date_taken_millis DESC
        """
    )
    fun observeVirtualAlbumMedia(albumId: Long): Flow<List<MediaItemEntity>>

    @Query(
        """
        SELECT m.* FROM media_items m
        INNER JOIN media_tag_cross_ref t ON m.id = t.media_id
        WHERE t.tag_id = :tagId AND m.is_trashed = 0 AND m.is_in_vault = 0
        ORDER BY m.date_taken_millis DESC
        """
    )
    fun observeMediaByTag(tagId: Long): Flow<List<MediaItemEntity>>

    @Query(
        """
        SELECT DISTINCT m.* FROM media_items m
        INNER JOIN media_faces f ON m.id = f.media_id
        WHERE f.person_id = :personId AND m.is_trashed = 0 AND m.is_in_vault = 0
        ORDER BY m.date_taken_millis DESC
        """
    )
    fun observeMediaByPerson(personId: Long): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE id = :id LIMIT 1")
    suspend fun getMediaById(id: String): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE id IN (:ids)")
    suspend fun getMediaByIds(ids: List<String>): List<MediaItemEntity>

    @Query("SELECT * FROM media_items")
    suspend fun getAllMediaSnapshot(): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE is_trashed = 0 AND is_in_vault = 0 AND media_type = 'PHOTO'")
    suspend fun getActivePhotosSnapshot(): List<MediaItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedia(items: List<MediaItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSingleMedia(item: MediaItemEntity)

    @Update
    suspend fun updateMedia(item: MediaItemEntity)

    @Query("DELETE FROM media_items WHERE id IN (:ids)")
    suspend fun deleteMediaByIds(ids: List<String>)

    @Query("UPDATE media_items SET is_favorite = :favorite WHERE id IN (:ids)")
    suspend fun setFavorites(ids: List<String>, favorite: Boolean)

    @Query("UPDATE media_items SET note = :note WHERE id = :mediaId")
    suspend fun updateNote(mediaId: String, note: String)

    @Query("UPDATE media_items SET edit_params_json = :json WHERE id = :mediaId")
    suspend fun updateEditParams(mediaId: String, json: String)

    @Query("UPDATE media_items SET playback_position_ms = :positionMs WHERE id = :mediaId")
    suspend fun updatePlaybackPosition(mediaId: String, positionMs: Long)

    @Query("UPDATE media_items SET is_trashed = :trashed, trashed_at_millis = :trashedAt WHERE id IN (:ids)")
    suspend fun setTrashed(ids: List<String>, trashed: Boolean, trashedAt: Long)

    @Query("UPDATE media_items SET is_in_vault = :inVault, vault_file_name = :vaultFileName WHERE id = :mediaId")
    suspend fun setVaultState(mediaId: String, inVault: Boolean, vaultFileName: String)

    @Query("UPDATE media_items SET sha256_hash = :sha256, d_hash = :dHash WHERE id = :mediaId")
    suspend fun updateHashes(mediaId: String, sha256: String, dHash: String)

    @Query("UPDATE media_items SET latitude = :lat, longitude = :lon, camera_model = :cameraModel WHERE id = :mediaId")
    suspend fun updateExifDetails(mediaId: String, lat: Double?, lon: Double?, cameraModel: String)

    @Query("SELECT * FROM media_items WHERE is_trashed = 1 AND is_in_vault = 0 AND trashed_at_millis > 0 AND trashed_at_millis < :cutoffMillis")
    suspend fun getExpiredTrashedItems(cutoffMillis: Long): List<MediaItemEntity>

    // Virtual Albums
    @Query("SELECT * FROM virtual_albums ORDER BY created_at_millis DESC")
    fun observeVirtualAlbums(): Flow<List<VirtualAlbumEntity>>

    @Query("SELECT * FROM virtual_albums")
    suspend fun getAllVirtualAlbumsSnapshot(): List<VirtualAlbumEntity>

    @Query("SELECT * FROM album_media_cross_ref")
    fun observeAllAlbumCrossRefs(): Flow<List<AlbumMediaCrossRef>>

    @Query("SELECT * FROM album_media_cross_ref")
    suspend fun getAllAlbumCrossRefsSnapshot(): List<AlbumMediaCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVirtualAlbum(album: VirtualAlbumEntity): Long

    @Query("UPDATE virtual_albums SET name = :name WHERE id = :albumId")
    suspend fun renameVirtualAlbum(albumId: Long, name: String)

    @Query("UPDATE virtual_albums SET cover_uri = :coverUri WHERE id = :albumId")
    suspend fun updateAlbumCover(albumId: Long, coverUri: String)

    @Query("DELETE FROM virtual_albums WHERE id = :albumId")
    suspend fun deleteVirtualAlbum(albumId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMediaToAlbum(refs: List<AlbumMediaCrossRef>)

    @Query("DELETE FROM album_media_cross_ref WHERE album_id = :albumId AND media_id IN (:mediaIds)")
    suspend fun removeMediaFromAlbum(albumId: Long, mediaIds: List<String>)

    @Query("DELETE FROM album_media_cross_ref WHERE album_id = :albumId")
    suspend fun clearAlbumMedia(albumId: Long)

    // Tags
    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun observeAllTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags")
    suspend fun getAllTagsSnapshot(): List<TagEntity>

    @Query("SELECT * FROM media_tag_cross_ref")
    fun observeAllMediaTagRefs(): Flow<List<MediaTagCrossRef>>

    @Query("SELECT * FROM media_tag_cross_ref")
    suspend fun getAllMediaTagRefsSnapshot(): List<MediaTagCrossRef>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun getTagByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMediaTagRef(ref: MediaTagCrossRef)

    @Query("DELETE FROM media_tag_cross_ref WHERE media_id = :mediaId AND tag_id = :tagId")
    suspend fun removeMediaTagRef(mediaId: String, tagId: Long)

    // Offline People / Faces
    @Query("SELECT * FROM person_groups ORDER BY face_count DESC, id ASC")
    fun observePersonGroups(): Flow<List<PersonGroupEntity>>

    @Query("SELECT * FROM person_groups")
    suspend fun getPersonGroupsSnapshot(): List<PersonGroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonGroup(group: PersonGroupEntity): Long

    @Query("UPDATE person_groups SET label = :label WHERE id = :personId")
    suspend fun renamePersonGroup(personId: Long, label: String)

    @Query("UPDATE person_groups SET face_count = :count, cover_media_id = :coverMediaId WHERE id = :personId")
    suspend fun updatePersonGroupStats(personId: Long, count: Int, coverMediaId: String)

    @Query("UPDATE person_groups SET face_count = :count, signature = :signature WHERE id = :personId")
    suspend fun updatePersonGroupCentroid(personId: Long, count: Int, signature: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaFaces(faces: List<MediaFaceEntity>)

    @Query("SELECT DISTINCT media_id FROM media_faces")
    suspend fun getScannedFaceMediaIds(): List<String>

    @Query("DELETE FROM media_faces")
    suspend fun deleteAllFaces()

    @Query("DELETE FROM person_groups")
    suspend fun deleteAllPersonGroups()

    // ---- Birthdays
    @Query("SELECT * FROM birthdays ORDER BY month, day, name")
    fun observeBirthdays(): Flow<List<BirthdayEntity>>

    @Query("SELECT * FROM birthdays WHERE enabled = 1")
    suspend fun getEnabledBirthdays(): List<BirthdayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBirthday(birthday: BirthdayEntity): Long

    @Query("DELETE FROM birthdays WHERE id = :id")
    suspend fun deleteBirthday(id: Long)

    // ---- OCR
    @Query("SELECT media_id FROM media_ocr")
    suspend fun getOcrScannedIds(): List<String>

    @Query("SELECT COUNT(*) FROM media_ocr")
    fun observeOcrCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOcr(rows: List<OcrEntity>)

    @Query("DELETE FROM media_ocr")
    suspend fun deleteAllOcr()

    @Query("DELETE FROM media_ocr WHERE media_id NOT IN (SELECT id FROM media_items)")
    suspend fun deleteOrphanOcr()

    /** Photos whose recognised text contains [q] (case-insensitive for ASCII). */
    @Query("SELECT media_id FROM media_ocr WHERE text LIKE '%' || :q || '%'")
    suspend fun searchOcrIds(q: String): List<String>

    @Query("SELECT media_id FROM media_ocr WHERE text LIKE :p1 OR text LIKE :p2 OR text LIKE :p3 OR text LIKE :p4 OR text LIKE :p5 OR text LIKE :p6")
    suspend fun searchOcrAny(p1: String, p2: String, p3: String, p4: String, p5: String, p6: String): List<String>
}
