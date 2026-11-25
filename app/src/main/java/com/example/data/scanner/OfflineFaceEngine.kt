package com.example.data.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.example.data.local.GalleryDao
import com.example.data.local.MediaFaceEntity
import com.example.data.local.MediaItemEntity
import com.example.data.local.PersonGroupEntity
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.io.File

/**
 * 100% on-device face scan: ML Kit (bundled) detects + gives eye landmarks, faces are aligned and
 * embedded with MobileFaceNet, then grouped by cosine similarity to each person's centroid.
 */
object OfflineFaceEngine {

    /** Cosine similarity to a person's centroid needed to join them. Higher = stricter (more, purer groups). */
    private const val MATCH_THRESHOLD = 0.60f
    private const val MIN_FACE_PX = 48
    private const val DECODE_MAX_SIDE = 1280
    private const val CHUNK = 16
    private const val NO_FACE_FILE = "face_scan_noface.txt"

    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setMinFaceSize(0.05f)
                .build()
        )
    }

    private class RawFace(val chw: FloatArray, val cx: Float, val cy: Float, val eye: Float)
    private class Detected(val item: MediaItemEntity, val faces: List<RawFace>)

    fun clearNoFaceMarkers(context: Context) {
        runCatching { File(context.filesDir, NO_FACE_FILE).delete() }
    }

    suspend fun scanUnprocessedPhotos(
        context: Context,
        photos: List<MediaItemEntity>,
        dao: GalleryDao,
        control: ScanControl,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Int {
        if (!FaceEmbedder.isAvailable(context)) return 0

        // Groups made by the old colour-average method are meaningless: wipe them once.
        val groupsNow = dao.getPersonGroupsSnapshot()
        if (groupsNow.any { FaceEmbedder.decode(it.signature) == null }) {
            dao.deleteAllFaces()
            dao.deleteAllPersonGroups()
            clearNoFaceMarkers(context)
        }

        val markerFile = File(context.filesDir, NO_FACE_FILE)
        val alreadyScanned = dao.getScannedFaceMediaIds().toHashSet()
        if (markerFile.exists()) runCatching { markerFile.forEachLine { alreadyScanned.add(it) } }
        val pending = photos.filter { it.id !in alreadyScanned }
        if (pending.isEmpty()) return 0

        class Centroid(var id: Long, var vec: FloatArray, var count: Int)
        val centroids = dao.getPersonGroupsSnapshot().mapNotNull { g ->
            FaceEmbedder.decode(g.signature)?.let { Centroid(g.id, it, g.faceCount) }
        }.toMutableList()
        var labelCounter = centroids.size
        var newFacesTotal = 0
        var processed = 0

        for (chunk in pending.chunked(CHUNK)) {
            if (!control.checkpoint()) break
            val results = java.util.concurrent.ConcurrentLinkedQueue<Detected>()
            val noFace = java.util.concurrent.ConcurrentLinkedQueue<String>()
            runParallel(chunk, control, onProgress = { _, _ -> }) { item ->
                val faces = detect(context, item) ?: return@runParallel
                if (faces.isEmpty()) noFace.add(item.id) else results.add(Detected(item, faces))
            }

            for (d in results) {
                val embeds = FaceEmbedder.embed(context, d.faces.map { it.chw })
                val entities = ArrayList<MediaFaceEntity>(d.faces.size)
                d.faces.forEachIndexed { idx, f ->
                    val e = embeds[idx] ?: return@forEachIndexed
                    var best: Centroid? = null
                    var bestSim = -1f
                    for (c in centroids) {
                        val sim = FaceEmbedder.cosine(e, c.vec)
                        if (sim > bestSim) { bestSim = sim; best = c }
                    }
                    val personId: Long
                    if (best != null && bestSim >= MATCH_THRESHOLD) {
                        val n = best.count
                        val merged = FaceEmbedder.l2(FloatArray(e.size) { best.vec[it] * n + e[it] })
                        best.vec = merged
                        best.count = n + 1
                        dao.updatePersonGroupCentroid(best.id, best.count, FaceEmbedder.encode(merged))
                        personId = best.id
                    } else {
                        labelCounter++
                        val id = dao.insertPersonGroup(
                            PersonGroupEntity(
                                label = "Person $labelCounter",
                                coverMediaId = d.item.id,
                                faceCount = 1,
                                signature = FaceEmbedder.encode(e)
                            )
                        )
                        centroids.add(Centroid(id, e, 1))
                        personId = id
                    }
                    entities.add(
                        MediaFaceEntity(
                            mediaId = d.item.id,
                            personId = personId,
                            centerX = f.cx,
                            centerY = f.cy,
                            eyeDistance = f.eye,
                            signature = ""
                        )
                    )
                    newFacesTotal++
                }
                if (entities.isNotEmpty()) dao.insertMediaFaces(entities)
            }
            if (noFace.isNotEmpty()) {
                runCatching { markerFile.appendText(noFace.joinToString("\n", postfix = "\n")) }
            }
            processed += chunk.size
            onProgress(processed.coerceAtMost(pending.size), pending.size)
        }
        return newFacesTotal
    }

    /** null = decode failed (retry next time); empty = no usable faces. */
    private fun detect(context: Context, item: MediaItemEntity): List<RawFace>? {
        val uri = Uri.parse(item.uriString)
        val bmp = decode(context, uri) ?: return null
        return try {
            val faces = Tasks.await(detector.process(InputImage.fromBitmap(bmp, 0)))
            val out = ArrayList<RawFace>()
            for (f in faces) {
                if (f.boundingBox.width() < MIN_FACE_PX) continue
                val le = f.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: continue
                val re = f.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: continue
                val (a, b) = if (le.x <= re.x) le to re else re to le
                val chw = FaceEmbedder.alignToChw(bmp, a.x, a.y, b.x, b.y)
                val dx = b.x - a.x
                val dy = b.y - a.y
                out.add(
                    RawFace(
                        chw = chw,
                        cx = f.boundingBox.centerX() / bmp.width.toFloat(),
                        cy = f.boundingBox.centerY() / bmp.height.toFloat(),
                        eye = kotlin.math.sqrt(dx * dx + dy * dy) / bmp.width.toFloat()
                    )
                )
            }
            out
        } catch (_: Throwable) {
            null
        } finally {
            bmp.recycle()
        }
    }

    /** Decodes ~1280px max, upright (EXIF-rotated) ARGB bitmap. */
    private fun decode(context: Context, uri: Uri): Bitmap? = runCatching {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= DECODE_MAX_SIDE || bounds.outHeight / (sample * 2) >= DECODE_MAX_SIDE) sample *= 2
        val raw = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        } ?: return null
        val rotation = runCatching {
            cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
        if (rotation == 0) raw else {
            val m = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
            val r = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            if (r !== raw) raw.recycle()
            r
        }
    }.getOrNull()
}
