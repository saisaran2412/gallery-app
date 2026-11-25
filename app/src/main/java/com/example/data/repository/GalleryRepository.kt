package com.example.data.repository

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.core.storage.AppStorageDirs
import com.example.data.local.AlbumDatabase
import com.example.data.local.AlbumMediaCrossRef
import com.example.data.local.MediaItemEntity
import com.example.data.local.MediaTagCrossRef
import com.example.data.local.MediaType
import com.example.data.local.PersonGroupEntity
import com.example.data.local.TagEntity
import com.example.data.local.VirtualAlbumEntity
import com.example.data.scanner.DuplicateGroup
import com.example.data.scanner.ExifMetadata
import com.example.data.scanner.MediaHashingEngine
import com.example.data.scanner.MediaStoreScanner
import com.example.data.scanner.OfflineFaceEngine
import com.example.data.scanner.ScanControl
import com.example.data.scanner.runParallel
import com.example.editor.EditParams
import com.example.editor.PhotoEditorEngine
import com.example.security.VaultCryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class GalleryRepository(private val context: Context) {

    private val db = AlbumDatabase.getInstance(context)
    private val dao = db.galleryDao()

    val activeMedia: Flow<List<MediaItemEntity>> = dao.observeActiveMedia()
    val favoriteMedia: Flow<List<MediaItemEntity>> = dao.observeFavoriteMedia()
    val trashedMedia: Flow<List<MediaItemEntity>> = dao.observeTrashedMedia()
    val vaultMedia: Flow<List<MediaItemEntity>> = dao.observeVaultMedia()
    val virtualAlbums: Flow<List<VirtualAlbumEntity>> = dao.observeVirtualAlbums()
    val albumCrossRefs: Flow<List<AlbumMediaCrossRef>> = dao.observeAllAlbumCrossRefs()
    val allTags: Flow<List<TagEntity>> = dao.observeAllTags()
    val mediaTagRefs: Flow<List<MediaTagCrossRef>> = dao.observeAllMediaTagRefs()
    val personGroups: Flow<List<PersonGroupEntity>> = dao.observePersonGroups()

    fun observeFolderMedia(bucketId: String): Flow<List<MediaItemEntity>> =
        dao.observeFolderMedia(bucketId)

    fun observeVirtualAlbumMedia(albumId: Long): Flow<List<MediaItemEntity>> =
        dao.observeVirtualAlbumMedia(albumId)

    fun observeMediaByTag(tagId: Long): Flow<List<MediaItemEntity>> =
        dao.observeMediaByTag(tagId)

    fun observeMediaByPerson(personId: Long): Flow<List<MediaItemEntity>> =
        dao.observeMediaByPerson(personId)

    suspend fun syncMediaStore(trashAutoCleanDays: Int) = withContext(Dispatchers.IO) {
        AppStorageDirs.ensureInitialized(context)
        val existingMap = dao.getAllMediaSnapshot().associateBy { it.id }
        val (upserts, staleIds) = MediaStoreScanner.scanDeviceMedia(context, existingMap)
        if (upserts.isNotEmpty()) {
            dao.upsertMedia(upserts)
        }
        if (staleIds.isNotEmpty()) {
            dao.deleteMediaByIds(staleIds)
        }
        if (trashAutoCleanDays > 0) {
            val cutoff = System.currentTimeMillis() - trashAutoCleanDays * 86_400_000L
            val expired = dao.getExpiredTrashedItems(cutoff)
            if (expired.isNotEmpty()) {
                permanentlyDeleteFromApp(expired)
            }
        }
    }

    suspend fun importFromPickedUris(uris: List<Uri>) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val importsDir = AppStorageDirs.importsDir(context)
        val newItems = mutableListOf<MediaItemEntity>()

        for (uri in uris) {
            runCatching {
                val mime = resolver.getType(uri) ?: "image/jpeg"
                val isVideo = mime.startsWith("video/")
                val name = queryDisplayName(resolver, uri)
                    ?: "Imported_${System.currentTimeMillis()}.${if (isVideo) "mp4" else "jpg"}"
                val safeName = "${UUID.randomUUID().toString().take(8)}_${name.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
                val destFile = File(importsDir, safeName)
                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output -> input.copyTo(output) }
                }
                val now = System.currentTimeMillis()
                newItems.add(
                    MediaItemEntity(
                        id = "imp_${UUID.randomUUID()}",
                        mediaStoreId = now,
                        uriString = Uri.fromFile(destFile).toString(),
                        mediaType = if (isVideo) MediaType.VIDEO else MediaType.PHOTO,
                        displayName = name,
                        mimeType = mime,
                        dateTakenMillis = now,
                        dateAddedMillis = now,
                        dateModifiedMillis = now,
                        width = 0,
                        height = 0,
                        sizeBytes = destFile.length(),
                        durationMillis = 0L,
                        bucketId = "imported",
                        bucketName = "Imported",
                        relativePath = "Imported"
                    )
                )
            }
        }
        if (newItems.isNotEmpty()) {
            dao.upsertMedia(newItems)
        }
        newItems.size
    }

    suspend fun registerReceivedAirShareMedia(
        destFile: File,
        displayName: String,
        mimeType: String
    ): MediaItemEntity = withContext(Dispatchers.IO) {
        val cleanMime = mimeType.ifBlank {
            if (destFile.extension.lowercase() in setOf("mp4", "mkv", "mov", "webm", "3gp")) "video/mp4" else "image/jpeg"
        }
        val isVideo = cleanMime.startsWith("video/")
        val now = System.currentTimeMillis()
        val entity = MediaItemEntity(
            id = "imp_airshare_${UUID.randomUUID()}",
            mediaStoreId = now,
            uriString = Uri.fromFile(destFile).toString(),
            mediaType = if (isVideo) MediaType.VIDEO else MediaType.PHOTO,
            displayName = displayName.ifBlank { destFile.name },
            mimeType = cleanMime,
            dateTakenMillis = now,
            dateAddedMillis = now,
            dateModifiedMillis = now,
            width = 0,
            height = 0,
            sizeBytes = destFile.length(),
            durationMillis = 0L,
            bucketId = "airshare",
            bucketName = "AirShare",
            relativePath = "AirShare"
        )
        dao.upsertMedia(listOf(entity))
        entity
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    suspend fun getMediaById(id: String): MediaItemEntity? = withContext(Dispatchers.IO) {
        dao.getMediaById(id)
    }

    suspend fun loadAndCacheExif(item: MediaItemEntity): ExifMetadata = withContext(Dispatchers.IO) {
        if (item.isVideo) return@withContext ExifMetadata(null, null, "")
        val meta = MediaStoreScanner.readExifMetadata(context, Uri.parse(item.uriString))
        if (meta.latitude != null || meta.cameraModel.isNotEmpty()) {
            dao.updateExifDetails(item.id, meta.latitude, meta.longitude, meta.cameraModel)
        }
        meta
    }

    suspend fun setFavorites(ids: List<String>, favorite: Boolean) = withContext(Dispatchers.IO) {
        dao.setFavorites(ids, favorite)
    }

    suspend fun updateNote(mediaId: String, note: String) = withContext(Dispatchers.IO) {
        dao.updateNote(mediaId, note)
    }

    suspend fun updatePlaybackPosition(mediaId: String, positionMs: Long) = withContext(Dispatchers.IO) {
        dao.updatePlaybackPosition(mediaId, positionMs)
    }

    suspend fun saveNonDestructiveEdit(mediaId: String, params: EditParams) = withContext(Dispatchers.IO) {
        val json = if (params.isModified) params.toJson() else ""
        dao.updateEditParams(mediaId, json)
    }

    suspend fun exportEditedCopy(
        item: MediaItemEntity,
        params: EditParams,
        depth: com.example.editor.DepthMap? = null
    ): Uri? = withContext(Dispatchers.IO) {
        val src = PhotoEditorEngine.decodeSampledBitmap(context, Uri.parse(item.uriString), 2400) ?: return@withContext null
        val depthMap = depth ?: if (params.depthBlur > 0.02f) com.example.editor.DepthEstimator.estimateCached(context, item.id, src) else null
        val rendered = PhotoEditorEngine.renderEditedBitmap(src, params, depthMap)
        val outName = "${item.displayName.substringBeforeLast('.')}_edited_${System.currentTimeMillis() % 10000}.jpg"
        val exportedUri = PhotoEditorEngine.exportBitmapToMediaStore(
            context = context,
            bitmap = rendered,
            fileName = outName,
            relativePath = AppStorageDirs.EXPORT_EDITED_REL_PATH,
            quality = 94
        )
        PhotoEditorEngine.saveWorkingCopy(context, rendered, item.id)
        if (rendered !== src) rendered.recycle()
        src.recycle()
        exportedUri
    }

    suspend fun exportCompressedCopy(
        item: MediaItemEntity,
        scaleFraction: Float,
        quality: Int
    ): Uri? = withContext(Dispatchers.IO) {
        val src = PhotoEditorEngine.decodeSampledBitmap(context, Uri.parse(item.uriString), 2400) ?: return@withContext null
        val targetW = (src.width * scaleFraction).toInt().coerceAtLeast(32)
        val targetH = (src.height * scaleFraction).toInt().coerceAtLeast(32)
        val scaled = if (scaleFraction < 0.99f) {
            android.graphics.Bitmap.createScaledBitmap(src, targetW, targetH, true)
        } else src
        val outName = "${item.displayName.substringBeforeLast('.')}_q${quality}_${System.currentTimeMillis() % 10000}.jpg"
        val exported = PhotoEditorEngine.exportBitmapToMediaStore(
            context = context,
            bitmap = scaled,
            fileName = outName,
            relativePath = AppStorageDirs.EXPORT_COMPRESSED_REL_PATH,
            quality = quality
        )
        if (scaled !== src) scaled.recycle()
        src.recycle()
        exported
    }

    suspend fun moveToTrash(ids: List<String>) = withContext(Dispatchers.IO) {
        dao.setTrashed(ids, trashed = true, trashedAt = System.currentTimeMillis())
    }

    suspend fun restoreFromTrash(ids: List<String>) = withContext(Dispatchers.IO) {
        dao.setTrashed(ids, trashed = false, trashedAt = 0L)
    }

    suspend fun permanentlyDeleteFromApp(items: List<MediaItemEntity>) = withContext(Dispatchers.IO) {
        items.forEach { item ->
            if (item.id.startsWith("imp_")) {
                runCatching { File(Uri.parse(item.uriString).path.orEmpty()).delete() }
            }
            if (item.vaultFileName.isNotEmpty()) {
                VaultCryptoManager.deleteVaultFile(context, item.vaultFileName)
            }
        }
        dao.deleteMediaByIds(items.map { it.id })
    }

    // Virtual Albums & Subfolders
    suspend fun createVirtualAlbum(name: String, parentAlbumId: Long? = null): Long = withContext(Dispatchers.IO) {
        dao.insertVirtualAlbum(
            VirtualAlbumEntity(
                name = name.trim(),
                parentAlbumId = parentAlbumId
            )
        )
    }

    suspend fun renameVirtualAlbum(albumId: Long, newName: String) = withContext(Dispatchers.IO) {
        dao.renameVirtualAlbum(albumId, newName.trim())
    }

    suspend fun updateAlbumCover(albumId: Long, coverUri: String) = withContext(Dispatchers.IO) {
        dao.updateAlbumCover(albumId, coverUri)
    }

    suspend fun deleteVirtualAlbum(albumId: Long) = withContext(Dispatchers.IO) {
        dao.clearAlbumMedia(albumId)
        dao.deleteVirtualAlbum(albumId)
    }

    suspend fun addMediaToAlbum(albumId: Long, mediaItems: List<MediaItemEntity>) = withContext(Dispatchers.IO) {
        val refs = mediaItems.map { AlbumMediaCrossRef(albumId = albumId, mediaId = it.id) }
        dao.addMediaToAlbum(refs)
        val firstCover = mediaItems.firstOrNull()?.uriString
        if (firstCover != null) {
            val album = dao.getAllVirtualAlbumsSnapshot().find { it.id == albumId }
            if (album != null && album.coverUri.isBlank()) {
                dao.updateAlbumCover(albumId, firstCover)
            }
        }
    }

    suspend fun removeMediaFromAlbum(albumId: Long, mediaIds: List<String>) = withContext(Dispatchers.IO) {
        dao.removeMediaFromAlbum(albumId, mediaIds)
    }

    // Tags
    suspend fun addTagToMedia(mediaId: String, tagName: String, colorHex: Long = 0xFF48CAE4L) = withContext(Dispatchers.IO) {
        val clean = tagName.trim()
        if (clean.isEmpty()) return@withContext
        val existing = dao.getTagByName(clean)
        val tagId = existing?.id ?: dao.insertTag(TagEntity(name = clean, colorHex = colorHex))
        val resolvedId = if (tagId > 0L) tagId else (dao.getTagByName(clean)?.id ?: return@withContext)
        dao.addMediaTagRef(MediaTagCrossRef(mediaId = mediaId, tagId = resolvedId))
    }

    suspend fun removeTagFromMedia(mediaId: String, tagId: Long) = withContext(Dispatchers.IO) {
        dao.removeMediaTagRef(mediaId, tagId)
    }

    // Hidden Vault (delegated to SQLCipher-encrypted VaultRepository)
    private val vaultRepository by lazy { VaultRepository.getInstance(context) }

    suspend fun moveItemsToVault(items: List<MediaItemEntity>): Int =
        vaultRepository.encryptAndStoreItems(items)

    suspend fun removeItemFromVault(item: MediaItemEntity) =
        vaultRepository.removeItemFromVault(item)

    suspend fun decryptVaultPreview(item: MediaItemEntity): Uri? =
        vaultRepository.decryptVaultPreview(item)

    // Duplicate Finder
    suspend fun scanHashesAndFindDuplicates(
        control: ScanControl,
        onProgress: (Int, Int) -> Unit
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val all = dao.getAllMediaSnapshot().filter { !it.isTrashed && !it.isInVault }
        // SHA-256 only matters when another file has the exact same byte size.
        val sizeCount = HashMap<Long, Int>(all.size)
        all.forEach { sizeCount[it.sizeBytes] = (sizeCount[it.sizeBytes] ?: 0) + 1 }
        val pending = all.filter {
            val needSha = it.sha256Hash.isEmpty() && (sizeCount[it.sizeBytes] ?: 0) > 1
            val needDHash = it.mediaType == MediaType.PHOTO && it.dHash.isEmpty()
            needSha || needDHash
        }
        if (pending.isNotEmpty()) {
            runParallel(pending, control, onProgress = onProgress) { item ->
                val uri = Uri.parse(item.uriString)
                val needSha = item.sha256Hash.isEmpty() && (sizeCount[item.sizeBytes] ?: 0) > 1
                val sha = if (needSha) MediaHashingEngine.computeSha256(context, uri) else item.sha256Hash
                val dh = if (item.mediaType == MediaType.PHOTO && item.dHash.isEmpty()) {
                    MediaHashingEngine.computeDHash(context, uri)
                } else item.dHash
                if (sha.isNotEmpty() || dh.isNotEmpty()) dao.updateHashes(item.id, sha, dh)
            }
        }
        // Works on partial data too, so Stop still shows what was found so far.
        val updated = dao.getAllMediaSnapshot().filter { !it.isTrashed && !it.isInVault }
        MediaHashingEngine.findDuplicateGroups(updated)
    }

    // Offline People / Face Grouping
    suspend fun scanOfflineFaces(control: ScanControl, onProgress: (Int, Int) -> Unit): Int =
        withContext(Dispatchers.IO) {
            val photos = dao.getActivePhotosSnapshot()
            OfflineFaceEngine.scanUnprocessedPhotos(context, photos, dao, control, onProgress)
        }

    suspend fun renamePersonGroup(personId: Long, newName: String) = withContext(Dispatchers.IO) {
        dao.renamePersonGroup(personId, newName.trim().ifEmpty { "Unknown" })
    }

    suspend fun deleteAllFaceData() = withContext(Dispatchers.IO) {
        dao.deleteAllFaces()
        dao.deleteAllPersonGroups()
        OfflineFaceEngine.clearNoFaceMarkers(context)
    }

    // Local Backup & Restore of metadata (Virtual Albums, Tags, Favorites, Notes, Edits)
    suspend fun createLocalMetadataBackup(): File = withContext(Dispatchers.IO) {
        val albums = dao.getAllVirtualAlbumsSnapshot()
        val refs = dao.getAllAlbumCrossRefsSnapshot()
        val tags = dao.getAllTagsSnapshot()
        val tagRefs = dao.getAllMediaTagRefsSnapshot()
        val media = dao.getAllMediaSnapshot().filter { it.isFavorite || it.note.isNotBlank() || it.editParamsJson.isNotBlank() }

        val root = JSONObject().apply {
            put("createdAt", System.currentTimeMillis())
            put("albums", JSONArray().apply {
                albums.forEach { a ->
                    put(JSONObject().apply {
                        put("id", a.id)
                        put("name", a.name)
                        put("parentAlbumId", a.parentAlbumId ?: JSONObject.NULL)
                        put("coverUri", a.coverUri)
                    })
                }
            })
            put("albumRefs", JSONArray().apply {
                refs.forEach { r ->
                    put(JSONObject().apply {
                        put("albumId", r.albumId)
                        put("mediaId", r.mediaId)
                    })
                }
            })
            put("tags", JSONArray().apply {
                tags.forEach { t ->
                    put(JSONObject().apply {
                        put("id", t.id)
                        put("name", t.name)
                        put("colorHex", t.colorHex)
                    })
                }
            })
            put("tagRefs", JSONArray().apply {
                tagRefs.forEach { tr ->
                    put(JSONObject().apply {
                        put("mediaId", tr.mediaId)
                        put("tagId", tr.tagId)
                    })
                }
            })
            put("mediaMeta", JSONArray().apply {
                media.forEach { m ->
                    put(JSONObject().apply {
                        put("id", m.id)
                        put("isFavorite", m.isFavorite)
                        put("note", m.note)
                        put("editParamsJson", m.editParamsJson)
                    })
                }
            })
        }

        val outFile = File(AppStorageDirs.backupsDir(context), "folio_backup_${System.currentTimeMillis()}.json")
        outFile.writeText(root.toString(2))
        outFile
    }

    fun getLatestBackupFile(): File? {
        return AppStorageDirs.backupsDir(context)
            .listFiles { f -> f.extension == "json" }
            ?.maxByOrNull { it.lastModified() }
    }

    suspend fun restoreFromBackupFile(file: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(file.readText())
            val albumsArr = root.optJSONArray("albums") ?: JSONArray()
            val idMap = mutableMapOf<Long, Long>()
            for (i in 0 until albumsArr.length()) {
                val obj = albumsArr.getJSONObject(i)
                val oldId = obj.getLong("id")
                val newId = dao.insertVirtualAlbum(
                    VirtualAlbumEntity(
                        name = obj.getString("name"),
                        parentAlbumId = null,
                        coverUri = obj.optString("coverUri")
                    )
                )
                idMap[oldId] = newId
            }
            val refsArr = root.optJSONArray("albumRefs") ?: JSONArray()
            val refsToInsert = mutableListOf<AlbumMediaCrossRef>()
            for (i in 0 until refsArr.length()) {
                val obj = refsArr.getJSONObject(i)
                val mappedAlbumId = idMap[obj.getLong("albumId")] ?: continue
                refsToInsert.add(AlbumMediaCrossRef(mappedAlbumId, obj.getString("mediaId")))
            }
            if (refsToInsert.isNotEmpty()) dao.addMediaToAlbum(refsToInsert)

            val mediaMetaArr = root.optJSONArray("mediaMeta") ?: JSONArray()
            for (i in 0 until mediaMetaArr.length()) {
                val obj = mediaMetaArr.getJSONObject(i)
                val id = obj.getString("id")
                if (obj.optBoolean("isFavorite")) dao.setFavorites(listOf(id), true)
                val note = obj.optString("note")
                if (note.isNotEmpty()) dao.updateNote(id, note)
                val edit = obj.optString("editParamsJson")
                if (edit.isNotEmpty()) dao.updateEditParams(id, edit)
            }
            true
        }.getOrDefault(false)
    }

    // Android Sharesheet & Targeted App Share (explicit user-initiated action)
    fun toShareableUri(uriString: String): Uri? {
        val parsed = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        if (parsed.scheme == ContentResolver.SCHEME_FILE) {
            val file = File(parsed.path.orEmpty())
            if (!file.exists()) return null
            return runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.getOrNull() ?: parsed
        }
        return parsed
    }

    /** Mirrors Settings: when location metadata is hidden, shared JPEGs have GPS EXIF removed. */
    @Volatile
    var stripLocationOnShare: Boolean = false

    private val gpsTags = listOf(
        androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE,
        androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE_REF,
        androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE,
        androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE_REF,
        androidx.exifinterface.media.ExifInterface.TAG_GPS_ALTITUDE,
        androidx.exifinterface.media.ExifInterface.TAG_GPS_ALTITUDE_REF,
        androidx.exifinterface.media.ExifInterface.TAG_GPS_TIMESTAMP,
        androidx.exifinterface.media.ExifInterface.TAG_GPS_DATESTAMP,
        androidx.exifinterface.media.ExifInterface.TAG_GPS_PROCESSING_METHOD,
        androidx.exifinterface.media.ExifInterface.TAG_GPS_AREA_INFORMATION
    )

    fun shareUriFor(item: MediaItemEntity): Uri? {
        val base = toShareableUri(item.uriString) ?: return null
        if (!stripLocationOnShare || item.isVideo) return base
        if (!item.mimeType.equals("image/jpeg", ignoreCase = true) &&
            !item.mimeType.equals("image/jpg", ignoreCase = true)
        ) return base
        return runCatching {
            val out = File(
                AppStorageDirs.tempCacheDir(context),
                "share_clean_${item.id.hashCode()}_${item.dateModifiedMillis}.jpg"
            )
            if (!out.exists() || out.length() == 0L) {
                val copied = context.contentResolver.openInputStream(base)?.use { input ->
                    FileOutputStream(out).use { output -> input.copyTo(output) }
                    true
                } ?: false
                if (!copied) return base
                val exif = androidx.exifinterface.media.ExifInterface(out.absolutePath)
                gpsTags.forEach { exif.setAttribute(it, null) }
                exif.saveAttributes()
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out)
        }.getOrDefault(base)
    }

    fun buildShareIntent(items: List<MediaItemEntity>): Intent? {
        val uris = ArrayList(items.mapNotNull { shareUriFor(it) })
        if (uris.isEmpty()) return null
        val hasVideo = items.any { it.isVideo }
        val hasPhoto = items.any { !it.isVideo }
        val mime = when {
            hasVideo && hasPhoto -> "*/*"
            hasVideo -> "video/*"
            else -> "image/*"
        }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = items.first().mimeType.ifEmpty { mime }
                putExtra(Intent.EXTRA_STREAM, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        return Intent.createChooser(intent, "Share ${items.size} item(s)")
    }

    fun buildTargetedMultiShareIntent(
        items: List<MediaItemEntity>,
        packageName: String,
        activityClassName: String? = null
    ): Intent? {
        val uris = ArrayList(items.mapNotNull { shareUriFor(it) })
        if (uris.isEmpty()) return null
        val hasVideo = items.any { it.isVideo }
        val hasPhoto = items.any { !it.isVideo }
        val mime = when {
            hasVideo && hasPhoto -> "*/*"
            hasVideo -> "video/*"
            else -> "image/*"
        }
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mime
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            if (!activityClassName.isNullOrBlank()) setClassName(packageName, activityClassName)
            else setPackage(packageName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildTargetedShareIntent(
        item: MediaItemEntity,
        packageName: String,
        activityClassName: String? = null
    ): Intent? {
        val uri = shareUriFor(item) ?: return null
        val mime = item.mimeType.ifEmpty { if (item.isVideo) "video/*" else "image/*" }
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            if (!activityClassName.isNullOrBlank()) {
                setClassName(packageName, activityClassName)
            } else {
                setPackage(packageName)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun buildLiteTargetedShareIntent(
        item: MediaItemEntity,
        packageName: String? = null,
        activityClassName: String? = null
    ): Intent? = withContext(Dispatchers.IO) {
        if (item.isVideo) {
            return@withContext if (packageName != null) {
                buildTargetedShareIntent(item, packageName, activityClassName)
            } else {
                buildShareIntent(listOf(item))
            }
        }
        val sourceUri = runCatching { Uri.parse(item.uriString) }.getOrNull() ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(sourceUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        var sample = 1
        while (maxSide / sample > 1440) sample *= 2

        val decoded = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@withContext if (packageName != null) {
            buildTargetedShareIntent(item, packageName, activityClassName)
        } else {
            buildShareIntent(listOf(item))
        }

        val scaled = if (maxOf(decoded.width, decoded.height) > 1280) {
            val ratio = 1280f / maxOf(decoded.width, decoded.height).toFloat()
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * ratio).toInt().coerceAtLeast(1),
                (decoded.height * ratio).toInt().coerceAtLeast(1),
                true
            ).also { if (it !== decoded) decoded.recycle() }
        } else {
            decoded
        }

        val tempDir = AppStorageDirs.tempCacheDir(context)
        val outFile = File(tempDir, "share_lite_${item.id.hashCode()}.jpg")
        FileOutputStream(outFile).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        }
        scaled.recycle()

        val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            if (!packageName.isNullOrBlank()) {
                if (!activityClassName.isNullOrBlank()) {
                    setClassName(packageName, activityClassName)
                } else {
                    setPackage(packageName)
                }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (packageName.isNullOrBlank()) Intent.createChooser(sendIntent, "Share Fast Copy") else sendIntent
    }

    fun copyMediaToClipboard(item: MediaItemEntity): Boolean {
        val shareUri = toShareableUri(item.uriString) ?: return false
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        val clip = ClipData.newUri(context.contentResolver, item.displayName, shareUri)
        clipboard.setPrimaryClip(clip)
        return true
    }

    fun buildExternalPlayerIntent(item: MediaItemEntity): Intent {
        val uri = toShareableUri(item.uriString) ?: Uri.parse(item.uriString)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.mimeType.ifEmpty { "video/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun createMediaStoreDeleteRequestIntent(items: List<MediaItemEntity>): android.app.PendingIntent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val contentUris = items
                .filter { !it.id.startsWith("imp_") }
                .mapNotNull { runCatching { Uri.parse(it.uriString) }.getOrNull() }
                .filter { it.scheme == ContentResolver.SCHEME_CONTENT }
            if (contentUris.isNotEmpty()) {
                return MediaStore.createDeleteRequest(context.contentResolver, contentUris)
            }
        }
        return null
    }

    companion object {
        @Volatile
        private var INSTANCE: GalleryRepository? = null

        fun getInstance(context: Context): GalleryRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GalleryRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
