package com.example.data.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.example.data.local.MediaItemEntity
import com.example.data.local.MediaType

data class ExifMetadata(
    val latitude: Double?,
    val longitude: Double?,
    val cameraModel: String
)

object MediaStoreScanner {

    fun scanDeviceMedia(
        context: Context,
        existingById: Map<String, MediaItemEntity>
    ): Pair<List<MediaItemEntity>, List<String>> {
        val scanned = mutableListOf<MediaItemEntity>()
        val activeIds = mutableSetOf<String>()

        scanImages(context, existingById, scanned, activeIds)
        scanVideos(context, existingById, scanned, activeIds)

        val staleIds = existingById.values
            .filter { !it.id.startsWith("imp_") && !it.isInVault && it.id !in activeIds }
            .map { it.id }

        return scanned to staleIds
    }

    private fun scanImages(
        context: Context,
        existingById: Map<String, MediaItemEntity>,
        out: MutableList<MediaItemEntity>,
        activeIds: MutableSet<String>
    ) {
        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            add(MediaStore.Images.Media.MIME_TYPE)
            add(MediaStore.Images.Media.DATE_TAKEN)
            add(MediaStore.Images.Media.DATE_ADDED)
            add(MediaStore.Images.Media.DATE_MODIFIED)
            add(MediaStore.Images.Media.WIDTH)
            add(MediaStore.Images.Media.HEIGHT)
            add(MediaStore.Images.Media.SIZE)
            add(MediaStore.Images.Media.BUCKET_ID)
            add(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val takenCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val modCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val wCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val hCol = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val bucketIdCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val entityId = "img_$mediaId"
                    activeIds.add(entityId)

                    val dateModified = cursor.getLong(modCol) * 1000L
                    val existing = existingById[entityId]
                    if (existing != null && existing.dateModifiedMillis == dateModified) {
                        continue
                    }

                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)
                    val dateAdded = cursor.getLong(addedCol) * 1000L
                    val rawTaken = if (takenCol >= 0) cursor.getLong(takenCol) else 0L
                    val dateTaken = when {
                        rawTaken > 0L -> rawTaken
                        dateAdded > 0L -> dateAdded
                        else -> dateModified
                    }

                    out.add(
                        MediaItemEntity(
                            id = entityId,
                            mediaStoreId = mediaId,
                            uriString = uri.toString(),
                            mediaType = MediaType.PHOTO,
                            displayName = cursor.getString(nameCol) ?: "Photo_$mediaId.jpg",
                            mimeType = cursor.getString(mimeCol) ?: "image/jpeg",
                            dateTakenMillis = dateTaken,
                            dateAddedMillis = dateAdded,
                            dateModifiedMillis = dateModified,
                            width = if (wCol >= 0) cursor.getInt(wCol) else 0,
                            height = if (hCol >= 0) cursor.getInt(hCol) else 0,
                            sizeBytes = cursor.getLong(sizeCol),
                            durationMillis = 0L,
                            bucketId = if (bucketIdCol >= 0) cursor.getString(bucketIdCol).orEmpty() else "",
                            bucketName = if (bucketNameCol >= 0) cursor.getString(bucketNameCol).orEmpty().ifEmpty { "Camera" } else "Camera",
                            relativePath = if (relPathCol >= 0) cursor.getString(relPathCol).orEmpty() else "",
                            isFavorite = existing?.isFavorite ?: false,
                            note = existing?.note.orEmpty(),
                            isTrashed = existing?.isTrashed ?: false,
                            trashedAtMillis = existing?.trashedAtMillis ?: 0L,
                            isInVault = existing?.isInVault ?: false,
                            vaultFileName = existing?.vaultFileName.orEmpty(),
                            sha256Hash = if (existing?.dateModifiedMillis == dateModified) existing.sha256Hash else "",
                            dHash = if (existing?.dateModifiedMillis == dateModified) existing.dHash else "",
                            latitude = existing?.latitude,
                            longitude = existing?.longitude,
                            cameraModel = existing?.cameraModel.orEmpty(),
                            editParamsJson = existing?.editParamsJson.orEmpty(),
                            playbackPositionMs = existing?.playbackPositionMs ?: 0L
                        )
                    )
                }
            }
        }
    }

    private fun scanVideos(
        context: Context,
        existingById: Map<String, MediaItemEntity>,
        out: MutableList<MediaItemEntity>,
        activeIds: MutableSet<String>
    ) {
        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.MIME_TYPE)
            add(MediaStore.Video.Media.DATE_TAKEN)
            add(MediaStore.Video.Media.DATE_ADDED)
            add(MediaStore.Video.Media.DATE_MODIFIED)
            add(MediaStore.Video.Media.WIDTH)
            add(MediaStore.Video.Media.HEIGHT)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.BUCKET_ID)
            add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Video.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        runCatching {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val takenCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_TAKEN)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val modCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val wCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val hCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val bucketIdCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val entityId = "vid_$mediaId"
                    activeIds.add(entityId)

                    val dateModified = cursor.getLong(modCol) * 1000L
                    val existing = existingById[entityId]
                    if (existing != null && existing.dateModifiedMillis == dateModified) {
                        continue
                    }

                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaId)
                    val dateAdded = cursor.getLong(addedCol) * 1000L
                    val rawTaken = if (takenCol >= 0) cursor.getLong(takenCol) else 0L
                    val dateTaken = when {
                        rawTaken > 0L -> rawTaken
                        dateAdded > 0L -> dateAdded
                        else -> dateModified
                    }

                    out.add(
                        MediaItemEntity(
                            id = entityId,
                            mediaStoreId = mediaId,
                            uriString = uri.toString(),
                            mediaType = MediaType.VIDEO,
                            displayName = cursor.getString(nameCol) ?: "Video_$mediaId.mp4",
                            mimeType = cursor.getString(mimeCol) ?: "video/mp4",
                            dateTakenMillis = dateTaken,
                            dateAddedMillis = dateAdded,
                            dateModifiedMillis = dateModified,
                            width = if (wCol >= 0) cursor.getInt(wCol) else 0,
                            height = if (hCol >= 0) cursor.getInt(hCol) else 0,
                            sizeBytes = cursor.getLong(sizeCol),
                            durationMillis = if (durCol >= 0) cursor.getLong(durCol) else 0L,
                            bucketId = if (bucketIdCol >= 0) cursor.getString(bucketIdCol).orEmpty() else "",
                            bucketName = if (bucketNameCol >= 0) cursor.getString(bucketNameCol).orEmpty().ifEmpty { "Videos" } else "Videos",
                            relativePath = if (relPathCol >= 0) cursor.getString(relPathCol).orEmpty() else "",
                            isFavorite = existing?.isFavorite ?: false,
                            note = existing?.note.orEmpty(),
                            isTrashed = existing?.isTrashed ?: false,
                            trashedAtMillis = existing?.trashedAtMillis ?: 0L,
                            isInVault = existing?.isInVault ?: false,
                            vaultFileName = existing?.vaultFileName.orEmpty(),
                            sha256Hash = if (existing?.dateModifiedMillis == dateModified) existing.sha256Hash else "",
                            dHash = if (existing?.dateModifiedMillis == dateModified) existing.dHash else "",
                            latitude = existing?.latitude,
                            longitude = existing?.longitude,
                            cameraModel = existing?.cameraModel.orEmpty(),
                            editParamsJson = existing?.editParamsJson.orEmpty(),
                            playbackPositionMs = existing?.playbackPositionMs ?: 0L
                        )
                    )
                }
            }
        }
    }

    fun readExifMetadata(context: Context, uri: Uri): ExifMetadata {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val latLong = exif.latLong
                val make = exif.getAttribute(ExifInterface.TAG_MAKE).orEmpty().trim()
                val model = exif.getAttribute(ExifInterface.TAG_MODEL).orEmpty().trim()
                val camera = listOf(make, model).filter { it.isNotEmpty() }.distinct().joinToString(" ")
                ExifMetadata(
                    latitude = latLong?.getOrNull(0),
                    longitude = latLong?.getOrNull(1),
                    cameraModel = camera
                )
            } ?: ExifMetadata(null, null, "")
        }.getOrDefault(ExifMetadata(null, null, ""))
    }
}
